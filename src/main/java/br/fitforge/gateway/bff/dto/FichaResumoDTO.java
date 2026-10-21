package br.fitforge.gateway.bff.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ficha já combinada com o seu uso real (dados de 2 microsserviços). */
public record FichaResumoDTO(String id, String name, List<String> days, int quantidadeExercicios,
                             BigDecimal volumeKg, long sessoesRealizadas, LocalDate ultimaSessao) {}
