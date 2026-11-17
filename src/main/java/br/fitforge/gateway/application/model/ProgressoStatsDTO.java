package br.fitforge.gateway.application.model;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contrato de GET /api/sessoes/estatisticas (progresso-service). */
public record ProgressoStatsDTO(long totalSessoes, long minutosTotais, long sessoesNaSemana, long minutosNaSemana,
                                long sessoesNoMes, Double esforcoMedio, BigDecimal ultimoPesoKg,
                                LocalDate ultimaSessao, int sequenciaDiasSeguidos, List<DiaSemanaDTO> semana) {}
