package com.carlesso.pilatesapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfissionalPagamentoAulaDTO(
        Long aulaId,
        LocalDate data,
        Long pacienteId,
        String pacienteNome,
        Long pagamentoId,
        BigDecimal valorPagamento,
        long quantidadeAulasPagamento,
        BigDecimal valorBaseAula,
        BigDecimal percentualPagamentoAula,
        BigDecimal valorProfissional
) {
}
