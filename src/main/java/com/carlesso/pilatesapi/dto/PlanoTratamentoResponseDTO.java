package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.PlanoTratamento;
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
        LocalDateTime dataAtualizacao) {
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
                p.getDataAtualizacao());
    }
}
