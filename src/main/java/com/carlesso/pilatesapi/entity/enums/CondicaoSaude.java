package com.carlesso.pilatesapi.entity.enums;

/**
 * Condições de saúde do histórico do paciente (anamnese).
 *
 * <p>Opção pré-definida da #248 — veja {@link OpcaoAvaliacao} para a regra de evolução (nunca apagar uma constante;
 * aposentar com {@code ativa = false}).
 */
public enum CondicaoSaude implements OpcaoAvaliacao {
    HERNIA_DE_DISCO("Hérnia de disco"),
    PROTRUSAO_DISCAL("Protrusão discal"),
    ESCOLIOSE("Escoliose"),
    ARTROSE("Artrose"),
    OSTEOPENIA_OSTEOPOROSE("Osteopenia/osteoporose"),
    FIBROMIALGIA("Fibromialgia"),
    TENDINOPATIA("Tendinopatia"),
    BURSITE("Bursite"),
    FRATURA_PREVIA("Fratura prévia"),
    CIRURGIA_ORTOPEDICA("Cirurgia ortopédica"),
    HIPERTENSAO("Hipertensão"),
    DIABETES("Diabetes"),
    CARDIOPATIA("Cardiopatia"),
    LABIRINTITE_VERTIGEM("Labirintite/vertigem"),
    GESTACAO("Gestação"),
    POS_PARTO("Pós-parto");

    private final String rotulo;
    private final boolean ativa;

    CondicaoSaude(String rotulo) {
        this(rotulo, true);
    }

    CondicaoSaude(String rotulo, boolean ativa) {
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
