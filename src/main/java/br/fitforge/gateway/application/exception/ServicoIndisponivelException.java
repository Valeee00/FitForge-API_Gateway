package br.fitforge.gateway.application.exception;

/** Um microsserviço necessário para a resposta está fora do ar (HTTP 502). */
public class ServicoIndisponivelException extends RuntimeException {
    public ServicoIndisponivelException(String servico) {
        super(servico + " indisponível");
    }
}
