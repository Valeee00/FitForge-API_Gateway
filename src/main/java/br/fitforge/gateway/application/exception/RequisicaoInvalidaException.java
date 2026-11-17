package br.fitforge.gateway.application.exception;

/** Parâmetro inválido enviado pelo frontend (HTTP 400). */
public class RequisicaoInvalidaException extends RuntimeException {
    public RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
