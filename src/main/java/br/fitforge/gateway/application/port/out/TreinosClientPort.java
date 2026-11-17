package br.fitforge.gateway.application.port.out;

import br.fitforge.gateway.application.model.WorkoutDTO;
import reactor.core.publisher.Mono;

import java.util.List;

/** Porta de saída: acesso ao Microsserviço 1 (treinos-service / MongoDB). */
public interface TreinosClientPort {
    Mono<List<WorkoutDTO>> listarFichas(String correlationId);

    /** Vazio se a ficha não existir; erro {@code ServicoIndisponivelException} se o serviço estiver fora. */
    Mono<WorkoutDTO> buscarFicha(String id, String correlationId);

    /** "UP" ou "DOWN". */
    Mono<String> saude();

    String endereco();
}
