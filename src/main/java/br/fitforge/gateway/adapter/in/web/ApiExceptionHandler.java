package br.fitforge.gateway.adapter.in.web;

import br.fitforge.gateway.application.exception.RequisicaoInvalidaException;
import br.fitforge.gateway.application.exception.ServicoIndisponivelException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Traduz as exceções da aplicação em respostas HTTP padronizadas. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ServicoIndisponivelException.class)
    public ResponseEntity<Map<String, Object>> indisponivel(ServicoIndisponivelException ex) {
        return corpo(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> invalida(RequisicaoInvalidaException ex) {
        return corpo(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> corpo(HttpStatus status, String mensagem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", mensagem);
        return ResponseEntity.status(status).body(body);
    }
}
