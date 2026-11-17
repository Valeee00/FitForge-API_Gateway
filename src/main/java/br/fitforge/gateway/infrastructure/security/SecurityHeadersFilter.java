package br.fitforge.gateway.infrastructure.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Segurança: cabeçalhos HTTP de proteção em todas as respostas do ponto único de entrada. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class SecurityHeadersFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        boolean api = exchange.getRequest().getURI().getRawPath().startsWith("/api/");
        response.beforeCommit(() -> {
            HttpHeaders h = response.getHeaders();
            h.set("X-Content-Type-Options", "nosniff");
            h.set("X-Frame-Options", "DENY");
            h.set("Referrer-Policy", "no-referrer");
            h.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            if (api) h.set(HttpHeaders.CACHE_CONTROL, "no-store");
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
