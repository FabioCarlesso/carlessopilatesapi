package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PagamentoRequestDTO(
        @NotNull(message = "pacienteId é obrigatório") Long pacienteId,
        @NotNull(message = "planoId é obrigatório") Long planoId,
        @NotNull(message = "valor é obrigatório") @DecimalMin(value = "0.01", message = "valor deve ser maior que zero") BigDecimal valor,
        @NotNull(message = "dataVencimento é obrigatória") LocalDate dataVencimento,
        @NotNull(message = "periodoInicio é obrigatório") LocalDate periodoInicio) {}
