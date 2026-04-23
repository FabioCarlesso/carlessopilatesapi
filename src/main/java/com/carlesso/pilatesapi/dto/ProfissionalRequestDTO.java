package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfissionalRequestDTO(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank String cpf,
        String telefone,
        @NotBlank String tipoContrato,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percentualPagamentoAula,
        @NotNull LocalDate dataInicio
) {}
