package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

/**
 * Evolução registrada junto com o atendimento da aula (#246): os campos de {@link EvolucaoSessaoRequestDTO}, menos a
 * sessão, que é a do atendimento.
 */
public record AtendimentoEvolucaoDTO(
        @Schema(description = "Momento do registro; ausente, vale o horário atual do estúdio (`app.fuso-horario`)")
                LocalDateTime dataHoraRegistro,
        String exerciciosRealizados,
        String equipamentosUtilizados,
        String cargasMolas,
        @Min(0) @Max(10) Integer dorAntes,
        @Min(0) @Max(10) Integer dorDepois,
        String respostaPaciente,
        String intercorrencias,
        String orientacoes,
        String observacoesFisioterapeuta) {}
