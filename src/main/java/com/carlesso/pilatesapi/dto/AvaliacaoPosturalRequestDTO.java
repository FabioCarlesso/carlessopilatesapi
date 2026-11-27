package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Criação de análise postural. O paciente é obrigatório; a avaliação fisioterapêutica e a reavaliação são opcionais e
 * mutuamente exclusivas — sem nenhuma das duas, a análise nasce solta (#247).
 */
@Schema(
        description =
                "Criação de análise postural (nasce em RASCUNHO). Solta, ou vinculada a uma avaliação fisioterapêutica ou a uma reavaliação do mesmo paciente — no máximo um dos dois vínculos")
public record AvaliacaoPosturalRequestDTO(
        @Schema(
                        description =
                                "ID do paciente dono da análise. Obrigatório na análise solta; com vínculo, ausente vale o paciente da avaliação/reavaliação (compatibilidade com o FE anterior à #247)",
                        example = "1")
                Long pacienteId,
        @Schema(description = "ID da avaliação fisioterapêutica do mesmo paciente (opcional)", example = "1")
                Long avaliacaoFisioterapeuticaId,
        @Schema(description = "ID da reavaliação do mesmo paciente (opcional)", example = "3") Long reavaliacaoId,
        @Schema(description = "Vista da foto analisada", example = "FRENTE") @NotNull VistaPostural vista) {}
