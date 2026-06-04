package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.NotaFiscalEmitida;
import com.carlesso.pilatesapi.util.CompetenciaUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

public record NotaFiscalEmitidaResponseDTO(
        Long id,
        Long pacienteId,
        String nomePaciente,
        String competencia,
        String numeroNota,
        LocalDate dataEmissao,
        BigDecimal valor,
        String observacoes,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao
) {
    public static NotaFiscalEmitidaResponseDTO from(NotaFiscalEmitida n) {
        return new NotaFiscalEmitidaResponseDTO(
                n.getId(),
                n.getPaciente().getId(),
                n.getPaciente().getNome(),
                CompetenciaUtils.format(YearMonth.from(n.getCompetencia())),
                n.getNumeroNota(),
                n.getDataEmissao(),
                n.getValor(),
                n.getObservacoes(),
                n.getDataCriacao(),
                n.getDataAtualizacao()
        );
    }
}
