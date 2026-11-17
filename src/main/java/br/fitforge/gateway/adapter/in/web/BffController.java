package br.fitforge.gateway.adapter.in.web;

import br.fitforge.gateway.application.model.DashboardBffDTO;
import br.fitforge.gateway.application.model.FichaDetalhadaBffDTO;
import br.fitforge.gateway.application.model.SessaoEnriquecidaDTO;
import br.fitforge.gateway.application.port.in.BffUseCase;
import br.fitforge.gateway.infrastructure.observability.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/** Adaptador de entrada HTTP do BFF: cada endpoint atende uma tela do FitForge com UMA requisição. */
@RestController
@RequestMapping("/api/bff")
@Tag(name = "BFF - Backend For Frontend", description = "Agregação de treinos-service (MongoDB) + progresso-service (Azure SQL) em uma única resposta")
@SecurityRequirement(name = "apiKey")
public class BffController {

    private final BffUseCase bff;

    public BffController(BffUseCase bff) {
        this.bff = bff;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Visão geral (agregação)",
            description = "Consulta em paralelo o Microsserviço Mongo (fichas) e o Microsserviço SQL (sessões + indicadores) e devolve tudo junto.")
    public Mono<DashboardBffDTO> dashboard(ServerWebExchange exchange) {
        return bff.dashboard(cid(exchange));
    }

    @GetMapping("/treinos/{id}")
    @Operation(summary = "Ficha + histórico (composição)",
            description = "Ficha do treinos-service composta com todas as sessões dela no progresso-service.")
    public Mono<ResponseEntity<FichaDetalhadaBffDTO>> fichaDetalhada(@PathVariable("id") String id, ServerWebExchange exchange) {
        return bff.fichaDetalhada(id, cid(exchange))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/historico")
    @Operation(summary = "Histórico enriquecido", description = "Sessões + situação atual da ficha (excluída ou renomeada no treinos-service).")
    public Mono<List<SessaoEnriquecidaDTO>> historico(@Parameter(description = "semana, mes ou tudo")
                                                      @RequestParam(name = "periodo", required = false) String periodo,
                                                      ServerWebExchange exchange) {
        return bff.historico(periodo, cid(exchange));
    }

    @GetMapping("/status")
    @Operation(summary = "Status da arquitetura", description = "Rotas e health check real (UP/DOWN + latência) de cada microsserviço.")
    public Mono<Map<String, Object>> status() {
        return bff.status();
    }

    private static String cid(ServerWebExchange exchange) {
        String cid = exchange.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return cid != null ? cid : "";
    }
}
