package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.ListaEspera;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ListaEsperaResponseDTO(
        Long id,
        Long pacienteId,
        String pacienteNome,
        DayOfWeek diaSemana,
        LocalTime horaInicio,
        LocalTime horaFim,
        LocalDateTime dataEntrada,
        String observacao) {

    public static ListaEsperaResponseDTO from(ListaEspera entrada) {
        return new ListaEsperaResponseDTO(
                entrada.getId(),
                entrada.getPaciente().getId(),
                entrada.getPaciente().getNome(),
                entrada.getDiaSemana(),
                entrada.getHoraInicio(),
                entrada.getHoraFim(),
                entrada.getDataEntrada(),
                entrada.getObservacao());
    }
}
