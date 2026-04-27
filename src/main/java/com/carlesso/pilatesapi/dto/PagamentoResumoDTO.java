package com.carlesso.pilatesapi.dto;

import java.math.BigDecimal;

public record PagamentoResumoDTO(
        Long pagamentoId,
        BigDecimal valorPagamento,
        long quantidadeAulasPagamento,
        long quantidadeAulasNoPeriodo,
        BigDecimal valorBaseAula,
        BigDecimal totalProfissional
) {
}
