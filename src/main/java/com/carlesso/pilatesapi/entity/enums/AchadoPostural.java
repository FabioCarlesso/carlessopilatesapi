package com.carlesso.pilatesapi.entity.enums;

/**
 * Achados posturais da avaliação fisioterapêutica e da reavaliação.
 *
 * <p>Opção pré-definida da #248 — veja {@link OpcaoAvaliacao} para a regra de evolução (nunca apagar uma constante;
 * aposentar com {@code ativa = false}).
 */
public enum AchadoPostural implements OpcaoAvaliacao {
    ANTERIORIZACAO_DE_CABECA("Anteriorização de cabeça"),
    RETIFICACAO_CERVICAL("Retificação cervical"),
    HIPERCIFOSE_TORACICA("Hipercifose torácica"),
    HIPERLORDOSE_LOMBAR("Hiperlordose lombar"),
    RETIFICACAO_LOMBAR("Retificação lombar"),
    ESCOLIOSE("Escoliose"),
    OMBROS_PROTUSOS("Ombros protusos"),
    DESNIVEL_DE_OMBROS("Desnível de ombros"),
    DESNIVEL_PELVICO("Desnível pélvico"),
    ANTEVERSAO_PELVICA("Anteversão pélvica"),
    RETROVERSAO_PELVICA("Retroversão pélvica"),
    JOELHO_VALGO("Joelho valgo"),
    JOELHO_VARO("Joelho varo"),
    JOELHO_RECURVADO("Joelho recurvado"),
    PE_PLANO("Pé plano"),
    PE_CAVO("Pé cavo");

    private final String rotulo;
    private final boolean ativa;

    AchadoPostural(String rotulo) {
        this(rotulo, true);
    }

    AchadoPostural(String rotulo, boolean ativa) {
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
