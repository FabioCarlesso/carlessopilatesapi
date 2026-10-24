package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DashboardResumoDTO(
        PacientesResumo pacientes,
        ProfissionaisResumo profissionais,
        PagamentosResumo pagamentos,
        AulasResumo aulas,
        LocalDateTime geradoEm) {

    public record PacientesResumo(long totalAtivos, long totalInativos) {}

    public record ProfissionaisResumo(long totalAtivos, long totalInativos) {}

    public record PagamentosResumo(
            long totalPendentes,
            long totalPagos,
            long totalVencidos,
            @Schema(description = "Receita confirmada no mês; null para papéis sem acesso financeiro", nullable = true)
                    BigDecimal receitaMesAtual) {}

    public record AulasResumo(long totalRealizadasMesAtual, long totalAgendadasMesAtual) {}
}
