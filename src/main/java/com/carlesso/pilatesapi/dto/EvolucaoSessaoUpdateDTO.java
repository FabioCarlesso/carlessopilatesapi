package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

public record EvolucaoSessaoUpdateDTO(
        LocalDateTime dataHoraRegistro,
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
