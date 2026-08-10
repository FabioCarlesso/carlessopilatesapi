package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Corpo de {@code PATCH /aulas/{id}/remarcar}.
 *
 * <p>{@code horario} já ocupa o contrato mas é recusado com {@code 400}: {@code Aula} só ganha o campo na #106.
 * Aceitá-lo e descartá-lo em silêncio faria a recepção acreditar que o horário foi gravado — pior que a recusa,
 * que ao menos diz ao frontend que a rota ainda não sabe remarcar hora.
 */
public record AulaRemarcacaoRequestDTO(
        @NotNull(message = "data é obrigatória") @Schema(
                        description = "Nova data da aula",
                        example = "2025-02-20",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                LocalDate data,
        @Null(message = "horario ainda não é suportado: a aula não possui esse campo")
                // Sem type/format explícitos o springdoc expande LocalTime no objeto {hour, minute, second, nano}, e
                // um cliente gerado a partir do schema mandaria esse objeto em vez de "08:00" quando a #106 ativar o
                // campo. O resto do projeto ainda sofre disso (SessaoPilatesRequestDTO/UpdateDTO) e merece correção
                // própria — aqui o contrato já nasce certo.
                @Schema(
                        type = "string",
                        format = "time",
                        description = "Reservado para a #106; enviar qualquer valor retorna 400")
                LocalTime horario) {}
