package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Métricas derivadas dos landmarks — sempre calculadas pelo sistema, nunca
 * aceitas em requisições. Métricas cujos pontos ainda não foram marcados vêm
 * nulas.
 */
@Schema(description = "Métricas calculadas a partir dos landmarks (somente leitura)")
public record MetricasPosturaisDTO(
        @Schema(description = "Inclinação da cabeça, em graus (linha entre os olhos)", example = "3.8")
                BigDecimal inclinacaoCabecaGraus,
        @Schema(description = "Desnível de ombros, em graus (linha entre os acrômios)", example = "2.3")
                BigDecimal desnivelOmbrosGraus,
        @Schema(description = "Desnível de quadril, em graus (linha entre as EIAS)") BigDecimal desnivelQuadrilGraus,
        @Schema(description = "Desnível de joelhos, em graus") BigDecimal desnivelJoelhosGraus,
        @Schema(description = "Desvio em relação à linha de prumo, em unidades normalizadas", example = "0.012")
                BigDecimal desvioPrumoNormalizado,
        @Schema(description = "Desvio de prumo em centímetros; só é preenchido quando há calibração")
                BigDecimal desvioPrumoCm) {

    public static final MetricasPosturaisDTO VAZIA = new MetricasPosturaisDTO(null, null, null, null, null, null);
}
