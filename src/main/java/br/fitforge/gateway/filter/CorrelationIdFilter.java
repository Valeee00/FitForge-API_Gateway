package br.fitforge.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Rastreabilidade distribuída: garante um X-Correlation-Id em TODA requisição.
 *
 * É um WebFilter (e não um GlobalFilter do gateway) de propósito: assim ele vale
 * tanto para as rotas de proxy (/api/treinos, /api/sessoes) quanto para os
 * endpoints do BFF (/api/bff/**), que são controllers comuns.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
            log.debug("[TRACE] Novo Correlation-Id gerado: {}", correlationId);
        }

        // Propaga para o microsserviço de destino e devolve ao frontend
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        exchange.getAttributes().put(ATTRIBUTE, correlationId);

        return chain.filter(exchange.mutate().request(request).build());
    }
}
