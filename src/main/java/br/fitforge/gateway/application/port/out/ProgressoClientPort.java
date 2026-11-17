package br.fitforge.gateway.application.port.out;

import br.fitforge.gateway.application.model.ProgressoStatsDTO;
import br.fitforge.gateway.application.model.SessionDTO;
import reactor.core.publisher.Mono;

import java.util.List;

/** Porta de saída: acesso ao Microsserviço 2 (progresso-service / Azure SQL). */
public interface ProgressoClientPort {
    Mono<List<SessionDTO>> listarSessoes(String correlationId);

    Mono<List<SessionDTO>> listarSessoesDaFicha(String fichaId, String correlationId);

    /** Erro {@code RequisicaoInvalidaException} se o período for inválido. */
    Mono<List<SessionDTO>> listarSessoesDoPeriodo(String periodo, String correlationId);

    Mono<ProgressoStatsDTO> estatisticas(String correlationId);

    Mono<String> saude();

    String endereco();
}
