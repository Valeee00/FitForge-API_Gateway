package br.fitforge.gateway.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Exercício (contrato do treinos-service). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExerciseDTO(String id, String name, String muscle, int sets, int reps, BigDecimal load, int rest) {

    public BigDecimal volume() {
        return load == null ? BigDecimal.ZERO : load.multiply(BigDecimal.valueOf((long) sets * reps));
    }
}
