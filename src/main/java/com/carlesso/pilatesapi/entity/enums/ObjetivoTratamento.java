package com.carlesso.pilatesapi.entity.enums;

/**
 * Objetivos do paciente com o tratamento (anamnese).
 *
 * <p>Opção pré-definida da #248 — veja {@link OpcaoAvaliacao} para a regra de evolução (nunca apagar uma constante;
 * aposentar com {@code ativa = false}).
 */
public enum ObjetivoTratamento implements OpcaoAvaliacao {
    ALIVIO_DE_DOR("Alívio de dor"),
    REABILITACAO("Reabilitação"),
    FORTALECIMENTO("Fortalecimento"),
    FLEXIBILIDADE_MOBILIDADE("Flexibilidade/mobilidade"),
    POSTURA("Postura"),
    CONDICIONAMENTO("Condicionamento"),
    GESTACAO_POS_PARTO("Gestação/pós-parto"),
    PREVENCAO("Prevenção"),
    QUALIDADE_DE_VIDA("Qualidade de vida");

    private final String rotulo;
    private final boolean ativa;

    ObjetivoTratamento(String rotulo) {
        this(rotulo, true);
    }

    ObjetivoTratamento(String rotulo, boolean ativa) {
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
