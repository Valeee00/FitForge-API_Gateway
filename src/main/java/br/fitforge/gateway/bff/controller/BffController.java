package br.fitforge.gateway.bff.controller;

import br.fitforge.gateway.bff.dto.DashboardBffDTO;
import br.fitforge.gateway.bff.dto.FichaDetalhadaBffDTO;
import br.fitforge.gateway.bff.dto.SessaoEnriquecidaDTO;
import br.fitforge.gateway.bff.service.BffService;
import br.fitforge.gateway.filter.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/** Endpoints do BFF: cada um atende uma tela do FitForge com UMA requisição. */
@RestController
@RequestMapping("/api/bff")
@Tag(name = "BFF - Backend For Frontend", description = "Composição de treinos-service + progresso-service para as telas do FitForge")
public class BffController {

    private final BffService bff;

    public BffController(BffService bff) {
        this.bff = bff;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Visão geral",
            description = "Fichas + sessões + indicadores em paralelo: treinos de hoje, ritmo da semana, uso de cada ficha e últimas sessões.")
    public Mono<DashboardBffDTO> dashboard(ServerWebExchange exchange) {
        return bff.dashboard(cid(exchange));
    }

    @GetMapping("/treinos/{id}")
    @Operation(summary = "Ficha + histórico",
            description = "Ficha do treinos-service composta com todas as sessões dela no progresso-service (total, minutos, esforço médio).")
    public Mono<ResponseEntity<FichaDetalhadaBffDTO>> fichaDetalhada(@PathVariable("id") String id, ServerWebExchange exchange) {
        return bff.fichaDetalhada(id, cid(exchange))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/historico")
    @Operation(summary = "Histórico enriquecido",
            description = "Sessões + situação atual da ficha (excluída ou renomeada no treinos-service).")
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
