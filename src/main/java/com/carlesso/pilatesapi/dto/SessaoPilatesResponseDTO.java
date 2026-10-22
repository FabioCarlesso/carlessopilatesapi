package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import io.swagger.v3.oas.annotations.media.Schema;
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
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao,
        @Schema(description = "E-mail de quem criou o registro; \"system\" para processos automáticos")
                String criadoPor,
        @Schema(description = "E-mail de quem fez a última alteração; nulo enquanto não houver alteração")
                String alteradoPor,
        Long version) {
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
                s.getDataCriacao(),
                s.getDataAtualizacao(),
                s.getCriadoPor(),
                s.getAlteradoPor(),
                s.getVersion());
    }
}
