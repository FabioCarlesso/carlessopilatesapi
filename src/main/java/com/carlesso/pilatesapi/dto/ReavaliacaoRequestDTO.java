package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReavaliacaoRequestDTO(
        @NotNull Long pacienteId,
        Long avaliacaoFisioterapeuticaId,
        Long planoTratamentoId,
        @NotNull LocalDate dataReavaliacao,
        String comparativoAvaliacaoAnterior,
        String evolucaoDor,
        String evolucaoForca,
        String evolucaoMobilidade,
        String evolucaoFuncional,
        String objetivosAlcancados,
        String pontosAtencao,
        String ajustesRecomendados,
        String observacoesGerais) {}
