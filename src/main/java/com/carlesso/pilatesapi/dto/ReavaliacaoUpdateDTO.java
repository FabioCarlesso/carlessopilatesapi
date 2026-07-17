package com.carlesso.pilatesapi.dto;

import java.time.LocalDate;

public record ReavaliacaoUpdateDTO(
        LocalDate dataReavaliacao,
        String comparativoAvaliacaoAnterior,
        String evolucaoDor,
        String evolucaoForca,
        String evolucaoMobilidade,
        String evolucaoFuncional,
        String objetivosAlcancados,
        String pontosAtencao,
        String ajustesRecomendados,
        String observacoesGerais) {}
