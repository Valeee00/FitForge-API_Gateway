package br.fitforge.gateway.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Segurança: exige o cabeçalho X-API-Key nas rotas /api/** quando GATEWAY_API_KEY está definida.
 * Os microsserviços ficam atrás do gateway: o frontend só fala com este ponto único de entrada.
 * Rotas públicas: /api/bff/status (painel), Swagger e actuator/health.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class ApiKeyFilter implements WebFilter {

    public static final String HEADER = "X-API-Key";

    private final byte[] chave;

    public ApiKeyFilter(@Value("${app.security.api-key:}") String chave) {
        this.chave = chave == null ? new byte[0] : chave.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getRawPath();
        boolean protegido = path.startsWith("/api/") && !path.equals("/api/bff/status");
        if (chave.length == 0 || !protegido || HttpMethod.OPTIONS.equals(req.getMethod())) {
            return chain.filter(exchange);
        }
        String recebida = req.getHeaders().getFirst(HEADER);
        if (recebida != null && MessageDigest.isEqual(chave, recebida.getBytes(StandardCharsets.UTF_8))) {
            return chain.filter(exchange);
        }
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.UNAUTHORIZED);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer corpo = resp.bufferFactory().wrap(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Cabeçalho X-API-Key ausente ou inválido.\"}"
                        .getBytes(StandardCharsets.UTF_8));
        return resp.writeWith(Mono.just(corpo));
    }
}
