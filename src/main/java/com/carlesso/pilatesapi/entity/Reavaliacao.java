package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reavaliacoes")
public class Reavaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avaliacao_fisioterapeutica_id")
    private AvaliacaoFisioterapeutica avaliacaoFisioterapeutica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_tratamento_id")
    private PlanoTratamento planoTratamento;

    @Column(nullable = false)
    private LocalDate dataReavaliacao;

    @Column(columnDefinition = "TEXT")
    private String comparativoAvaliacaoAnterior;

    @Column(columnDefinition = "TEXT")
    private String evolucaoDor;

    @Column(columnDefinition = "TEXT")
    private String evolucaoForca;

    @Column(columnDefinition = "TEXT")
    private String evolucaoMobilidade;

    @Column(columnDefinition = "TEXT")
    private String evolucaoFuncional;

    @Column(columnDefinition = "TEXT")
    private String objetivosAlcancados;

    @Column(columnDefinition = "TEXT")
    private String pontosAtencao;

    @Column(columnDefinition = "TEXT")
    private String ajustesRecomendados;

    @Column(columnDefinition = "TEXT")
    private String observacoesGerais;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    public Reavaliacao() {}

    @PrePersist
    void prePersist() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = paciente;
    }

    public AvaliacaoFisioterapeutica getAvaliacaoFisioterapeutica() {
        return avaliacaoFisioterapeutica;
    }

    public void setAvaliacaoFisioterapeutica(AvaliacaoFisioterapeutica avaliacaoFisioterapeutica) {
        this.avaliacaoFisioterapeutica = avaliacaoFisioterapeutica;
    }

    public PlanoTratamento getPlanoTratamento() {
        return planoTratamento;
    }

    public void setPlanoTratamento(PlanoTratamento planoTratamento) {
        this.planoTratamento = planoTratamento;
    }

    public LocalDate getDataReavaliacao() {
        return dataReavaliacao;
    }

    public void setDataReavaliacao(LocalDate dataReavaliacao) {
        this.dataReavaliacao = dataReavaliacao;
    }

    public String getComparativoAvaliacaoAnterior() {
        return comparativoAvaliacaoAnterior;
    }

    public void setComparativoAvaliacaoAnterior(String comparativoAvaliacaoAnterior) {
        this.comparativoAvaliacaoAnterior = comparativoAvaliacaoAnterior;
    }

    public String getEvolucaoDor() {
        return evolucaoDor;
    }

    public void setEvolucaoDor(String evolucaoDor) {
        this.evolucaoDor = evolucaoDor;
    }

    public String getEvolucaoForca() {
        return evolucaoForca;
    }

    public void setEvolucaoForca(String evolucaoForca) {
        this.evolucaoForca = evolucaoForca;
    }

    public String getEvolucaoMobilidade() {
        return evolucaoMobilidade;
    }

    public void setEvolucaoMobilidade(String evolucaoMobilidade) {
        this.evolucaoMobilidade = evolucaoMobilidade;
    }

    public String getEvolucaoFuncional() {
        return evolucaoFuncional;
    }

    public void setEvolucaoFuncional(String evolucaoFuncional) {
        this.evolucaoFuncional = evolucaoFuncional;
    }

    public String getObjetivosAlcancados() {
        return objetivosAlcancados;
    }

    public void setObjetivosAlcancados(String objetivosAlcancados) {
        this.objetivosAlcancados = objetivosAlcancados;
    }

    public String getPontosAtencao() {
        return pontosAtencao;
    }

    public void setPontosAtencao(String pontosAtencao) {
        this.pontosAtencao = pontosAtencao;
    }

    public String getAjustesRecomendados() {
        return ajustesRecomendados;
    }

    public void setAjustesRecomendados(String ajustesRecomendados) {
        this.ajustesRecomendados = ajustesRecomendados;
    }

    public String getObservacoesGerais() {
        return observacoesGerais;
    }

    public void setObservacoesGerais(String observacoesGerais) {
        this.observacoesGerais = observacoesGerais;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public LocalDateTime getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(LocalDateTime dataAtualizacao) {
        this.dataAtualizacao = dataAtualizacao;
    }
}
