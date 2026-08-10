package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Período bloqueado do estúdio. {@code dataFim} é inclusiva — um feriado de um dia só repete a mesma data nas duas
 * pontas. {@code horaInicio}/{@code horaFim} são opcionais, mas só valem juntas: as duas ausentes significam dia
 * inteiro, e mandar apenas uma é recusado com 400.
 */
public record BloqueioAgendaRequestDTO(
        @NotNull(message = "dataInicio é obrigatória") LocalDate dataInicio,
        @NotNull(message = "dataFim é obrigatória") LocalDate dataFim,
        LocalTime horaInicio,
        LocalTime horaFim,
        @NotBlank(message = "motivo é obrigatório") @Size(max = 200, message = "motivo deve ter no máximo 200 caracteres") String motivo) {}
