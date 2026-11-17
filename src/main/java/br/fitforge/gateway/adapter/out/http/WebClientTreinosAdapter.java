package br.fitforge.gateway.adapter.out.http;

import br.fitforge.gateway.application.exception.ServicoIndisponivelException;
import br.fitforge.gateway.application.model.WorkoutDTO;
import br.fitforge.gateway.application.port.out.TreinosClientPort;
import br.fitforge.gateway.infrastructure.observability.CorrelationIdFilter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/** Adaptador de saída HTTP (WebClient reativo) para o treinos-service. */
public class WebClientTreinosAdapter implements TreinosClientPort {

    private static final ParameterizedTypeReference<List<WorkoutDTO>> LISTA = new ParameterizedTypeReference<>() {};

    private final WebClient client;
    private final String url;
    private final Duration timeout;

    public WebClientTreinosAdapter(WebClient client, String url, Duration timeout) {
        this.client = client;
        this.url = url;
        this.timeout = timeout;
    }

    @Override
    public Mono<List<WorkoutDTO>> listarFichas(String cid) {
        return client.get().uri("/api/treinos")
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToMono(LISTA)
                .timeout(timeout);
    }

    @Override
    public Mono<WorkoutDTO> buscarFicha(String id, String cid) {
        return client.get().uri("/api/treinos/{id}", id)
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToMono(WorkoutDTO.class)
                .timeout(timeout)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty())
                .onErrorMap(ex -> !(ex instanceof ServicoIndisponivelException),
                        ex -> new ServicoIndisponivelException("treinos-service"));
    }

    @Override
    public Mono<String> saude() {
        return HealthCheck.verificar(client, timeout);
    }

    @Override
    public String endereco() {
        return url;
    }
}
