package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Fechamento financeiro consolidado do estúdio no período (#108): receita × repasses. */
public record RelatorioFinanceiroDTO(
        PeriodoDTO periodo,
        Receita receita,
        Repasses repasses,
        Inadimplencia inadimplencia,
        @Schema(description = "Receita menos o total de repasses. A inadimplência é informativa e não entra na conta")
                BigDecimal resultadoLiquido,
        LocalDateTime geradoEm) {

    @Schema(description = "Pagamentos PAGO com dataPagamento no período (regime de caixa)")
    public record Receita(long quantidadePagamentos, BigDecimal total) {}

    @Schema(description = "Soma dos relatórios de pagamento por profissional no mesmo período")
    public record Repasses(long totalAulas, BigDecimal total, List<RepasseProfissional> profissionais) {}

    public record RepasseProfissional(
            Long profissionalId,
            String nome,
            BigDecimal percentualPagamentoAula,
            long totalAulas,
            long quantidadePagamentos,
            BigDecimal totalPagamentosBruto,
            BigDecimal totalProfissional) {}

    @Schema(description = "Pagamentos em aberto (PENDENTE ou VENCIDO) com dataVencimento no período e já vencida")
    public record Inadimplencia(long quantidadePagamentos, BigDecimal total) {}
}
