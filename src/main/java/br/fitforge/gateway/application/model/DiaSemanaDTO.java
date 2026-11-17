package br.fitforge.gateway.application.model;


import java.time.LocalDate;

/** Um dia da semana atual (formato de weeklyBuckets() do frontend). */
public record DiaSemanaDTO(LocalDate date, String label, long count, long minutes) {}
