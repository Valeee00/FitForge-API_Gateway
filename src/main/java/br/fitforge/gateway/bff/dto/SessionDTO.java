package br.fitforge.gateway.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Sessão de treino (contrato do progresso-service, GET /api/sessoes). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionDTO(String id, String workoutId, String workoutName, LocalDate date,
                         int duration, int effort, BigDecimal bodyWeight, String notes) {}
