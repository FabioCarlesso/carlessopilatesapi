package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PlanoRequestDTO(
        @NotNull(message = "pacienteId é obrigatório") Long pacienteId,
        @NotNull(message = "tipo é obrigatório") TipoPagamento tipo,
        @NotNull(message = "valor é obrigatório") @DecimalMin(value = "0.01", message = "valor deve ser maior que zero") BigDecimal valor,
        @NotNull(message = "frequenciaSemanal é obrigatória") FrequenciaSemanal frequenciaSemanal,
        @NotEmpty(message = "diasSemana não pode ser vazio") List<DayOfWeek> diasSemana,
        LocalDate dataInicio,
        @Schema(
                        description =
                                "Horário das aulas em todos os dias do plano, copiado para cada aula gerada. Opcional: sem ele, as aulas nascem sem horário",
                        example = "08:00:00")
                LocalTime horario) {}
