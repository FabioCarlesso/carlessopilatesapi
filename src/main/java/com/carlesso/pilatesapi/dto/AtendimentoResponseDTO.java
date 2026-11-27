package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Atendimento de uma aula (#246): a aula, a sessão vinculada a ela e a evolução da sessão, se houver. */
public record AtendimentoResponseDTO(
        AulaResponseDTO aula,
        SessaoPilatesResponseDTO sessao,
        @Schema(description = "Evolução da sessão; nulo enquanto não houver") EvolucaoSessaoResponseDTO evolucao) {}
