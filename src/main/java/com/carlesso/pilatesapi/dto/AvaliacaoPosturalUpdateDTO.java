package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/**
 * Atualização da marcação de uma análise em rascunho. Apenas os campos enviados
 * são alterados; as métricas nunca são aceitas aqui — são recalculadas a cada
 * salvamento.
 */
@Schema(description = "Atualização de landmarks, linha de prumo, calibração e observações (apenas em RASCUNHO)")
public record AvaliacaoPosturalUpdateDTO(
        @Schema(description = "Posição horizontal da linha de prumo, normalizada", example = "0.502")
                @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal linhaPrumoX,
        @Schema(description = "Centímetros por unidade normalizada; sem ela o desvio de prumo sai apenas normalizado")
                @Positive BigDecimal calibracaoCmPorUnidade,
        @Schema(
                        description = "Razão largura/altura da foto; sem ela os ângulos assumem imagem quadrada",
                        example = "0.75")
                @Positive BigDecimal proporcaoImagem,
        @Schema(description = "Observações clínicas") String observacoes,
        @Schema(description = "Pontos marcados; substituem integralmente os anteriores quando enviados") @Valid List<LandmarkDTO> landmarks) {}
