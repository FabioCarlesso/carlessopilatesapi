package com.carlesso.pilatesapi.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProfissionalPagamentoRelatorioDTO(
        ProfissionalResumoDTO profissional,
        PeriodoDTO periodo,
        ResumoFinanceiroDTO resumo,
        List<PagamentoResumoDTO> pagamentos,
        List<ProfissionalPagamentoAulaDTO> aulas,
        LocalDateTime geradoEm
) {
}
