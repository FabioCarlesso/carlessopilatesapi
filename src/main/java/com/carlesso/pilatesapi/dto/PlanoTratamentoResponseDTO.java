package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.PlanoTratamento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlanoTratamentoResponseDTO(
        Long id,
        Long pacienteId,
        String nomePaciente,
        LocalDate dataInicio,
        LocalDate dataFimPrevista,
        String objetivosTratamento,
        String intervencoesPlanejadas,
        Integer numeroSessoesPrevistas,
        String frequenciaSessoes,
        String responsavelTratamento,
        String observacoes,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao,
        @Schema(description = "E-mail de quem criou o registro; \"system\" para processos automáticos")
                String criadoPor,
        @Schema(description = "E-mail de quem fez a última alteração; nulo enquanto não houver alteração")
                String alteradoPor,
        Long version) {
    public static PlanoTratamentoResponseDTO from(PlanoTratamento p) {
        return new PlanoTratamentoResponseDTO(
                p.getId(),
                p.getPaciente().getId(),
                p.getPaciente().getNome(),
                p.getDataInicio(),
                p.getDataFimPrevista(),
                p.getObjetivosTratamento(),
                p.getIntervencoesPlanejadas(),
                p.getNumeroSessoesPrevistas(),
                p.getFrequenciaSessoes(),
                p.getResponsavelTratamento(),
                p.getObservacoes(),
                p.getDataCriacao(),
                p.getDataAtualizacao(),
                p.getCriadoPor(),
                p.getAlteradoPor(),
                p.getVersion());
    }
}
