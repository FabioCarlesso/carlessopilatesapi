package com.carlesso.pilatesapi.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Regra compartilhada das listagens por período (agenda de aulas e sessões, bloqueios de agenda).
 *
 * <p>Esses endpoints não são paginados: o recorte é o próprio período. Por
 * isso o intervalo precisa ser fechado, coerente e curto — sem o teto de dias
 * um {@code inicio}/{@code fim} largo devolveria a base inteira em uma única
 * resposta. Ver issue #168.
 *
 * <p>O teto não é único porque o volume por dia não é: a agenda rende dezenas
 * de linhas por dia, o cadastro de bloqueios rende algumas por ano. Daí a
 * sobrecarga que recebe o limite explícito.
 */
public final class PeriodoGuard {

    /** Um trimestre: cobre o maior recorte que o calendário do estúdio exibe de uma vez. */
    public static final long LIMITE_DIAS_AGENDA = 92;

    /**
     * Um ano (366 para não recusar o bissexto), mesmo teto do relatório de pagamento do profissional.
     *
     * <p>Feriados são cadastrados e conferidos de uma vez para o ano inteiro; com o teto da agenda a tela do frontend
     * precisaria de quatro requisições para montar essa visão. O risco que o teto curto evita não existe aqui — são
     * poucos registros por ano, não milhares por trimestre.
     */
    public static final long LIMITE_DIAS_BLOQUEIOS = 366;

    /** Mesmo teto do relatório de pagamento do profissional, pela mesma razão: resposta única, sem paginação. */
    public static final int LIMITE_REGISTROS_AGENDA = 5_000;

    private PeriodoGuard() {}

    /**
     * Recusa períodos ausentes, invertidos ou acima do limite da agenda ({@value #LIMITE_DIAS_AGENDA} dias).
     *
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler
     */
    public static void exigirPeriodoValido(LocalDate inicio, LocalDate fim) {
        exigirPeriodoValido(inicio, fim, LIMITE_DIAS_AGENDA);
    }

    /**
     * Mesma regra, com teto de amplitude explícito para quem não tem o volume diário da agenda.
     *
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler
     */
    public static void exigirPeriodoValido(LocalDate inicio, LocalDate fim, long limiteDias) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Período inicial e final são obrigatórios");
        }
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Período inicial não pode ser maior que o período final");
        }
        long diasPeriodo = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (diasPeriodo > limiteDias) {
            throw new IllegalArgumentException("Consulta por período limitada a " + limiteDias + " dias");
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
