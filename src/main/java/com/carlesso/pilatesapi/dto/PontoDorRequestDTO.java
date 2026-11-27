package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.VistaCorporal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Ponto de dor marcado sobre o mapa corporal")
public record PontoDorRequestDTO(
        @Schema(description = "Vista do desenho", example = "FRENTE") @NotNull VistaCorporal vista,
        @Schema(
                        description = "Coordenada horizontal normalizada (0 a 1) relativa ao viewBox do desenho",
                        example = "0.42")
                @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double x,
        @Schema(
                        description = "Coordenada vertical normalizada (0 a 1) relativa ao viewBox do desenho",
                        example = "0.31")
                @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double y,
        @Schema(description = "Versão do desenho em que o ponto foi marcado", example = "1") @NotNull @Min(1) Integer desenhoVersao,
        @Schema(description = "Intensidade da dor, de 0 a 10", example = "7", nullable = true) @Min(0) @Max(10) Integer intensidade,
        @Schema(description = "Observação curta sobre o ponto", example = "Dor ao flexionar", nullable = true)
                @Size(max = 200) String observacao) {}
