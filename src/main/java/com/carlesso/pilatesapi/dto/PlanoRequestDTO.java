package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public record PlanoRequestDTO(
        @NotNull(message = "pacienteId é obrigatório") Long pacienteId,
        @NotNull(message = "tipo é obrigatório") TipoPagamento tipo,
        @NotNull(message = "valor é obrigatório") @DecimalMin(value = "0.01", message = "valor deve ser maior que zero") BigDecimal valor,
        @NotNull(message = "frequenciaSemanal é obrigatória") FrequenciaSemanal frequenciaSemanal,
        @NotEmpty(message = "diasSemana não pode ser vazio") List<DayOfWeek> diasSemana,
        LocalDate dataInicio) {}
