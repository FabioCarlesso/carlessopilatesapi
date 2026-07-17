package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Reavaliacao;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReavaliacaoResponseDTO(
        Long id,
        Long pacienteId,
        String nomePaciente,
        Long avaliacaoFisioterapeuticaId,
        Long planoTratamentoId,
        LocalDate dataReavaliacao,
        String comparativoAvaliacaoAnterior,
        String evolucaoDor,
        String evolucaoForca,
        String evolucaoMobilidade,
        String evolucaoFuncional,
        String objetivosAlcancados,
        String pontosAtencao,
        String ajustesRecomendados,
        String observacoesGerais,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao) {
    public static ReavaliacaoResponseDTO from(Reavaliacao r) {
        return new ReavaliacaoResponseDTO(
                r.getId(),
                r.getPaciente().getId(),
                r.getPaciente().getNome(),
                r.getAvaliacaoFisioterapeutica() != null
                        ? r.getAvaliacaoFisioterapeutica().getId()
                        : null,
                r.getPlanoTratamento() != null ? r.getPlanoTratamento().getId() : null,
                r.getDataReavaliacao(),
                r.getComparativoAvaliacaoAnterior(),
                r.getEvolucaoDor(),
                r.getEvolucaoForca(),
                r.getEvolucaoMobilidade(),
                r.getEvolucaoFuncional(),
                r.getObjetivosAlcancados(),
                r.getPontosAtencao(),
                r.getAjustesRecomendados(),
                r.getObservacoesGerais(),
                r.getDataCriacao(),
                r.getDataAtualizacao());
    }
}
