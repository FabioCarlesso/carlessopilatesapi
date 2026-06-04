package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NotaFiscalEmitidaRequestDTO(
        @NotNull Long pacienteId,
        @NotNull @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{4}$", message = "competencia deve estar no formato MM/AAAA")
        String competencia,
        String numeroNota,
        @NotNull LocalDate dataEmissao,
        BigDecimal valor,
        String observacoes
) {}
