package com.carlesso.pilatesapi.entity.enums;

/**
 * Grau de alteração usado no equilíbrio e na coordenação motora (seleção única em cada um).
 *
 * <p>Opção pré-definida da #248 — veja {@link OpcaoAvaliacao} para a regra de evolução (nunca apagar uma constante;
 * aposentar com {@code ativa = false}).
 */
public enum GrauAlteracao implements OpcaoAvaliacao {
    PRESERVADO("Preservado"),
    ALTERACAO_LEVE("Alteração leve"),
    ALTERACAO_MODERADA("Alteração moderada"),
    ALTERACAO_GRAVE("Alteração grave");

    private final String rotulo;
    private final boolean ativa;

    GrauAlteracao(String rotulo) {
        this(rotulo, true);
    }

    GrauAlteracao(String rotulo, boolean ativa) {
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
