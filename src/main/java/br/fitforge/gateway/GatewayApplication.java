package br.fitforge.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FitForge — API Gateway & BFF (camada de interface do backend).
 *
 * Arquitetura Hexagonal (Ports & Adapters):
 *   application    → BFF (casos de uso + portas para os microsserviços)
 *   adapter/in/web → controller REST do BFF
 *   adapter/out    → clientes HTTP reativos (WebClient) dos microsserviços
 *   infrastructure → segurança (API key, rate limit, headers) e observabilidade (correlation id, logs)
 *   application.yml→ rotas do Spring Cloud Gateway (proxy reverso do CRUD)
 */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
