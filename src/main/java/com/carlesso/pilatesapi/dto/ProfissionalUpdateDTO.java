package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfissionalUpdateDTO(
        String nome,
        @Email String email,
        String telefone,
        TipoContrato tipoContrato,
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percentualPagamentoAula,
        LocalDate dataInicio
) {}
