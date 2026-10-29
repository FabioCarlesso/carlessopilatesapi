package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Faixa semanal de atendimento. O mesmo corpo serve ao {@code POST} e ao {@code PUT} (substituição completa);
 * {@code version} só é conferida no {@code PUT} e é ignorada na criação.
 */
public record DisponibilidadeProfissionalRequestDTO(
        @Schema(description = "Dia da semana, em inglês maiúsculo", example = "MONDAY")
                @NotNull(message = "diaSemana é obrigatório") DayOfWeek diaSemana,
        @Schema(type = "string", description = "Início da faixa (`HH:mm`), inclusivo", example = "07:00")
                @NotNull(message = "horaInicio é obrigatória") LocalTime horaInicio,
        @Schema(type = "string", description = "Fim da faixa (`HH:mm`), exclusivo", example = "12:00")
                @NotNull(message = "horaFim é obrigatória") LocalTime horaFim,
        @Schema(
                        description =
                                "Só no PUT: versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem (modo legado)",
                        example = "0")
                Long version) {}
