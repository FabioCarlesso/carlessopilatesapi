package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.DisponibilidadeProfissional;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record DisponibilidadeProfissionalResponseDTO(
        Long id,
        Long profissionalId,
        DayOfWeek diaSemana,
        LocalTime horaInicio,
        LocalTime horaFim,
        Long version,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao) {

    public static DisponibilidadeProfissionalResponseDTO from(DisponibilidadeProfissional disponibilidade) {
        return new DisponibilidadeProfissionalResponseDTO(
                disponibilidade.getId(),
                disponibilidade.getProfissional().getId(),
                disponibilidade.getDiaSemana(),
                disponibilidade.getHoraInicio(),
                disponibilidade.getHoraFim(),
                disponibilidade.getVersion(),
                disponibilidade.getDataCriacao(),
                disponibilidade.getDataAtualizacao());
    }
}
