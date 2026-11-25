package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Frequência e faltas por paciente no período (#233): aulas previstas, realizadas e não realizadas (faltas), com a taxa
 * de presença. Só contam as aulas com data anterior a hoje — a de hoje e as futuras ainda podem ser realizadas.
 */
public record RelatorioFrequenciaDTO(
        PeriodoDTO periodo,
        @Schema(
                        description = "Última data considerada: o menor entre o fim do período e ontem (fuso do"
                                + " estúdio). Se for anterior ao início, nenhuma aula conta e a lista vem vazia")
                LocalDate consideradoAte,
        Filtros filtros,
        Totais totais,
        @Schema(description = "Um item por paciente com ao menos uma aula considerada, pela menor taxa de presença")
                List<PacienteFrequencia> pacientes,
        LocalDateTime geradoEm) {

    @Schema(description = "Filtros aplicados; null quando o filtro não foi informado")
    public record Filtros(Long profissionalId, String profissionalNome, Long pacienteId, BigDecimal taxaMaxima) {}

    @Schema(description = "Somas das linhas do relatório; taxaPresenca é null quando não há aula considerada")
    public record Totais(
            long pacientes, long aulasPrevistas, long aulasRealizadas, long faltas, BigDecimal taxaPresenca) {}

    public record PacienteFrequencia(
            Long pacienteId,
            String pacienteNome,
            boolean ativo,
            long aulasPrevistas,
            long aulasRealizadas,
            @Schema(description = "Aulas previstas e não realizadas") long faltas,
            @Schema(
                            description = "Percentual de presença (0–100), realizadas / previstas, com 1 casa decimal"
                                    + " (arredondamento HALF_UP)",
                            example = "75.0")
                    BigDecimal taxaPresenca,
            @Schema(description = "Data da última aula realizada no período considerado; null se nenhuma")
                    LocalDate ultimaAulaRealizada) {}
}
