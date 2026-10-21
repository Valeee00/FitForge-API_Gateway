package br.fitforge.gateway.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contrato de GET /api/sessoes/estatisticas (progresso-service). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgressoStatsDTO(long totalSessoes, long minutosTotais, long sessoesNaSemana, long minutosNaSemana,
                                long sessoesNoMes, Double esforcoMedio, BigDecimal ultimoPesoKg,
                                LocalDate ultimaSessao, int sequenciaDiasSeguidos, List<DiaSemanaDTO> semana) {}
