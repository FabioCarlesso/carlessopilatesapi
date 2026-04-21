package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Aula;

import java.time.LocalDate;

public record AulaResponseDTO(
        Long id,
        Long pacienteId,
        String pacienteNome,
        Long pagamentoId,
        LocalDate data,
        boolean realizada
) {
    public static AulaResponseDTO from(Aula aula) {
        return new AulaResponseDTO(
                aula.getId(),
                aula.getPaciente().getId(),
                aula.getPaciente().getNome(),
                aula.getPagamento().getId(),
                aula.getData(),
                aula.isRealizada()
        );
    }
}
