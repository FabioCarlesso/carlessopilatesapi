package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public record PlanoResponseDTO(
        Long id,
        Long pacienteId,
        String pacienteNome,
        TipoPagamento tipo,
        BigDecimal valor,
        FrequenciaSemanal frequenciaSemanal,
        List<DayOfWeek> diasSemana,
        LocalDate dataInicio,
        boolean ativo
) {
    public static PlanoResponseDTO from(Plano plano) {
        return new PlanoResponseDTO(
                plano.getId(),
                plano.getPaciente().getId(),
                plano.getPaciente().getNome(),
                plano.getTipo(),
                plano.getValor(),
                plano.getFrequenciaSemanal(),
                plano.getDiasSemana(),
                plano.getDataInicio(),
                plano.isAtivo()
        );
    }
}
