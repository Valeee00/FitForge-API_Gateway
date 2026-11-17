package br.fitforge.gateway.adapter.out.http;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Consulta /actuator/health de um microsserviço e devolve "UP" ou "DOWN". */
final class HealthCheck {

    private HealthCheck() {
    }

    static Mono<String> verificar(WebClient client, Duration timeout) {
        return client.get().uri("/actuator/health")
                .exchangeToMono(resp -> resp.releaseBody()
                        .thenReturn(resp.statusCode().is2xxSuccessful() ? "UP" : "DOWN"))
                .timeout(timeout)
                .onErrorResume(ex -> Mono.just("DOWN"));
    }
}
