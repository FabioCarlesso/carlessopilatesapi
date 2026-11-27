package com.carlesso.pilatesapi.entity.enums;

/**
 * Nível de atividade física declarado na anamnese (seleção única).
 *
 * <p>Opção pré-definida da #248 — veja {@link OpcaoAvaliacao} para a regra de evolução (nunca apagar uma constante;
 * aposentar com {@code ativa = false}).
 */
public enum NivelAtividadeFisica implements OpcaoAvaliacao {
    SEDENTARIO("Sedentário"),
    IRREGULARMENTE_ATIVO("Irregularmente ativo"),
    ATIVO("Ativo"),
    MUITO_ATIVO("Muito ativo");

    private final String rotulo;
    private final boolean ativa;

    NivelAtividadeFisica(String rotulo) {
        this(rotulo, true);
    }

    NivelAtividadeFisica(String rotulo, boolean ativa) {
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
