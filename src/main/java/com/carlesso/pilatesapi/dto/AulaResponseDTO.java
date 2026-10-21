package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Profissional;
import java.time.LocalDate;
import java.time.LocalTime;

public record AulaResponseDTO(
        Long id,
        Long pacienteId,
        String pacienteNome,
        Long profissionalId,
        String profissionalNome,
        Long pagamentoId,
        LocalDate data,
        LocalTime horario,
        boolean realizada) {
    public static AulaResponseDTO from(Aula aula) {
        Profissional profissional = aula.getProfissional();
        return new AulaResponseDTO(
                aula.getId(),
                aula.getPaciente().getId(),
                aula.getPaciente().getNome(),
                profissional != null ? profissional.getId() : null,
                profissional != null ? profissional.getNome() : null,
                aula.getPagamento().getId(),
                aula.getData(),
                aula.getHorario(),
                aula.isRealizada());
    }
}
