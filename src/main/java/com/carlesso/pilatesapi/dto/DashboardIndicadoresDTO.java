package com.carlesso.pilatesapi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Séries mensais do dashboard (issue #202). Cada lista traz todos os meses da janela, em ordem crescente e com zero nos
 * meses sem movimento, para o frontend plotar sem preencher lacunas.
 */
public record DashboardIndicadoresDTO(
        int meses,
        List<ReceitaMensal> receita,
        List<AulasMensal> aulas,
        List<PacientesMensal> pacientes,
        LocalDateTime geradoEm) {

    public record ReceitaMensal(YearMonth mes, BigDecimal receita) {}

    public record AulasMensal(YearMonth mes, long realizadas, long agendadas) {}

    public record PacientesMensal(YearMonth mes, long novos, long inativados) {}
}
