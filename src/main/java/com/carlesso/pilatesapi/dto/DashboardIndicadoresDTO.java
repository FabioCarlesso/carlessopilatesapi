package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    /**
     * O Jackson serializa {@link YearMonth} como {@code "AAAA-MM"}, mas o springdoc o descreveria como objeto
     * ({@code year}, {@code month}...): o {@code @Schema} alinha o contrato publicado ao JSON real.
     */
    private static final String MES_PATTERN = "^\\d{4}-\\d{2}$";

    public record ReceitaMensal(
            @Schema(type = "string", pattern = MES_PATTERN, example = "2026-03") YearMonth mes, BigDecimal receita) {}

    public record AulasMensal(
            @Schema(type = "string", pattern = MES_PATTERN, example = "2026-03") YearMonth mes,
            long realizadas,
            long agendadas) {}

    public record PacientesMensal(
            @Schema(type = "string", pattern = MES_PATTERN, example = "2026-03") YearMonth mes,
            long novos,
            long inativados) {}
}
