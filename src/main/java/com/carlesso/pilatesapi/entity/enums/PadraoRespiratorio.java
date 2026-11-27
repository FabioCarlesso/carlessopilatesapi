package com.carlesso.pilatesapi.entity.enums;

/**
 * Padrão respiratório observado (seleção única).
 *
 * <p>Opção pré-definida da #248 — veja {@link OpcaoAvaliacao} para a regra de evolução (nunca apagar uma constante;
 * aposentar com {@code ativa = false}).
 */
public enum PadraoRespiratorio implements OpcaoAvaliacao {
    DIAFRAGMATICO("Diafragmático"),
    TORACICO_APICAL("Torácico/apical"),
    MISTO("Misto"),
    PARADOXAL("Paradoxal");

    private final String rotulo;
    private final boolean ativa;

    PadraoRespiratorio(String rotulo) {
        this(rotulo, true);
    }

    PadraoRespiratorio(String rotulo, boolean ativa) {
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
