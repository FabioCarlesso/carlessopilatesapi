package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record EvolucaoSessaoResponseDTO(
        Long id,
        Long sessaoId,
        Long profissionalId,
        String profissionalNome,
        String profissionalNumeroRegistro,
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
        LocalDateTime dataAtualizacao,
        @Schema(description = "E-mail de quem criou o registro; \"system\" para processos automáticos")
                String criadoPor,
        @Schema(description = "E-mail de quem fez a última alteração; nulo enquanto não houver alteração")
                String alteradoPor,
        Long version) {
    public static EvolucaoSessaoResponseDTO from(EvolucaoSessao e) {
        return new EvolucaoSessaoResponseDTO(
                e.getId(),
                e.getSessao().getId(),
                e.getProfissionalId(),
                e.getProfissionalNome(),
                e.getProfissionalNumeroRegistro(),
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
                e.getDataAtualizacao(),
                e.getCriadoPor(),
                e.getAlteradoPor(),
                e.getVersion());
    }
}
