package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/** Corpo de {@code PATCH /aulas/{id}/remarcar}. */
public record AulaRemarcacaoRequestDTO(
        @NotNull(message = "data é obrigatória") @Schema(
                        description = "Nova data da aula",
                        example = "2025-02-20",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                LocalDate data,
        @Schema(description = "Novo horário da aula. Ausente ou nulo mantém o horário atual", example = "08:00:00")
                LocalTime horario) {}
