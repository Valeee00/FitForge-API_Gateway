package br.fitforge.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger do gateway, com o esquema de segurança X-API-Key (botão "Authorize"). */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("FitForge — API Gateway & BFF").version("2.0.0")
                        .description("Ponto único de entrada: roteamento (/api/treinos, /api/sessoes), BFF (/api/bff), "
                                + "CORS, X-API-Key, rate limit e cabeçalhos de segurança. Arquitetura Hexagonal. "
                                + "Use o seletor no topo para ver também o Swagger de cada microsserviço."))
                .components(new Components().addSecuritySchemes("apiKey",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")));
    }
}
