package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import java.math.BigDecimal;

public record ProfissionalResumoDTO(
        Long id, String nome, String cpf, TipoContrato tipoContrato, BigDecimal percentualPagamentoAula) {
    public static ProfissionalResumoDTO from(Profissional p) {
        return new ProfissionalResumoDTO(
                p.getId(), p.getNome(), p.getCpf(), p.getTipoContrato(), p.getPercentualPagamentoAula());
    }
}
