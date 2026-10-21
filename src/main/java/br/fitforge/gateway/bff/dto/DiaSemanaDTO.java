package br.fitforge.gateway.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/** Um dia da semana atual (formato de weeklyBuckets() do frontend). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiaSemanaDTO(LocalDate date, String label, long count, long minutes) {}
