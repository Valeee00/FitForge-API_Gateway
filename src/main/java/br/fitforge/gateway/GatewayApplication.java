package br.fitforge.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FitForge — camada de interface do backend (Atividade 16).
 *
 * Um único processo exerce dois papéis:
 *  1. API GATEWAY (Spring Cloud Gateway): proxy reverso transparente para os
 *     microsserviços de domínio (rotas declaradas no application.yml).
 *  2. BFF (Backend For Frontend): endpoints /api/bff/** que agregam e moldam
 *     dados de vários microsserviços em uma única resposta para as telas.
 */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
