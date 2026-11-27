package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.CamposAnamneseOpcoesDTO;
import com.carlesso.pilatesapi.dto.CamposExameFisicoOpcoesDTO;
import com.carlesso.pilatesapi.dto.TesteFuncionalRealizadoDTO;
import com.carlesso.pilatesapi.entity.Anamnese;
import com.carlesso.pilatesapi.entity.ExameFisicoOpcoes;
import com.carlesso.pilatesapi.entity.TesteFuncionalRealizado;
import com.carlesso.pilatesapi.entity.enums.CampoOpcoesAvaliacao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Validação e gravação dos campos estruturados de anamnese, avaliação e reavaliação (#248).
 *
 * <p>Todos os métodos seguem a semântica do PUT parcial dos três recursos — campo ausente ({@code null}) não muda o
 * que está gravado —, e a criação usa os mesmos métodos sobre a entidade recém-instanciada, que começa com listas
 * vazias e seleções nulas. Assim criação e edição validam do mesmo jeito:
 *
 * <ul>
 *   <li>seleção múltipla: lista substitui a atual, com os repetidos descartados na ordem em que chegaram; lista
 *       vazia limpa;
 *   <li>seleção única: texto vazio limpa a seleção (é o único jeito de desmarcar num PUT parcial);
 *   <li>valor fora do enum do campo: {@link IllegalArgumentException}, que vira 400.
 * </ul>
 */
final class OpcoesAvaliacaoNormalizador {

    private OpcoesAvaliacaoNormalizador() {}

    /** Seleção múltipla presente: valida, deduplica e grava. */
    static void aplicarMultipla(CampoOpcoesAvaliacao campo, List<String> valores, Consumer<List<String>> setter) {
        if (valores == null) {
            return;
        }
        LinkedHashSet<String> unicos = new LinkedHashSet<>();
        for (String valor : valores) {
            unicos.add(campo.exigirOpcao(valor));
        }
        setter.accept(List.copyOf(unicos));
    }

    /** Seleção única presente: valida e grava; texto em branco limpa a seleção. */
    static void aplicarUnica(CampoOpcoesAvaliacao campo, String valor, Consumer<String> setter) {
        if (valor != null) {
            setter.accept(valor.isBlank() ? null : campo.exigirOpcao(valor));
        }
    }

    /** Complemento livre presente: grava como veio, igual aos demais campos de texto. */
    static void aplicarTexto(String valor, Consumer<String> setter) {
        if (valor != null) {
            setter.accept(valor);
        }
    }

    /**
     * Valida os testes funcionais: teste e resultado do enum, resultado obrigatório. O mesmo teste repetido com o mesmo
     * resultado é deduplicado; com resultados diferentes não há como saber qual vale, e a requisição é recusada.
     */
    static List<TesteFuncionalRealizado> testesFuncionais(List<TesteFuncionalRealizadoDTO> testes) {
        Map<String, String> resultadoPorTeste = new LinkedHashMap<>();
        for (TesteFuncionalRealizadoDTO item : testes) {
            if (item == null) {
                throw new IllegalArgumentException("testesFuncionais não aceita item nulo");
            }
            String teste = CampoOpcoesAvaliacao.TESTES_FUNCIONAIS.exigirOpcao(item.teste());
            String resultado = CampoOpcoesAvaliacao.exigirResultado(teste, item.resultado());
            String anterior = resultadoPorTeste.putIfAbsent(teste, resultado);
            if (anterior != null && !anterior.equals(resultado)) {
                throw new IllegalArgumentException("Teste " + teste + " informado com resultados diferentes");
            }
        }
        List<TesteFuncionalRealizado> normalizados = new ArrayList<>();
        resultadoPorTeste.forEach(
                (teste, resultado) -> normalizados.add(new TesteFuncionalRealizado(teste, resultado)));
        return normalizados;
    }

    /**
     * Aplica os campos do exame físico comuns à avaliação e à reavaliação. Um valor inválido lança exceção no meio do
     * caminho; a transação do service é desfeita, então a entidade não chega ao banco meio alterada.
     */
    static void aplicarExameFisico(ExameFisicoOpcoes exame, CamposExameFisicoOpcoesDTO dto) {
        aplicarMultipla(CampoOpcoesAvaliacao.ACHADOS_POSTURAIS, dto.achadosPosturais(), exame::setAchadosPosturais);
        aplicarTexto(dto.achadosPosturaisComplemento(), exame::setAchadosPosturaisComplemento);
        aplicarUnica(
                CampoOpcoesAvaliacao.PADRAO_RESPIRATORIO,
                dto.padraoRespiratorioOpcao(),
                exame::setPadraoRespiratorioOpcao);
        aplicarTexto(dto.padraoRespiratorioComplemento(), exame::setPadraoRespiratorioComplemento);
        aplicarUnica(CampoOpcoesAvaliacao.EQUILIBRIO, dto.equilibrioOpcao(), exame::setEquilibrioOpcao);
        aplicarTexto(dto.equilibrioComplemento(), exame::setEquilibrioComplemento);
        aplicarUnica(
                CampoOpcoesAvaliacao.COORDENACAO_MOTORA,
                dto.coordenacaoMotoraOpcao(),
                exame::setCoordenacaoMotoraOpcao);
        aplicarTexto(dto.coordenacaoMotoraComplemento(), exame::setCoordenacaoMotoraComplemento);
        if (dto.testesFuncionais() != null) {
            exame.setTestesFuncionais(testesFuncionais(dto.testesFuncionais()));
        }
        aplicarTexto(dto.testesFuncionaisComplemento(), exame::setTestesFuncionaisComplemento);
    }

    /** Aplica os campos estruturados da anamnese; mesma semântica de {@link #aplicarExameFisico}. */
    static void aplicarAnamnese(Anamnese anamnese, CamposAnamneseOpcoesDTO dto) {
        aplicarMultipla(CampoOpcoesAvaliacao.REGIOES_DOR, dto.regioesDor(), anamnese::setRegioesDor);
        aplicarTexto(dto.regioesDorComplemento(), anamnese::setRegioesDorComplemento);
        aplicarMultipla(CampoOpcoesAvaliacao.CONDICOES_SAUDE, dto.condicoesSaude(), anamnese::setCondicoesSaude);
        aplicarTexto(dto.condicoesSaudeComplemento(), anamnese::setCondicoesSaudeComplemento);
        aplicarUnica(
                CampoOpcoesAvaliacao.NIVEL_ATIVIDADE_FISICA,
                dto.nivelAtividadeFisicaOpcao(),
                anamnese::setNivelAtividadeFisicaOpcao);
        aplicarTexto(dto.nivelAtividadeFisicaComplemento(), anamnese::setNivelAtividadeFisicaComplemento);
        aplicarMultipla(CampoOpcoesAvaliacao.OBJETIVOS, dto.objetivosOpcoes(), anamnese::setObjetivosOpcoes);
        aplicarTexto(dto.objetivosComplemento(), anamnese::setObjetivosComplemento);
    }
}
