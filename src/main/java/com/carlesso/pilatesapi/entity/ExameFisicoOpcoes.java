package com.carlesso.pilatesapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Campos estruturados do exame físico, comuns à avaliação fisioterapêutica e à reavaliação (#248).
 *
 * <p>Embutido nas duas entidades para que os mesmos campos, com os mesmos nomes de coluna, existam nas duas tabelas
 * sem duplicar o mapeamento — a reavaliação compara exatamente o que a avaliação registrou.
 *
 * <p>Os valores são o {@code name()} dos enums de opção ({@link com.carlesso.pilatesapi.entity.enums.OpcaoAvaliacao})
 * guardados como texto: a validação contra o enum acontece na escrita, e a leitura de um registro antigo nunca
 * quebra por causa de uma opção que deixou de existir. Listas de seleção múltipla vão em {@code text[]}; os testes
 * funcionais, que são pares teste/resultado, em {@code jsonb}. Cada campo tem um complemento livre.
 *
 * <p>Os campos de seleção única levam o sufixo {@code Opcao} porque a avaliação já tem colunas de texto livre
 * {@code equilibrio}, {@code coordenacao_motora} e {@code padrao_respiratorio}, que continuam existindo.
 */
@Embeddable
public class ExameFisicoOpcoes {

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "achados_posturais")
    private List<String> achadosPosturais = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String achadosPosturaisComplemento;

    @Column(length = 40)
    private String padraoRespiratorioOpcao;

    @Column(columnDefinition = "TEXT")
    private String padraoRespiratorioComplemento;

    @Column(length = 40)
    private String equilibrioOpcao;

    @Column(columnDefinition = "TEXT")
    private String equilibrioComplemento;

    @Column(length = 40)
    private String coordenacaoMotoraOpcao;

    @Column(columnDefinition = "TEXT")
    private String coordenacaoMotoraComplemento;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "testes_funcionais")
    private List<TesteFuncionalRealizado> testesFuncionais = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String testesFuncionaisComplemento;

    public List<String> getAchadosPosturais() {
        return achadosPosturais == null ? List.of() : List.copyOf(achadosPosturais);
    }

    public void setAchadosPosturais(List<String> achadosPosturais) {
        this.achadosPosturais = achadosPosturais == null ? new ArrayList<>() : new ArrayList<>(achadosPosturais);
    }

    public String getAchadosPosturaisComplemento() {
        return achadosPosturaisComplemento;
    }

    public void setAchadosPosturaisComplemento(String achadosPosturaisComplemento) {
        this.achadosPosturaisComplemento = achadosPosturaisComplemento;
    }

    public String getPadraoRespiratorioOpcao() {
        return padraoRespiratorioOpcao;
    }

    public void setPadraoRespiratorioOpcao(String padraoRespiratorioOpcao) {
        this.padraoRespiratorioOpcao = padraoRespiratorioOpcao;
    }

    public String getPadraoRespiratorioComplemento() {
        return padraoRespiratorioComplemento;
    }

    public void setPadraoRespiratorioComplemento(String padraoRespiratorioComplemento) {
        this.padraoRespiratorioComplemento = padraoRespiratorioComplemento;
    }

    public String getEquilibrioOpcao() {
        return equilibrioOpcao;
    }

    public void setEquilibrioOpcao(String equilibrioOpcao) {
        this.equilibrioOpcao = equilibrioOpcao;
    }

    public String getEquilibrioComplemento() {
        return equilibrioComplemento;
    }

    public void setEquilibrioComplemento(String equilibrioComplemento) {
        this.equilibrioComplemento = equilibrioComplemento;
    }

    public String getCoordenacaoMotoraOpcao() {
        return coordenacaoMotoraOpcao;
    }

    public void setCoordenacaoMotoraOpcao(String coordenacaoMotoraOpcao) {
        this.coordenacaoMotoraOpcao = coordenacaoMotoraOpcao;
    }

    public String getCoordenacaoMotoraComplemento() {
        return coordenacaoMotoraComplemento;
    }

    public void setCoordenacaoMotoraComplemento(String coordenacaoMotoraComplemento) {
        this.coordenacaoMotoraComplemento = coordenacaoMotoraComplemento;
    }

    public List<TesteFuncionalRealizado> getTestesFuncionais() {
        return testesFuncionais == null ? List.of() : List.copyOf(testesFuncionais);
    }

    public void setTestesFuncionais(List<TesteFuncionalRealizado> testesFuncionais) {
        this.testesFuncionais = testesFuncionais == null ? new ArrayList<>() : new ArrayList<>(testesFuncionais);
    }

    public String getTestesFuncionaisComplemento() {
        return testesFuncionaisComplemento;
    }

    public void setTestesFuncionaisComplemento(String testesFuncionaisComplemento) {
        this.testesFuncionaisComplemento = testesFuncionaisComplemento;
    }
}
