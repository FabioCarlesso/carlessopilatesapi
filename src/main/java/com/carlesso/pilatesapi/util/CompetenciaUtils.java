package com.carlesso.pilatesapi.util;

import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * Utilitários para validação, parsing e formatação de competências fiscais no
 * formato {@code MM/AAAA}, compartilhados entre o relatório de NFSE e o registro
 * de notas emitidas.
 */
public final class CompetenciaUtils {

    /**
     * Expressão regular do formato de competência {@code MM/AAAA}, reutilizada
     * tanto na validação Bean Validation (DTO) quanto no parsing programático.
     */
    public static final String COMPETENCIA_REGEX = "^(0[1-9]|1[0-2])/\\d{4}$";

    private static final Pattern COMPETENCIA_PATTERN = Pattern.compile(COMPETENCIA_REGEX);

    private CompetenciaUtils() {
    }

    public static YearMonth parse(String competencia) {
        if (competencia == null || competencia.isBlank()) {
            throw new IllegalArgumentException("competencia é obrigatória");
        }
        if (!COMPETENCIA_PATTERN.matcher(competencia).matches()) {
            throw new IllegalArgumentException("competencia deve estar no formato MM/AAAA");
        }

        int mes = Integer.parseInt(competencia.substring(0, 2));
        int ano = Integer.parseInt(competencia.substring(3));
        return YearMonth.of(ano, mes);
    }

    public static String format(YearMonth periodo) {
        return String.format("%02d/%04d", periodo.getMonthValue(), periodo.getYear());
    }
}
