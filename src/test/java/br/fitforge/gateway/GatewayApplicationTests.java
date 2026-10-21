package br.fitforge.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Testes de fumaça: sobem o gateway SEM os microsserviços para garantir que
 * o contexto carrega, que o BFF degrada com fallback e que o Correlation-Id
 * é sempre devolvido.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
        "services.treinos.url=http://localhost:1",
        "services.progresso.url=http://localhost:1",
        "services.timeout-seconds=2"
})
class GatewayApplicationTests {

    @Autowired
    WebTestClient client;

    @Test
    void dashboardResponde200MesmoComServicosForaDoAr() {
        client.get().uri("/api/bff/dashboard").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.statusMicrosservicos['treinos-service']").isEqualTo("OFFLINE")
                .jsonPath("$.statusMicrosservicos['progresso-service']").isEqualTo("OFFLINE");
    }

    @Test
    void statusInformaServicosDown() {
        client.get().uri("/api/bff/status").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.microsservicos[0].status").isEqualTo("DOWN");
    }

    @Test
    void correlationIdRecebidoEhPropagado() {
        client.get().uri("/actuator/health").header("X-Correlation-Id", "teste123").exchange()
                .expectHeader().valueEquals("X-Correlation-Id", "teste123");
    }

    @Test
    void fichaDetalhadaRetorna502ComTreinosForaDoAr() {
        client.get().uri("/api/bff/treinos/template-a").exchange()
                .expectStatus().isEqualTo(502);
    }

    @Test
    void rotaDoProxyRespondeErroDeGatewayComServicoForaDoAr() {
        client.get().uri("/api/treinos").exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void paginaInicialCarrega() {
        client.get().uri("/index.html").exchange().expectStatus().isOk();
    }
}
