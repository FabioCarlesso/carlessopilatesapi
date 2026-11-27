package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.BloqueioAgenda;
import com.carlesso.pilatesapi.entity.enums.TipoBloqueio;
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
        TipoBloqueio tipo,
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
                bloqueio.getTipo(),
                bloqueio.getDataCriacao(),
                bloqueio.getDataAtualizacao());
    }
}
