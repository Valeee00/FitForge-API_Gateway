package br.fitforge.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Observabilidade centralizada: registra entrada/saída de cada requisição,
 * mede a latência e injeta headers de auditoria na resposta.
 *
 * Os headers são escritos em beforeCommit(), ou seja, imediatamente antes de a
 * resposta ser enviada — depois disso os headers HTTP ficam somente leitura.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GlobalLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(GlobalLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long inicio = System.currentTimeMillis();
        String metodo = exchange.getRequest().getMethod().name();
        String caminho = exchange.getRequest().getURI().getRawPath();
        String cid = exchange.getAttribute(CorrelationIdFilter.ATTRIBUTE);

        if (caminho.startsWith("/api/")) {
            log.info("[GATEWAY IN ] {} {} | cid={}", metodo, caminho, cid);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            long duracao = System.currentTimeMillis() - inicio;
            response.getHeaders().set("X-Gateway-Processed-By", "FitForge-Spring-Cloud-Gateway");
            response.getHeaders().set("X-Gateway-Execution-Time-Ms", String.valueOf(duracao));
            if (caminho.startsWith("/api/bff")) {
                response.getHeaders().set("X-Gateway-Route", "bff-local");
            }
            return Mono.empty();
        });

        return chain.filter(exchange).doFinally(signal -> {
            if (!caminho.startsWith("/api/")) return;
            Route rota = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String alvo = rota != null ? rota.getId() + " -> " + rota.getUri() : "BFF (local)";
            int status = response.getStatusCode() != null ? response.getStatusCode().value() : 0;
            log.info("[GATEWAY OUT] {} {} -> HTTP {} ({} ms) | alvo: {} | cid={}",
                    metodo, caminho, status, System.currentTimeMillis() - inicio, alvo, cid);
        });
    }
}
