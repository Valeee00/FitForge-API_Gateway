package br.fitforge.gateway.application.port.in;

import br.fitforge.gateway.application.model.DashboardBffDTO;
import br.fitforge.gateway.application.model.FichaDetalhadaBffDTO;
import br.fitforge.gateway.application.model.SessaoEnriquecidaDTO;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/** Porta de entrada: casos de uso do Backend For Frontend (uma chamada por tela). */
public interface BffUseCase {
    Mono<DashboardBffDTO> dashboard(String correlationId);

    Mono<FichaDetalhadaBffDTO> fichaDetalhada(String fichaId, String correlationId);

    Mono<List<SessaoEnriquecidaDTO>> historico(String periodo, String correlationId);

    Mono<Map<String, Object>> status();
}
