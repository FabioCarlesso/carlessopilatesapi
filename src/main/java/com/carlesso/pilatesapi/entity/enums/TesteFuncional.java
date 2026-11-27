package com.carlesso.pilatesapi.entity.enums;

/**
 * Testes funcionais realizados na avaliação/reavaliação; cada um é registrado com o seu {@link ResultadoTesteFuncional}.
 *
 * <p>Opção pré-definida da #248 — veja {@link OpcaoAvaliacao} para a regra de evolução (nunca apagar uma constante;
 * aposentar com {@code ativa = false}).
 */
public enum TesteFuncional implements OpcaoAvaliacao {
    SCHOBER("Schober"),
    ADAMS("Adams"),
    THOMAS("Thomas"),
    LASEGUE("Lasègue"),
    SENTAR_E_ALCANCAR("Sentar e alcançar"),
    TRENDELENBURG("Trendelenburg"),
    APOIO_UNIPODAL("Apoio unipodal");

    private final String rotulo;
    private final boolean ativa;

    TesteFuncional(String rotulo) {
        this(rotulo, true);
    }

    TesteFuncional(String rotulo, boolean ativa) {
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
