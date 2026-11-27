package com.carlesso.pilatesapi.entity.enums;

/**
 * Resultado de um {@link TesteFuncional}.
 *
 * <p>Opção pré-definida da #248 — veja {@link OpcaoAvaliacao} para a regra de evolução (nunca apagar uma constante;
 * aposentar com {@code ativa = false}).
 */
public enum ResultadoTesteFuncional implements OpcaoAvaliacao {
    POSITIVO("Positivo"),
    NEGATIVO("Negativo");

    private final String rotulo;
    private final boolean ativa;

    ResultadoTesteFuncional(String rotulo) {
        this(rotulo, true);
    }

    ResultadoTesteFuncional(String rotulo, boolean ativa) {
        this.rotulo = rotulo;
        this.ativa = ativa;
    }

    @Override
    public String getRotulo() {
        return rotulo;
    }

    @Override
    public boolean isAtiva() {
        return ativa;
    }
}
