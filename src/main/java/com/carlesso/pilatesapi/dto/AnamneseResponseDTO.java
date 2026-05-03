package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Anamnese;
import java.time.LocalDateTime;

public record AnamneseResponseDTO(
        Long id,
        Long pacienteId,
        String nomePaciente,
        String queixaPrincipal,
        String historicoDoencas,
        String historicoCirurgias,
        String historicoLesoes,
        String medicamentosUso,
        String alergias,
        String nivelAtividadeFisica,
        String restricoesMedicas,
        String objetivos,
        String observacoes,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao
) {
    public static AnamneseResponseDTO from(Anamnese a) {
        return new AnamneseResponseDTO(
                a.getId(),
                a.getPaciente().getId(),
                a.getPaciente().getNome(),
                a.getQueixaPrincipal(),
                a.getHistoricoDoencas(),
                a.getHistoricoCirurgias(),
                a.getHistoricoLesoes(),
                a.getMedicamentosUso(),
                a.getAlergias(),
                a.getNivelAtividadeFisica(),
                a.getRestricoesMedicas(),
                a.getObjetivos(),
                a.getObservacoes(),
                a.getDataCriacao(),
                a.getDataAtualizacao()
        );
    }
}
