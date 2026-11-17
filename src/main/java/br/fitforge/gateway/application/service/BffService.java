package br.fitforge.gateway.application.service;

import br.fitforge.gateway.application.model.*;
import br.fitforge.gateway.application.port.in.BffUseCase;
import br.fitforge.gateway.application.port.out.ProgressoClientPort;
import br.fitforge.gateway.application.port.out.TreinosClientPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Backend For Frontend do FitForge (núcleo da aplicação, sem dependência de Spring).
 *
 * Cada microsserviço só conhece o próprio domínio (database per service):
 *   - treinos-service   (MongoDB)   → fichas e exercícios
 *   - progresso-service (Azure SQL) → sessões registradas
 * O BFF junta os dois por meio das portas de saída e entrega a cada tela
 * exatamente o que ela precisa, em UMA resposta. Chamadas independentes rodam
 * em paralelo (Mono.zip); se um serviço cair, a resposta é degradada (fallback).
 */
public class BffService implements BffUseCase {

    private static final Logger log = LoggerFactory.getLogger(BffService.class);
    private static final List<String> DIAS = List.of("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom");

    private final TreinosClientPort treinos;
    private final ProgressoClientPort progresso;
    private final Clock clock;

    public BffService(TreinosClientPort treinos, ProgressoClientPort progresso, Clock clock) {
        this.treinos = treinos;
        this.progresso = progresso;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 1) Visão geral
    @Override
    public Mono<DashboardBffDTO> dashboard(String cid) {
        Mono<Optional<List<WorkoutDTO>>> fichasMono = opcional(treinos.listarFichas(cid), "treinos-service");
        Mono<Optional<List<SessionDTO>>> sessoesMono = opcional(progresso.listarSessoes(cid), "progresso-service");
        Mono<Optional<ProgressoStatsDTO>> statsMono = opcional(progresso.estatisticas(cid), "progresso-service/estatisticas");

        return Mono.zip(fichasMono, sessoesMono, statsMono).map(t -> {
            List<WorkoutDTO> fichas = t.getT1().orElse(List.of());
            List<SessionDTO> sessoes = t.getT2().orElse(List.of());
            Optional<ProgressoStatsDTO> stats = t.getT3();

            String hoje = DIAS.get(LocalDate.now(clock).getDayOfWeek().getValue() - 1);
            List<WorkoutDTO> deHoje = fichas.stream().filter(f -> f.days().contains(hoje)).toList();

            Map<String, List<SessionDTO>> porFicha = sessoes.stream().collect(Collectors.groupingBy(SessionDTO::workoutId));
            List<FichaResumoDTO> resumo = fichas.stream().map(f -> {
                List<SessionDTO> s = porFicha.getOrDefault(f.id(), List.of());
                return new FichaResumoDTO(f.id(), f.name(), f.days(), f.exercises().size(), f.volume(), s.size(),
                        s.stream().map(SessionDTO::date).max(Comparator.naturalOrder()).orElse(null), f.totalSessoes());
            }).toList();

            Map<String, WorkoutDTO> fichaPorId = indexar(fichas);
            boolean fichasOk = t.getT1().isPresent();
            List<SessaoEnriquecidaDTO> ultimas = sessoes.stream()
                    .sorted(Comparator.comparing(SessionDTO::date).reversed())
                    .limit(5)
                    .map(s -> enriquecer(s, fichaPorId, fichasOk))
                    .toList();

            Map<String, String> status = new LinkedHashMap<>();
            status.put("treinos-service", t.getT1().isPresent() ? "ONLINE" : "OFFLINE");
            status.put("progresso-service", t.getT2().isPresent() ? "ONLINE" : "OFFLINE");

            return new DashboardBffDTO(
                    hoje, deHoje,
                    fichas.size(),
                    fichas.stream().mapToLong(f -> f.exercises().size()).sum(),
                    fichas.stream().map(WorkoutDTO::volume).reduce(BigDecimal.ZERO, BigDecimal::add),
                    stats.map(ProgressoStatsDTO::totalSessoes).orElse((long) sessoes.size()),
                    stats.map(ProgressoStatsDTO::minutosTotais).orElse(sessoes.stream().mapToLong(SessionDTO::duration).sum()),
                    stats.map(ProgressoStatsDTO::sessoesNaSemana).orElse(0L),
                    stats.map(ProgressoStatsDTO::minutosNaSemana).orElse(0L),
                    stats.map(ProgressoStatsDTO::esforcoMedio).orElse(null),
                    stats.map(ProgressoStatsDTO::ultimoPesoKg).orElse(null),
                    stats.map(ProgressoStatsDTO::sequenciaDiasSeguidos).orElse(0),
                    stats.map(ProgressoStatsDTO::semana).orElse(List.of()),
                    resumo, ultimas, status, LocalDateTime.now(clock));
        });
    }

    // ------------------------------------------------------------------ 2) Ficha + histórico
    @Override
    public Mono<FichaDetalhadaBffDTO> fichaDetalhada(String id, String cid) {
        Mono<WorkoutDTO> fichaMono = treinos.buscarFicha(id, cid);
        Mono<Optional<List<SessionDTO>>> historicoMono = opcional(progresso.listarSessoesDaFicha(id, cid), "progresso-service");

        return Mono.zip(fichaMono, historicoMono).map(t -> {
            WorkoutDTO ficha = t.getT1();
            List<SessionDTO> historico = t.getT2().orElse(List.of());
            Double esforco = historico.isEmpty() ? null : BigDecimal.valueOf(
                            historico.stream().mapToInt(SessionDTO::effort).average().orElse(0))
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
            return new FichaDetalhadaBffDTO(
                    ficha, ficha.volume(), historico.size(),
                    historico.stream().mapToLong(SessionDTO::duration).sum(),
                    esforco,
                    historico.stream().map(SessionDTO::date).max(Comparator.naturalOrder()).orElse(null),
                    historico, t.getT2().isPresent(), LocalDateTime.now(clock));
        });
    }

    // ------------------------------------------------------------------ 3) Histórico enriquecido
    @Override
    public Mono<List<SessaoEnriquecidaDTO>> historico(String periodo, String cid) {
        Mono<List<SessionDTO>> sessoesMono = progresso.listarSessoesDoPeriodo(periodo, cid);
        Mono<Optional<List<WorkoutDTO>>> fichasMono = opcional(treinos.listarFichas(cid), "treinos-service");
        return Mono.zip(sessoesMono, fichasMono).map(t -> {
            Map<String, WorkoutDTO> porId = indexar(t.getT2().orElse(List.of()));
            boolean fichasOk = t.getT2().isPresent();
            return t.getT1().stream().map(s -> enriquecer(s, porId, fichasOk)).toList();
        });
    }

    // ------------------------------------------------------------------ 4) Status
    @Override
    public Mono<Map<String, Object>> status() {
        long inicio = System.currentTimeMillis();
        Mono<Map<String, Object>> t = treinos.saude().map(st -> saude("treinos-service", treinos.endereco(), st, inicio));
        Mono<Map<String, Object>> p = progresso.saude().map(st -> saude("progresso-service", progresso.endereco(), st, inicio));
        return Mono.zip(t, p).map(r -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("componente", "FitForge — API Gateway & BFF");
            Map<String, String> rotas = new LinkedHashMap<>();
            rotas.put("/api/treinos/**", treinos.endereco());
            rotas.put("/api/sessoes/**", progresso.endereco());
            rotas.put("/api/bff/**", "processado localmente (BFF)");
            s.put("rotas", rotas);
            s.put("microsservicos", List.of(r.getT1(), r.getT2()));
            s.put("timestamp", LocalDateTime.now(clock));
            return s;
        });
    }

    // ------------------------------------------------------------------ auxiliares
    private static Map<String, Object> saude(String nome, String url, String status, long inicio) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("servico", nome);
        m.put("url", url);
        m.put("status", status);
        m.put("latenciaMs", System.currentTimeMillis() - inicio);
        return m;
    }

    private static Map<String, WorkoutDTO> indexar(List<WorkoutDTO> fichas) {
        return fichas.stream().collect(Collectors.toMap(WorkoutDTO::id, Function.identity(), (a, b) -> a));
    }

    private static SessaoEnriquecidaDTO enriquecer(SessionDTO s, Map<String, WorkoutDTO> fichas, boolean fichasDisponiveis) {
        WorkoutDTO f = fichas.get(s.workoutId());
        // Se o treinos-service estiver fora, não afirmamos que a ficha foi excluída
        boolean existe = f != null || (!fichasDisponiveis && s.fichaAtiva());
        return new SessaoEnriquecidaDTO(s, existe, f != null ? f.name() : s.workoutName());
    }

    /** Converte erro em Optional.empty() (fallback) e registra em log. */
    private static <T> Mono<Optional<T>> opcional(Mono<T> origem, String servico) {
        return origem.map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(ex -> {
                    log.warn("[BFF FALLBACK] {} indisponível: {}", servico, ex.toString());
                    return Mono.just(Optional.empty());
                });
    }
}
