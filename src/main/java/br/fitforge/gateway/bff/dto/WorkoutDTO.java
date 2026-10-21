package br.fitforge.gateway.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** Ficha de treino (contrato do treinos-service, GET /api/treinos). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutDTO(String id, String name, String description, List<String> days, List<ExerciseDTO> exercises) {

    public List<String> days() {
        return days == null ? List.of() : days;
    }

    public List<ExerciseDTO> exercises() {
        return exercises == null ? List.of() : exercises;
    }

    public BigDecimal volume() {
        return exercises().stream().map(ExerciseDTO::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
