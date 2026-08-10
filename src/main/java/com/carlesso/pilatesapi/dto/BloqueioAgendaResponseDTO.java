package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.BloqueioAgenda;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record BloqueioAgendaResponseDTO(
        Long id,
        LocalDate dataInicio,
        LocalDate dataFim,
        LocalTime horaInicio,
        LocalTime horaFim,
        boolean diaInteiro,
        String motivo,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao) {

    public static BloqueioAgendaResponseDTO from(BloqueioAgenda bloqueio) {
        return new BloqueioAgendaResponseDTO(
                bloqueio.getId(),
                bloqueio.getDataInicio(),
                bloqueio.getDataFim(),
                bloqueio.getHoraInicio(),
                bloqueio.getHoraFim(),
                bloqueio.isDiaInteiro(),
                bloqueio.getMotivo(),
                bloqueio.getDataCriacao(),
                bloqueio.getDataAtualizacao());
    }
}
