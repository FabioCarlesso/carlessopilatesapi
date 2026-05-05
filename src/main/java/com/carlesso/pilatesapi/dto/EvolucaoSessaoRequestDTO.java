package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record EvolucaoSessaoRequestDTO(
        @NotNull Long sessaoId,
        @NotNull LocalDateTime dataHoraRegistro,
        String exerciciosRealizados,
        String equipamentosUtilizados,
        String cargasMolas,
        @Min(0) @Max(10) Integer dorAntes,
        @Min(0) @Max(10) Integer dorDepois,
        String respostaPaciente,
        String intercorrencias,
        String orientacoes,
        String observacoesFisioterapeuta
) {}
