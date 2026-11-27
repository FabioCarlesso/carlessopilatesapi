package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaUpdateDTO;
import com.carlesso.pilatesapi.dto.TesteFuncionalRealizadoDTO;
import com.carlesso.pilatesapi.entity.ExameFisicoOpcoes;
import com.carlesso.pilatesapi.entity.TesteFuncionalRealizado;
import com.carlesso.pilatesapi.entity.enums.CampoOpcoesAvaliacao;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpcoesAvaliacaoNormalizadorTest {

    @Test
    void multipla_deduplicaMantendoAOrdemDeChegada() {
        AtomicReference<List<String>> gravado = new AtomicReference<>();

        OpcoesAvaliacaoNormalizador.aplicarMultipla(
                CampoOpcoesAvaliacao.REGIOES_DOR, List.of("LOMBAR", "CERVICAL", "LOMBAR", "CERVICAL"), gravado::set);

        assertThat(gravado.get()).containsExactly("LOMBAR", "CERVICAL");
    }

    @Test
    void multipla_ausenteNaoGrava_eVaziaLimpa() {
        AtomicReference<List<String>> gravado = new AtomicReference<>(List.of("LOMBAR"));

        OpcoesAvaliacaoNormalizador.aplicarMultipla(CampoOpcoesAvaliacao.REGIOES_DOR, null, gravado::set);
        assertThat(gravado.get()).containsExactly("LOMBAR");

        OpcoesAvaliacaoNormalizador.aplicarMultipla(CampoOpcoesAvaliacao.REGIOES_DOR, List.of(), gravado::set);
        assertThat(gravado.get()).isEmpty();
    }

    @Test
    void multipla_comValorForaDoEnum_lancaIllegalArgument() {
        assertThatThrownBy(() -> OpcoesAvaliacaoNormalizador.aplicarMultipla(
                        CampoOpcoesAvaliacao.CONDICOES_SAUDE, List.of("HERNIA_DE_DISCO", "GRIPE"), v -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Opção inválida em condicoesSaude: 'GRIPE'");
    }

    @Test
    void multipla_naoAceitaValorDeOutroCampo() {
        // ESCOLIOSE existe em condicoesSaude e em achadosPosturais; CERVICAL só em regioesDor.
        assertThatThrownBy(() -> OpcoesAvaliacaoNormalizador.aplicarMultipla(
                        CampoOpcoesAvaliacao.ACHADOS_POSTURAIS, List.of("CERVICAL"), v -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("achadosPosturais");
    }

    @Test
    void multipla_comItemNulo_lancaIllegalArgument() {
        assertThatThrownBy(() -> OpcoesAvaliacaoNormalizador.aplicarMultipla(
                        CampoOpcoesAvaliacao.REGIOES_DOR, Arrays.asList("LOMBAR", null), v -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("regioesDor não aceita valor nulo");
    }

    @Test
    void multipla_eUnica_saoSensiveisAMaiusculas() {
        assertThatThrownBy(() -> OpcoesAvaliacaoNormalizador.aplicarMultipla(
                        CampoOpcoesAvaliacao.REGIOES_DOR, List.of("lombar"), v -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unica_validaLimpaComBrancoEIgnoraAusente() {
        AtomicReference<String> gravado = new AtomicReference<>("SEDENTARIO");

        OpcoesAvaliacaoNormalizador.aplicarUnica(CampoOpcoesAvaliacao.NIVEL_ATIVIDADE_FISICA, null, gravado::set);
        assertThat(gravado.get()).isEqualTo("SEDENTARIO");

        OpcoesAvaliacaoNormalizador.aplicarUnica(
                CampoOpcoesAvaliacao.NIVEL_ATIVIDADE_FISICA, "MUITO_ATIVO", gravado::set);
        assertThat(gravado.get()).isEqualTo("MUITO_ATIVO");

        OpcoesAvaliacaoNormalizador.aplicarUnica(CampoOpcoesAvaliacao.NIVEL_ATIVIDADE_FISICA, " ", gravado::set);
        assertThat(gravado.get()).isNull();
    }

    @Test
    void unica_comValorForaDoEnum_lancaIllegalArgument() {
        assertThatThrownBy(() ->
                        OpcoesAvaliacaoNormalizador.aplicarUnica(CampoOpcoesAvaliacao.EQUILIBRIO, "OTIMO", v -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Opção inválida em equilibrioOpcao: 'OTIMO'");
    }

    @Test
    void testes_deduplicaOMesmoTesteComOMesmoResultado() {
        List<TesteFuncionalRealizado> testes = OpcoesAvaliacaoNormalizador.testesFuncionais(List.of(
                new TesteFuncionalRealizadoDTO("SCHOBER", "POSITIVO"),
                new TesteFuncionalRealizadoDTO("ADAMS", "NEGATIVO"),
                new TesteFuncionalRealizadoDTO("SCHOBER", "POSITIVO")));

        assertThat(testes)
                .containsExactly(
                        new TesteFuncionalRealizado("SCHOBER", "POSITIVO"),
                        new TesteFuncionalRealizado("ADAMS", "NEGATIVO"));
    }

    @Test
    void testes_mesmoTesteComResultadosDiferentes_lancaIllegalArgument() {
        assertThatThrownBy(() -> OpcoesAvaliacaoNormalizador.testesFuncionais(List.of(
                        new TesteFuncionalRealizadoDTO("SCHOBER", "POSITIVO"),
                        new TesteFuncionalRealizadoDTO("SCHOBER", "NEGATIVO"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Teste SCHOBER informado com resultados diferentes");
    }

    @Test
    void testes_semResultado_lancaIllegalArgument() {
        assertThatThrownBy(() -> OpcoesAvaliacaoNormalizador.testesFuncionais(
                        List.of(new TesteFuncionalRealizadoDTO("THOMAS", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Informe o resultado (POSITIVO ou NEGATIVO) do teste THOMAS");
    }

    @Test
    void testes_comTesteOuResultadoForaDoEnum_lancaIllegalArgument() {
        assertThatThrownBy(() -> OpcoesAvaliacaoNormalizador.testesFuncionais(
                        List.of(new TesteFuncionalRealizadoDTO("FABER", "POSITIVO"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Opção inválida em testesFuncionais: 'FABER'");
        assertThatThrownBy(() -> OpcoesAvaliacaoNormalizador.testesFuncionais(
                        List.of(new TesteFuncionalRealizadoDTO("THOMAS", "INCONCLUSIVO"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'INCONCLUSIVO'");
    }

    @Test
    void testes_comItemNulo_lancaIllegalArgument() {
        List<TesteFuncionalRealizadoDTO> comNulo = new ArrayList<>();
        comNulo.add(null);

        assertThatThrownBy(() -> OpcoesAvaliacaoNormalizador.testesFuncionais(comNulo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("testesFuncionais não aceita item nulo");
    }

    @Test
    void exameFisico_aplicaSoOsCamposPresentes() {
        ExameFisicoOpcoes exame = new ExameFisicoOpcoes();
        exame.setEquilibrioOpcao("PRESERVADO");
        exame.setTestesFuncionais(List.of(new TesteFuncionalRealizado("ADAMS", "NEGATIVO")));

        OpcoesAvaliacaoNormalizador.aplicarExameFisico(
                exame,
                new AvaliacaoFisioterapeuticaUpdateDTO(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("PE_PLANO"),
                        "Bilateral",
                        "MISTO",
                        null,
                        null,
                        null,
                        "",
                        null,
                        null,
                        null,
                        null));

        assertThat(exame.getAchadosPosturais()).containsExactly("PE_PLANO");
        assertThat(exame.getAchadosPosturaisComplemento()).isEqualTo("Bilateral");
        assertThat(exame.getPadraoRespiratorioOpcao()).isEqualTo("MISTO");
        assertThat(exame.getEquilibrioOpcao()).isEqualTo("PRESERVADO");
        assertThat(exame.getCoordenacaoMotoraOpcao()).isNull();
        assertThat(exame.getTestesFuncionais()).containsExactly(new TesteFuncionalRealizado("ADAMS", "NEGATIVO"));
    }
}
