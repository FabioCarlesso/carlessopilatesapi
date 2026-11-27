package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Conjunto completo de pontos de dor de um registro. O PUT substitui o conjunto: lista vazia apaga todos os pontos.
 */
@Schema(description = "Conjunto completo de pontos de dor do registro (substitui o atual; lista vazia apaga todos)")
public record PontosDorRequestDTO(
        @Schema(description = "Pontos do registro, no máximo " + PontosDorRequestDTO.MAXIMO_PONTOS)
                @NotNull @Size(max = PontosDorRequestDTO.MAXIMO_PONTOS) List<@NotNull @Valid PontoDorRequestDTO> pontos) {

    /** Limite de pontos por registro: folga para qualquer marcação clínica e teto contra payload abusivo. */
    public static final int MAXIMO_PONTOS = 50;
}
