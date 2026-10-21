package br.fitforge.gateway.bff.service;

import br.fitforge.gateway.bff.dto.*;
import br.fitforge.gateway.filter.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Backend For Frontend do FitForge.
 *
 * Cada microsserviço só conhece o próprio domínio (database per service):
 *   - treinos-service   -> fichas e exercícios
 *   - progresso-service -> sessões registradas
 * O BFF junta os dois e entrega a cada tela exatamente o que ela precisa.
 * Chamadas independentes rodam EM PARALELO (Mono.zip); se um serviço cair,
 * a resposta é degradada (fallback) em vez de quebrar a tela inteira.
 */
@Service
public class BffService {

    private static final Logger log = LoggerFactory.getLogger(BffService.class);
    private static final List<String> DIAS = List.of("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom");

    private final WebClient treinos;
    private final WebClient progresso;
    private final Duration timeout;
    private final Clock clock;
    private final String treinosUrl;
    private final String progressoUrl;

    public BffService(@Qualifier("treinosWebClient") WebClient treinos,
                      @Qualifier("progressoWebClient") WebClient progresso,
                      @Value("${services.timeout-seconds:15}") long timeoutSeconds,
                      @Value("${services.treinos.url}") String treinosUrl,
                      @Value("${services.progresso.url}") String progressoUrl,
                      Clock clock) {
        this.treinos = treinos;
        this.progresso = progresso;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.clock = clock;
        this.treinosUrl = treinosUrl;
        this.progressoUrl = progressoUrl;
        log.info("[BFF] treinos-service   = {}", treinosUrl);
        log.info("[BFF] progresso-service = {}", progressoUrl);
    }

    // ------------------------------------------------------------------
    // 1) DASHBOARD (Visão geral) — 3 chamadas em paralelo
    // ------------------------------------------------------------------
    public Mono<DashboardBffDTO> dashboard(String cid) {
        Mono<Optional<List<WorkoutDTO>>> fichasMono = lista(treinos, "/api/treinos", WorkoutDTO.class, cid, "treinos-service");
        Mono<Optional<List<SessionDTO>>> sessoesMono = lista(progresso, "/api/sessoes", SessionDTO.class, cid, "progresso-service");
        Mono<Optional<ProgressoStatsDTO>> statsMono = objeto(progresso, "/api/sessoes/estatisticas", ProgressoStatsDTO.class, cid, "progresso-service/estatisticas");

        return Mono.zip(fichasMono, sessoesMono, statsMono).map(t -> {
            List<WorkoutDTO> fichas = t.getT1().orElse(List.of());
            List<SessionDTO> sessoes = t.getT2().orElse(List.of());
            Optional<ProgressoStatsDTO> stats = t.getT3();

            String hoje = DIAS.get(LocalDate.now(clock).getDayOfWeek().getValue() - 1);
            List<WorkoutDTO> deHoje = fichas.stream().filter(f -> f.days().contains(hoje)).toList();

            Map<String, List<SessionDTO>> porFicha = sessoes.stream()
                    .collect(Collectors.groupingBy(SessionDTO::workoutId));
            List<FichaResumoDTO> resumo = fichas.stream().map(f -> {
                List<SessionDTO> s = porFicha.getOrDefault(f.id(), List.of());
                return new FichaResumoDTO(f.id(), f.name(), f.days(), f.exercises().size(), f.volume(),
                        s.size(), s.stream().map(SessionDTO::date).max(Comparator.naturalOrder()).orElse(null));
            }).toList();

            Map<String, WorkoutDTO> fichaPorId = fichas.stream()
                    .collect(Collectors.toMap(WorkoutDTO::id, Function.identity(), (a, b) -> a));
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

    // ------------------------------------------------------------------
    // 2) FICHA DETALHADA — ficha + histórico dela (paralelo)
    // ------------------------------------------------------------------
    public Mono<FichaDetalhadaBffDTO> fichaDetalhada(String id, String cid) {
        Mono<WorkoutDTO> fichaMono = treinos.get().uri("/api/treinos/{id}", id)
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToMono(WorkoutDTO.class)
                .timeout(timeout)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty())
                .onErrorMap(ex -> !(ex instanceof ResponseStatusException), ex -> indisponivel("treinos-service", ex));

        Mono<Optional<List<SessionDTO>>> historicoMono = progresso.get()
                .uri(b -> b.path("/api/sessoes").queryParam("workoutId", id).build())
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToFlux(SessionDTO.class).collectList()
                .timeout(timeout)
                .map(Optional::of)
                .onErrorResume(ex -> this.<List<SessionDTO>>fallback("progresso-service", ex));

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

    // ------------------------------------------------------------------
    // 3) HISTÓRICO ENRIQUECIDO — sessões + situação atual de cada ficha
    // ------------------------------------------------------------------
    public Mono<List<SessaoEnriquecidaDTO>> historico(String periodo, String cid) {
        Mono<List<SessionDTO>> sessoesMono = progresso.get()
                .uri(b -> {
                    b.path("/api/sessoes");
                    if (periodo != null && !periodo.isBlank()) b.queryParam("periodo", periodo);
                    return b.build();
                })
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToFlux(SessionDTO.class).collectList()
                .timeout(timeout)
                .onErrorMap(WebClientResponseException.BadRequest.class,
                        ex -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido. Use semana, mes ou tudo."))
                .onErrorMap(ex -> !(ex instanceof ResponseStatusException), ex -> indisponivel("progresso-service", ex));

        Mono<Optional<List<WorkoutDTO>>> fichasMono = lista(treinos, "/api/treinos", WorkoutDTO.class, cid, "treinos-service");

        return Mono.zip(sessoesMono, fichasMono).map(t -> {
            Map<String, WorkoutDTO> porId = t.getT2().orElse(List.of()).stream()
                    .collect(Collectors.toMap(WorkoutDTO::id, Function.identity(), (a, b) -> a));
            boolean fichasOk = t.getT2().isPresent();
            return t.getT1().stream().map(s -> enriquecer(s, porId, fichasOk)).toList();
        });
    }

    // ------------------------------------------------------------------
    // 4) STATUS — health check real dos microsserviços (paralelo)
    // ------------------------------------------------------------------
    public Mono<Map<String, Object>> status() {
        return Mono.zip(health("treinos-service", treinos, treinosUrl), health("progresso-service", progresso, progressoUrl))
                .map(t -> {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("componente", "FitForge — API Gateway & BFF");
                    Map<String, String> rotas = new LinkedHashMap<>();
                    rotas.put("/api/treinos/**", treinosUrl);
                    rotas.put("/api/sessoes/**", progressoUrl);
                    rotas.put("/api/bff/**", "processado localmente (BFF)");
                    s.put("rotas", rotas);
                    s.put("microsservicos", List.of(t.getT1(), t.getT2()));
                    s.put("timestamp", LocalDateTime.now(clock));
                    return s;
                });
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------
    private SessaoEnriquecidaDTO enriquecer(SessionDTO s, Map<String, WorkoutDTO> fichas, boolean fichasDisponiveis) {
        WorkoutDTO f = fichas.get(s.workoutId());
        // Se o treinos-service estiver fora, não afirmamos que a ficha foi excluída
        boolean existe = f != null || !fichasDisponiveis;
        return new SessaoEnriquecidaDTO(s, existe, f != null ? f.name() : s.workoutName());
    }

    private <T> Mono<Optional<List<T>>> lista(WebClient client, String uri, Class<T> tipo, String cid, String nome) {
        return client.get().uri(uri)
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToFlux(tipo).collectList()
                .timeout(timeout)
                .map(Optional::of)
                .onErrorResume(ex -> this.<List<T>>fallback(nome, ex));
    }

    private <T> Mono<Optional<T>> objeto(WebClient client, String uri, Class<T> tipo, String cid, String nome) {
        return client.get().uri(uri)
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToMono(tipo)
                .timeout(timeout)
                .map(Optional::of)
                .onErrorResume(ex -> this.<T>fallback(nome, ex));
    }

    private Mono<Map<String, Object>> health(String nome, WebClient client, String url) {
        long inicio = System.currentTimeMillis();
        return client.get().uri("/actuator/health")
                .exchangeToMono(resp -> resp.releaseBody()
                        .thenReturn(resp.statusCode().is2xxSuccessful() ? "UP" : "DOWN"))
                .timeout(timeout)
                .onErrorResume(ex -> Mono.just("DOWN"))
                .map(st -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("servico", nome);
                    m.put("url", url);
                    m.put("status", st);
                    m.put("latenciaMs", System.currentTimeMillis() - inicio);
                    return m;
                });
    }

    private <T> Mono<Optional<T>> fallback(String servico, Throwable ex) {
        log.warn("[BFF FALLBACK] {} indisponível: {}", servico, ex.toString());
        return Mono.just(Optional.empty());
    }

    private ResponseStatusException indisponivel(String servico, Throwable ex) {
        log.error("[BFF] Falha ao consultar {}: {}", servico, ex.toString());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, servico + " indisponível");
    }
}
