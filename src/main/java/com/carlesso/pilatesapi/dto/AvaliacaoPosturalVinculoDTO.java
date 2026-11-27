package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Novo vínculo de uma análise postural. Substitui o vínculo atual por inteiro: os dois nulos deixam a análise solta, e
 * os dois preenchidos são recusados ({@code 400}) — a análise pertence a no máximo um registro clínico (#247).
 */
@Schema(
        description =
                "Vínculo da análise postural: avaliação fisioterapêutica OU reavaliação do mesmo paciente; os dois nulos deixam a análise solta")
public record AvaliacaoPosturalVinculoDTO(
        @Schema(description = "ID da avaliação fisioterapêutica; nulo para não vincular a uma avaliação", example = "1")
                Long avaliacaoFisioterapeuticaId,
        @Schema(description = "ID da reavaliação; nulo para não vincular a uma reavaliação", example = "3")
                Long reavaliacaoId) {}
