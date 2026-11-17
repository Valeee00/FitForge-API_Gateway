package br.fitforge.gateway.application.model;


import java.math.BigDecimal;
import java.time.LocalDate;

/** Sessão de treino (contrato do progresso-service, GET /api/sessoes). */
public record SessionDTO(String id, String workoutId, String workoutName, LocalDate date,
                         int duration, int effort, BigDecimal bodyWeight, String notes, boolean fichaAtiva) {}
