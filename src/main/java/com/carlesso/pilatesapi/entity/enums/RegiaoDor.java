package com.carlesso.pilatesapi.entity.enums;

/**
 * Regiões de dor/queixa: lados direito e esquerdo são opções separadas para comparar a dor de cada lado entre avaliação e reavaliação.
 *
 * <p>Opção pré-definida da #248 — veja {@link OpcaoAvaliacao} para a regra de evolução (nunca apagar uma constante;
 * aposentar com {@code ativa = false}).
 */
public enum RegiaoDor implements OpcaoAvaliacao {
    CERVICAL("Cervical"),
    TORACICA("Torácica"),
    LOMBAR("Lombar"),
    SACROILIACA("Sacroilíaca"),
    OMBRO_DIREITO("Ombro direito"),
    OMBRO_ESQUERDO("Ombro esquerdo"),
    COTOVELO_DIREITO("Cotovelo direito"),
    COTOVELO_ESQUERDO("Cotovelo esquerdo"),
    PUNHO_MAO_DIREITO("Punho/mão direito"),
    PUNHO_MAO_ESQUERDO("Punho/mão esquerdo"),
    QUADRIL_DIREITO("Quadril direito"),
    QUADRIL_ESQUERDO("Quadril esquerdo"),
    JOELHO_DIREITO("Joelho direito"),
    JOELHO_ESQUERDO("Joelho esquerdo"),
    TORNOZELO_PE_DIREITO("Tornozelo/pé direito"),
    TORNOZELO_PE_ESQUERDO("Tornozelo/pé esquerdo"),
    CEFALEIA("Cefaleia");

    private final String rotulo;
    private final boolean ativa;

    RegiaoDor(String rotulo) {
        this(rotulo, true);
    }

    RegiaoDor(String rotulo, boolean ativa) {
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
