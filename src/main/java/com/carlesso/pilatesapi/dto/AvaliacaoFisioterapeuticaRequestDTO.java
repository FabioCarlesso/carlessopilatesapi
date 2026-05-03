package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AvaliacaoFisioterapeuticaRequestDTO(
        @NotNull Long pacienteId,
        @NotNull LocalDate dataAvaliacao,
        @NotBlank String queixaFuncional,
        String avaliacaoPostural,
        String mobilidadeArticular,
        String forcaMuscular,
        String flexibilidade,
        String equilibrio,
        String coordenacaoMotora,
        String padraoRespiratorio,
        @NotNull @Min(0) @Max(10) Integer escalaDor,
        String testesFuncionaisRealizados,
        @NotBlank String diagnosticoFisioterapeutico,
        String observacoesGerais
) {}
