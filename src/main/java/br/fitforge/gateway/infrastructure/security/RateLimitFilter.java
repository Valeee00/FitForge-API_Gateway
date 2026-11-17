package br.fitforge.gateway.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Segurança: limite de requisições por IP (janela fixa de 1 minuto) nas rotas /api/**.
 * Protege os microsserviços (e a cota do plano gratuito) contra abuso. Excesso → HTTP 429.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class RateLimitFilter implements WebFilter {

    private final int limitePorMinuto;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.security.rate-limit-per-minute:120}") int limitePorMinuto) {
        this.limitePorMinuto = limitePorMinuto;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        if (limitePorMinuto <= 0 || HttpMethod.OPTIONS.equals(req.getMethod())
                || !req.getURI().getRawPath().startsWith("/api/")) {
            return chain.filter(exchange);
        }
        long minuto = System.currentTimeMillis() / 60_000;
        Janela janela = janelas.compute(cliente(req), (k, atual) ->
                (atual == null || atual.minuto != minuto) ? new Janela(minuto) : atual);
        if (janela.contador.incrementAndGet() > limitePorMinuto) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().set("Retry-After", "60");
            return exchange.getResponse().setComplete();
        }
        if (janelas.size() > 10_000) janelas.entrySet().removeIf(e -> e.getValue().minuto != minuto);
        return chain.filter(exchange);
    }

    static String cliente(ServerHttpRequest req) {
        String xff = req.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        InetSocketAddress remoto = req.getRemoteAddress();
        return remoto != null && remoto.getAddress() != null ? remoto.getAddress().getHostAddress() : "desconhecido";
    }

    private static final class Janela {
        final long minuto;
        final AtomicInteger contador = new AtomicInteger();

        Janela(long minuto) {
            this.minuto = minuto;
        }
    }
}
