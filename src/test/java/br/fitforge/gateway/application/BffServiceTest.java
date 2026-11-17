package br.fitforge.gateway.application;

import br.fitforge.gateway.application.exception.ServicoIndisponivelException;
import br.fitforge.gateway.application.model.*;
import br.fitforge.gateway.application.port.out.ProgressoClientPort;
import br.fitforge.gateway.application.port.out.TreinosClientPort;
import br.fitforge.gateway.application.service.BffService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários do núcleo do BFF, com portas falsas (sem Spring, sem rede). */
class BffServiceTest {

    // 2026-11-16 é segunda-feira
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneId.of("UTC"));

    private static final WorkoutDTO FICHA = new WorkoutDTO("f1", "Treino A", "desc", List.of("Seg", "Qui"),
            List.of(new ExerciseDTO("e1", "Supino", "Peito", 3, 10, new BigDecimal("20"), 60)), 0, null);
    private static final SessionDTO SESSAO = new SessionDTO("s1", "f1", "Treino A", LocalDate.of(2026, 11, 15),
            45, 8, new BigDecimal("78.5"), "", true);
    private static final SessionDTO SESSAO_ORFA = new SessionDTO("s2", "removida", "Treino antigo", LocalDate.of(2026, 11, 10),
            30, 6, null, "", false);

    private static TreinosClientPort treinos(boolean online) {
        return new TreinosClientPort() {
            public Mono<List<WorkoutDTO>> listarFichas(String c) {
                return online ? Mono.just(List.of(FICHA)) : Mono.error(new ServicoIndisponivelException("treinos"));
            }
            public Mono<WorkoutDTO> buscarFicha(String id, String c) {
                return online && id.equals("f1") ? Mono.just(FICHA) : online ? Mono.empty() : Mono.error(new ServicoIndisponivelException("treinos"));
            }
            public Mono<String> saude() { return Mono.just(online ? "UP" : "DOWN"); }
            public String endereco() { return "fake://treinos"; }
        };
    }

    private static ProgressoClientPort progresso(boolean online) {
        return new ProgressoClientPort() {
            public Mono<List<SessionDTO>> listarSessoes(String c) {
                return online ? Mono.just(List.of(SESSAO, SESSAO_ORFA)) : Mono.error(new ServicoIndisponivelException("progresso"));
            }
            public Mono<List<SessionDTO>> listarSessoesDaFicha(String id, String c) {
                return online ? Mono.just(List.of(SESSAO)) : Mono.error(new ServicoIndisponivelException("progresso"));
            }
            public Mono<List<SessionDTO>> listarSessoesDoPeriodo(String p, String c) { return listarSessoes(c); }
            public Mono<ProgressoStatsDTO> estatisticas(String c) {
                return online ? Mono.just(new ProgressoStatsDTO(2, 75, 1, 45, 2, 7.0, new BigDecimal("78.5"),
                        LocalDate.of(2026, 11, 15), 1, List.of())) : Mono.error(new ServicoIndisponivelException("progresso"));
            }
            public Mono<String> saude() { return Mono.just(online ? "UP" : "DOWN"); }
            public String endereco() { return "fake://progresso"; }
        };
    }

    private static BffService bff(boolean t, boolean p) { return new BffService(treinos(t), progresso(p), RELOGIO); }

    @Test
    void dashboardAgregaOsDoisServicos() {
        StepVerifier.create(bff(true, true).dashboard("c1")).assertNext(d -> {
            assertEquals("Seg", d.hoje());
            assertEquals(1, d.treinosDeHoje().size());
            assertEquals(1, d.totalFichas());
            assertEquals(2L, d.totalSessoes());
            assertEquals("ONLINE", d.statusMicrosservicos().get("treinos-service"));
            assertEquals("ONLINE", d.statusMicrosservicos().get("progresso-service"));
        }).verifyComplete();
    }

    @Test
    void dashboardComProgressoForaDoArUsaFallback() {
        StepVerifier.create(bff(true, false).dashboard("c1")).assertNext(d -> {
            assertEquals("OFFLINE", d.statusMicrosservicos().get("progresso-service"));
            assertEquals("ONLINE", d.statusMicrosservicos().get("treinos-service"));
            assertEquals(1, d.totalFichas());
            assertEquals(0L, d.totalSessoes());
        }).verifyComplete();
    }

    @Test
    void dashboardComTreinosForaDoArUsaFallback() {
        StepVerifier.create(bff(false, true).dashboard("c1")).assertNext(d -> {
            assertEquals("OFFLINE", d.statusMicrosservicos().get("treinos-service"));
            assertEquals(0, d.totalFichas());
        }).verifyComplete();
    }

    @Test
    void historicoMarcaFichaExcluida() {
        StepVerifier.create(bff(true, true).historico("mes", "c1")).assertNext(lista -> {
            assertEquals(2, lista.size());
            assertTrue(lista.stream().filter(s -> s.sessao().id().equals("s1")).findFirst().orElseThrow().fichaExiste());
            assertFalse(lista.stream().filter(s -> s.sessao().id().equals("s2")).findFirst().orElseThrow().fichaExiste());
        }).verifyComplete();
    }

    @Test
    void fichaDetalhadaCalculaTotaisDoHistorico() {
        StepVerifier.create(bff(true, true).fichaDetalhada("f1", "c1")).assertNext(f -> {
            assertEquals(1, f.totalSessoes());
            assertEquals(45L, f.minutosTotais());
            assertEquals(8.0, f.esforcoMedio());
            assertTrue(f.historicoDisponivel());
        }).verifyComplete();
    }

    @Test
    void fichaDetalhadaFalhaQuandoTreinosEstaForaDoAr() {
        StepVerifier.create(bff(false, true).fichaDetalhada("f1", "c1"))
                .expectError(ServicoIndisponivelException.class).verify();
    }

    @Test
    void statusReportaSaudeDeCadaServico() {
        StepVerifier.create(bff(true, false).status()).assertNext(m -> {
            @SuppressWarnings("unchecked") var lista = (List<java.util.Map<String, Object>>) m.get("microsservicos");
            assertEquals("UP", lista.get(0).get("status"));
            assertEquals("DOWN", lista.get(1).get("status"));
        }).verifyComplete();
    }
}
