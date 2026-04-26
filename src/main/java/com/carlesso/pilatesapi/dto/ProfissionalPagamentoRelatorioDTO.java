package com.carlesso.pilatesapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfissionalPagamentoRelatorioDTO(
        Long profissionalId,
        String profissionalNome,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        long totalAulas,
        BigDecimal totalPagamento,
        List<ProfissionalPagamentoAulaDTO> aulas
) {
}
