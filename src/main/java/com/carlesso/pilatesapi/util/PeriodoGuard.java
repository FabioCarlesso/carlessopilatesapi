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
}
