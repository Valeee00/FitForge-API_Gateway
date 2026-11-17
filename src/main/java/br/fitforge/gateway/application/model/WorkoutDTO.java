package br.fitforge.gateway.application.model;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ficha de treino (contrato do treinos-service, GET /api/treinos). */
public record WorkoutDTO(String id, String name, String description, List<String> days, List<ExerciseDTO> exercises,
                         int totalSessoes, LocalDate ultimaSessao) {

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
