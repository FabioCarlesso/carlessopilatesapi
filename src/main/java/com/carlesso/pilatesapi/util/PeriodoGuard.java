package com.carlesso.pilatesapi.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Regra compartilhada das listagens por período da agenda (aulas e sessões).
 *
 * <p>Endpoints de agenda não são paginados: o recorte é o próprio período. Por
 * isso o intervalo precisa ser fechado, coerente e curto — sem o teto de dias
 * um {@code inicio}/{@code fim} largo devolveria a base inteira em uma única
 * resposta. Ver issue #168.
 */
public final class PeriodoGuard {

    /** Um trimestre: cobre o maior recorte que o calendário do estúdio exibe de uma vez. */
    public static final long LIMITE_DIAS_AGENDA = 92;

    /** Mesmo teto do relatório de pagamento do profissional, pela mesma razão: resposta única, sem paginação. */
    public static final int LIMITE_REGISTROS_AGENDA = 5_000;

    private PeriodoGuard() {}

    /**
     * Recusa períodos ausentes, invertidos ou acima do limite de amplitude.
     *
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler
     */
    public static void exigirPeriodoValido(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Período inicial e final são obrigatórios");
        }
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Período inicial não pode ser maior que o período final");
        }
        long diasPeriodo = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (diasPeriodo > LIMITE_DIAS_AGENDA) {
            throw new IllegalArgumentException("Consulta por período limitada a " + LIMITE_DIAS_AGENDA + " dias");
        }
    }

    /**
     * Recusa respostas grandes demais para um payload único.
     *
     * <p>O teto de dias limita a janela, não o volume: um período curto de um
     * estúdio movimentado ainda pode render milhares de linhas. A checagem é
     * feita depois da consulta — como em
     * {@code ProfissionalService.gerarRelatorioPagamento} — porque um
     * {@code count} antes dobraria a ida ao banco em toda requisição para
     * proteger o caso raro.
     *
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler
     */
    public static void exigirVolumeSuportado(int quantidade) {
        if (quantidade > LIMITE_REGISTROS_AGENDA) {
            throw new IllegalArgumentException("Consulta por período limitada a " + LIMITE_REGISTROS_AGENDA
                    + " registros; reduza o intervalo ou aplique filtros");
        }
    }
}
