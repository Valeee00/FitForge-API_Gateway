package br.fitforge.gateway.adapter.out.http;

import br.fitforge.gateway.application.exception.RequisicaoInvalidaException;
import br.fitforge.gateway.application.exception.ServicoIndisponivelException;
import br.fitforge.gateway.application.model.ProgressoStatsDTO;
import br.fitforge.gateway.application.model.SessionDTO;
import br.fitforge.gateway.application.port.out.ProgressoClientPort;
import br.fitforge.gateway.infrastructure.observability.CorrelationIdFilter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/** Adaptador de saída HTTP (WebClient reativo) para o progresso-service. */
public class WebClientProgressoAdapter implements ProgressoClientPort {

    private static final ParameterizedTypeReference<List<SessionDTO>> LISTA = new ParameterizedTypeReference<>() {};

    private final WebClient client;
    private final String url;
    private final Duration timeout;

    public WebClientProgressoAdapter(WebClient client, String url, Duration timeout) {
        this.client = client;
        this.url = url;
        this.timeout = timeout;
    }

    @Override
    public Mono<List<SessionDTO>> listarSessoes(String cid) {
        return client.get().uri("/api/sessoes")
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToMono(LISTA)
                .timeout(timeout);
    }

    @Override
    public Mono<List<SessionDTO>> listarSessoesDaFicha(String fichaId, String cid) {
        return client.get().uri(b -> b.path("/api/sessoes").queryParam("workoutId", fichaId).build())
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToMono(LISTA)
                .timeout(timeout);
    }

    @Override
    public Mono<List<SessionDTO>> listarSessoesDoPeriodo(String periodo, String cid) {
        return client.get()
                .uri(b -> {
                    b.path("/api/sessoes");
                    if (periodo != null && !periodo.isBlank()) b.queryParam("periodo", periodo);
                    return b.build();
                })
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToMono(LISTA)
                .timeout(timeout)
                .onErrorMap(WebClientResponseException.BadRequest.class,
                        ex -> new RequisicaoInvalidaException("Período inválido. Use semana, mes ou tudo."))
                .onErrorMap(ex -> !(ex instanceof RequisicaoInvalidaException),
                        ex -> new ServicoIndisponivelException("progresso-service"));
    }

    @Override
    public Mono<ProgressoStatsDTO> estatisticas(String cid) {
        return client.get().uri("/api/sessoes/estatisticas")
                .header(CorrelationIdFilter.HEADER, cid)
                .retrieve().bodyToMono(ProgressoStatsDTO.class)
                .timeout(timeout);
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
