package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Criação de análise postural (nasce em RASCUNHO, dentro de uma avaliação fisioterapêutica)")
public record AvaliacaoPosturalRequestDTO(
        @Schema(description = "ID da avaliação fisioterapêutica que receberá a análise", example = "1") @NotNull Long avaliacaoFisioterapeuticaId,
        @Schema(description = "Vista da foto analisada", example = "FRENTE") @NotNull VistaPostural vista) {}
