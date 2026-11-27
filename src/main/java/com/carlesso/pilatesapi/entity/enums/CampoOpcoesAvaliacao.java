package com.carlesso.pilatesapi.entity.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Os campos estruturados da anamnese, da avaliação fisioterapêutica e da reavaliação, cada um com o enum de opções que
 * aceita (#248).
 *
 * <p>É a fonte única das duas pontas: o catálogo {@code GET /avaliacoes/opcoes} é montado a partir daqui e a escrita
 * valida contra os mesmos enums, então o que o FE oferece e o que a API aceita não divergem. {@link #getCampo()} é o
 * nome da propriedade nos DTOs (igual nos formulários que compartilham o campo) e {@link #getCampoComplemento()} o do
 * texto livre que acompanha o campo.
 */
public enum CampoOpcoesAvaliacao {
    REGIOES_DOR(
            "regioesDor", "regioesDorComplemento", "Regiões de dor/queixa", SelecaoOpcao.MULTIPLA, RegiaoDor.values()),
    CONDICOES_SAUDE(
            "condicoesSaude",
            "condicoesSaudeComplemento",
            "Condições de saúde",
            SelecaoOpcao.MULTIPLA,
            CondicaoSaude.values()),
    NIVEL_ATIVIDADE_FISICA(
            "nivelAtividadeFisicaOpcao",
            "nivelAtividadeFisicaComplemento",
            "Nível de atividade física",
            SelecaoOpcao.UNICA,
            NivelAtividadeFisica.values()),
    OBJETIVOS(
            "objetivosOpcoes", "objetivosComplemento", "Objetivos", SelecaoOpcao.MULTIPLA, ObjetivoTratamento.values()),
    ACHADOS_POSTURAIS(
            "achadosPosturais",
            "achadosPosturaisComplemento",
            "Achados posturais",
            SelecaoOpcao.MULTIPLA,
            AchadoPostural.values()),
    PADRAO_RESPIRATORIO(
            "padraoRespiratorioOpcao",
            "padraoRespiratorioComplemento",
            "Padrão respiratório",
            SelecaoOpcao.UNICA,
            PadraoRespiratorio.values()),
    EQUILIBRIO("equilibrioOpcao", "equilibrioComplemento", "Equilíbrio", SelecaoOpcao.UNICA, GrauAlteracao.values()),
    COORDENACAO_MOTORA(
            "coordenacaoMotoraOpcao",
            "coordenacaoMotoraComplemento",
            "Coordenação motora",
            SelecaoOpcao.UNICA,
            GrauAlteracao.values()),
    /** Único campo com resultado: cada item é um par teste/{@link ResultadoTesteFuncional}. */
    TESTES_FUNCIONAIS(
            "testesFuncionais",
            "testesFuncionaisComplemento",
            "Testes funcionais",
            SelecaoOpcao.MULTIPLA,
            TesteFuncional.values());

    private final String campo;
    private final String campoComplemento;
    private final String rotulo;
    private final SelecaoOpcao selecao;
    private final List<OpcaoAvaliacao> opcoes;
    private final Map<String, OpcaoAvaliacao> opcoesPorValor;

    CampoOpcoesAvaliacao(
            String campo, String campoComplemento, String rotulo, SelecaoOpcao selecao, OpcaoAvaliacao[] opcoes) {
        this.campo = campo;
        this.campoComplemento = campoComplemento;
        this.rotulo = rotulo;
        this.selecao = selecao;
        this.opcoes = List.of(opcoes);
        this.opcoesPorValor =
                Arrays.stream(opcoes).collect(Collectors.toMap(OpcaoAvaliacao::name, Function.identity()));
    }

    public String getCampo() {
        return campo;
    }

    public String getCampoComplemento() {
        return campoComplemento;
    }

    public String getRotulo() {
        return rotulo;
    }

    public SelecaoOpcao getSelecao() {
        return selecao;
    }

    public boolean isComResultado() {
        return this == TESTES_FUNCIONAIS;
    }

    /** Todas as opções, na ordem de exibição, inclusive as aposentadas. */
    public List<OpcaoAvaliacao> getOpcoes() {
        return opcoes;
    }

    /**
     * Devolve o valor se ele é uma opção do campo — ativa ou aposentada, para que a edição de um registro antigo que
     * reenvia o valor não falhe.
     *
     * @throws IllegalArgumentException (400) para valor nulo ou fora do enum
     */
    public String exigirOpcao(String valor) {
        return exigir(valor, campo, opcoesPorValor::get).name();
    }

    /**
     * Valida o resultado de um teste funcional: obrigatório e {@code POSITIVO} ou {@code NEGATIVO}.
     *
     * @throws IllegalArgumentException (400) para resultado ausente ou fora do enum
     */
    public static String exigirResultado(String teste, String resultado) {
        if (resultado == null || resultado.isBlank()) {
            throw new IllegalArgumentException(
                    "Informe o resultado (POSITIVO ou NEGATIVO) do teste " + teste + " em testesFuncionais");
        }
        return exigir(resultado, "testesFuncionais (resultado do teste " + teste + ")", RESULTADOS::get)
                .name();
    }

    private static <T extends OpcaoAvaliacao> T exigir(String valor, String campo, Function<String, T> busca) {
        if (valor == null) {
            throw new IllegalArgumentException(campo + " não aceita valor nulo");
        }
        T opcao = busca.apply(valor);
        if (opcao == null) {
            throw new IllegalArgumentException("Opção inválida em " + campo + ": '" + valor + "'");
        }
        return opcao;
    }

    private static final Map<String, ResultadoTesteFuncional> RESULTADOS =
            Arrays.stream(ResultadoTesteFuncional.values()).collect(Collectors.toMap(Enum::name, Function.identity()));
}
