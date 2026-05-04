package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record SessaoPilatesResponseDTO(
        Long id,
        Long pacienteId,
        String nomePaciente,
        Long profissionalId,
        String nomeProfissional,
        Long planoTratamentoId,
        TipoSessao tipo,
        StatusSessao status,
        LocalDate data,
        LocalTime horario,
        String local,
        Integer duracaoMinutos,
        String observacoes,
        String evolucao,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao
) {
    public static SessaoPilatesResponseDTO from(SessaoPilates s) {
        return new SessaoPilatesResponseDTO(
                s.getId(),
                s.getPaciente().getId(),
                s.getPaciente().getNome(),
                s.getProfissional() != null ? s.getProfissional().getId() : null,
                s.getProfissional() != null ? s.getProfissional().getNome() : null,
                s.getPlanoTratamento() != null ? s.getPlanoTratamento().getId() : null,
                s.getTipo(),
                s.getStatus(),
                s.getData(),
                s.getHorario(),
                s.getLocal(),
                s.getDuracaoMinutos(),
                s.getObservacoes(),
                s.getEvolucao(),
                s.getDataCriacao(),
                s.getDataAtualizacao()
        );
    }
}
