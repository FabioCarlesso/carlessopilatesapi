package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.util.CompetenciaUtils;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NotaFiscalEmitidaRequestDTO(
        @NotNull Long pacienteId,
        @NotNull @Pattern(regexp = CompetenciaUtils.COMPETENCIA_REGEX, message = "competencia deve estar no formato MM/AAAA")
        String competencia,
        @Size(max = 60, message = "numeroNota deve ter no máximo 60 caracteres") String numeroNota,
        @NotNull LocalDate dataEmissao,
        BigDecimal valor,
        String observacoes
) {}
