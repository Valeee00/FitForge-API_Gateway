package br.fitforge.gateway.config;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

/** Página inicial do Web App: redireciona "/" para o painel estático index.html. */
@Hidden
@RestController
public class HomeController {
    @GetMapping("/")
    public Mono<ResponseEntity<Void>> home() {
        return Mono.just(ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/index.html")).build());
    }
}
