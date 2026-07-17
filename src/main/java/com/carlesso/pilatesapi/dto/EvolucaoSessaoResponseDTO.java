package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import java.time.LocalDateTime;

public record EvolucaoSessaoResponseDTO(
        Long id,
        Long sessaoId,
        LocalDateTime dataHoraRegistro,
        String exerciciosRealizados,
        String equipamentosUtilizados,
        String cargasMolas,
        Integer dorAntes,
        Integer dorDepois,
        String respostaPaciente,
        String intercorrencias,
        String orientacoes,
        String observacoesFisioterapeuta,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao) {
    public static EvolucaoSessaoResponseDTO from(EvolucaoSessao e) {
        return new EvolucaoSessaoResponseDTO(
                e.getId(),
                e.getSessao().getId(),
                e.getDataHoraRegistro(),
                e.getExerciciosRealizados(),
                e.getEquipamentosUtilizados(),
                e.getCargasMolas(),
                e.getDorAntes(),
                e.getDorDepois(),
                e.getRespostaPaciente(),
                e.getIntercorrencias(),
                e.getOrientacoes(),
                e.getObservacoesFisioterapeuta(),
                e.getDataCriacao(),
                e.getDataAtualizacao());
    }
}
