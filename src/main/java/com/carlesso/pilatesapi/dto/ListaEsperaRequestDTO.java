package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Inscrição na lista de espera. Não existe campo {@code dataEntrada}: a ordem de chegada é do servidor, e um cliente
 * capaz de escolhê-la conseguiria furar a fila.
 */
public record ListaEsperaRequestDTO(
        @NotNull(message = "pacienteId é obrigatório") Long pacienteId,
        @NotNull(message = "diaSemana é obrigatório") DayOfWeek diaSemana,
        @NotNull(message = "horaInicio é obrigatória") LocalTime horaInicio,
        @NotNull(message = "horaFim é obrigatória") LocalTime horaFim,
        @Size(max = 500, message = "observacao deve ter no máximo 500 caracteres") String observacao) {}
