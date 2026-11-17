package br.fitforge.gateway.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Ficha + histórico de sessões dela (composição treinos-service + progresso-service). */
public record FichaDetalhadaBffDTO(
        WorkoutDTO ficha,
        BigDecimal volumeKg,
        long totalSessoes,
        long minutosTotais,
        Double esforcoMedio,
        LocalDate ultimaSessao,
        List<SessionDTO> historico,
        boolean historicoDisponivel,
        LocalDateTime geradoEm
) {}
