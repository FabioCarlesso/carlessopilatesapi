package com.carlesso.pilatesapi.entity.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CampoOpcoesAvaliacaoTest {

    private static List<String> rotulos(CampoOpcoesAvaliacao campo) {
        return campo.getOpcoes().stream().map(OpcaoAvaliacao::getRotulo).toList();
    }

    @Test
    void rotulosSeguemATabelaAprovadaNaIssue() {
        assertThat(rotulos(CampoOpcoesAvaliacao.REGIOES_DOR))
                .containsExactly(
                        "Cervical",
                        "Torácica",
                        "Lombar",
                        "Sacroilíaca",
                        "Ombro direito",
                        "Ombro esquerdo",
                        "Cotovelo direito",
                        "Cotovelo esquerdo",
                        "Punho/mão direito",
                        "Punho/mão esquerdo",
                        "Quadril direito",
                        "Quadril esquerdo",
                        "Joelho direito",
                        "Joelho esquerdo",
                        "Tornozelo/pé direito",
                        "Tornozelo/pé esquerdo",
                        "Cefaleia");
        assertThat(rotulos(CampoOpcoesAvaliacao.CONDICOES_SAUDE)).hasSize(16).startsWith("Hérnia de disco");
        assertThat(rotulos(CampoOpcoesAvaliacao.NIVEL_ATIVIDADE_FISICA))
                .containsExactly("Sedentário", "Irregularmente ativo", "Ativo", "Muito ativo");
        assertThat(rotulos(CampoOpcoesAvaliacao.OBJETIVOS)).hasSize(9).contains("Gestação/pós-parto");
        assertThat(rotulos(CampoOpcoesAvaliacao.ACHADOS_POSTURAIS)).hasSize(16).endsWith("Pé plano", "Pé cavo");
        assertThat(rotulos(CampoOpcoesAvaliacao.PADRAO_RESPIRATORIO))
                .containsExactly("Diafragmático", "Torácico/apical", "Misto", "Paradoxal");
        assertThat(rotulos(CampoOpcoesAvaliacao.EQUILIBRIO))
                .containsExactly("Preservado", "Alteração leve", "Alteração moderada", "Alteração grave")
                .isEqualTo(rotulos(CampoOpcoesAvaliacao.COORDENACAO_MOTORA));
        assertThat(rotulos(CampoOpcoesAvaliacao.TESTES_FUNCIONAIS))
                .containsExactly(
                        "Schober",
                        "Adams",
                        "Thomas",
                        "Lasègue",
                        "Sentar e alcançar",
                        "Trendelenburg",
                        "Apoio unipodal");
    }

    @Test
    void valoresSaoMaiusculasComUnderscoreERotulosPreenchidos() {
        Arrays.stream(CampoOpcoesAvaliacao.values())
                .flatMap(c -> c.getOpcoes().stream())
                .forEach(o -> {
                    assertThat(o.name()).matches("[A-Z][A-Z_]*");
                    assertThat(o.getRotulo()).isNotBlank();
                    // Cabe na coluna VARCHAR(40) das seleções únicas (V52).
                    assertThat(o.name()).hasSizeLessThanOrEqualTo(40);
                });
    }

    @Test
    void complementoDeCadaCampoSegueONomeBase() {
        assertThat(Arrays.stream(CampoOpcoesAvaliacao.values()).map(CampoOpcoesAvaliacao::getCampoComplemento))
                .containsExactly(
                        "regioesDorComplemento",
                        "condicoesSaudeComplemento",
                        "nivelAtividadeFisicaComplemento",
                        "objetivosComplemento",
                        "achadosPosturaisComplemento",
                        "padraoRespiratorioComplemento",
                        "equilibrioComplemento",
                        "coordenacaoMotoraComplemento",
                        "testesFuncionaisComplemento");
    }

    @Test
    void soTestesFuncionaisTemResultado() {
        assertThat(Arrays.stream(CampoOpcoesAvaliacao.values()).filter(CampoOpcoesAvaliacao::isComResultado))
                .containsExactly(CampoOpcoesAvaliacao.TESTES_FUNCIONAIS);
    }

    @Test
    void exigirOpcao_aceitaSoValoresDoProprioCampo() {
        assertThat(CampoOpcoesAvaliacao.REGIOES_DOR.exigirOpcao("OMBRO_ESQUERDO"))
                .isEqualTo("OMBRO_ESQUERDO");
        assertThatThrownBy(() -> CampoOpcoesAvaliacao.REGIOES_DOR.exigirOpcao("HERNIA_DE_DISCO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Opção inválida em regioesDor: 'HERNIA_DE_DISCO'");
    }

    @Test
    void exigirResultado_aceitaPositivoENegativo() {
        assertThat(CampoOpcoesAvaliacao.exigirResultado("SCHOBER", "NEGATIVO")).isEqualTo("NEGATIVO");
        assertThatThrownBy(() -> CampoOpcoesAvaliacao.exigirResultado("SCHOBER", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
