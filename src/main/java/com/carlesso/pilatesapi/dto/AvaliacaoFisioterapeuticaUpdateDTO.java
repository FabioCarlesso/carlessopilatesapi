package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record AvaliacaoFisioterapeuticaUpdateDTO(
        LocalDate dataAvaliacao,
        @Pattern(regexp = ".*\\S.*", message = "queixaFuncional não pode ser vazio") String queixaFuncional,
        String avaliacaoPostural,
        String mobilidadeArticular,
        String forcaMuscular,
        String flexibilidade,
        String equilibrio,
        String coordenacaoMotora,
        String padraoRespiratorio,
        @Min(0) @Max(10) Integer escalaDor,
        String testesFuncionaisRealizados,
        @Pattern(regexp = ".*\\S.*", message = "diagnosticoFisioterapeutico não pode ser vazio") String diagnosticoFisioterapeutico,
        String observacoesGerais) {}
