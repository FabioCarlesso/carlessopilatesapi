package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.CodigoLandmark;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "Ponto anatômico marcado na foto, em coordenadas normalizadas (0 a 1) relativas à imagem")
public record LandmarkDTO(
        @Schema(description = "Código do ponto anatômico; deve pertencer à vista da análise", example = "OMBRO_ESQ")
                @NotNull CodigoLandmark codigo,
        @Schema(description = "Coordenada horizontal normalizada", example = "0.401")
                @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal x,
        @Schema(description = "Coordenada vertical normalizada", example = "0.232")
                @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal y) {}
