package br.fitforge.gateway.bff.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Tela "Visão geral": 1 chamada do navegador = 3 chamadas paralelas no backend. */
public record DashboardBffDTO(
        String hoje,
        List<WorkoutDTO> treinosDeHoje,
        long totalFichas,
        long totalExercicios,
        BigDecimal volumePlanejadoKg,
        long totalSessoes,
        long minutosTotais,
        long sessoesNaSemana,
        long minutosNaSemana,
        Double esforcoMedio,
        BigDecimal ultimoPesoKg,
        int sequenciaDiasSeguidos,
        List<DiaSemanaDTO> semana,
        List<FichaResumoDTO> fichas,
        List<SessaoEnriquecidaDTO> ultimasSessoes,
        Map<String, String> statusMicrosservicos,
        LocalDateTime geradoEm
) {}
