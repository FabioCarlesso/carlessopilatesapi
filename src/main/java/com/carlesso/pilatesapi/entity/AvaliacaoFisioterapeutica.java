package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "avaliacoes_fisioterapeuticas")
public class AvaliacaoFisioterapeutica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Column(nullable = false)
    private LocalDate dataAvaliacao;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String queixaFuncional;

    @Column(columnDefinition = "TEXT")
    private String avaliacaoPostural;

    @Column(columnDefinition = "TEXT")
    private String mobilidadeArticular;

    @Column(columnDefinition = "TEXT")
    private String forcaMuscular;

    @Column(columnDefinition = "TEXT")
    private String flexibilidade;

    @Column(columnDefinition = "TEXT")
    private String equilibrio;

    @Column(columnDefinition = "TEXT")
    private String coordenacaoMotora;

    @Column(columnDefinition = "TEXT")
    private String padraoRespiratorio;

    @Column(nullable = false)
    private Integer escalaDor;

    @Column(columnDefinition = "TEXT")
    private String testesFuncionaisRealizados;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String diagnosticoFisioterapeutico;

    @Column(columnDefinition = "TEXT")
    private String observacoesGerais;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    public AvaliacaoFisioterapeutica() {}

    public Long getId() { return id; }

    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }

    public LocalDate getDataAvaliacao() { return dataAvaliacao; }
    public void setDataAvaliacao(LocalDate dataAvaliacao) { this.dataAvaliacao = dataAvaliacao; }

    public String getQueixaFuncional() { return queixaFuncional; }
    public void setQueixaFuncional(String queixaFuncional) { this.queixaFuncional = queixaFuncional; }

    public String getAvaliacaoPostural() { return avaliacaoPostural; }
    public void setAvaliacaoPostural(String avaliacaoPostural) { this.avaliacaoPostural = avaliacaoPostural; }

    public String getMobilidadeArticular() { return mobilidadeArticular; }
    public void setMobilidadeArticular(String mobilidadeArticular) { this.mobilidadeArticular = mobilidadeArticular; }

    public String getForcaMuscular() { return forcaMuscular; }
    public void setForcaMuscular(String forcaMuscular) { this.forcaMuscular = forcaMuscular; }

    public String getFlexibilidade() { return flexibilidade; }
    public void setFlexibilidade(String flexibilidade) { this.flexibilidade = flexibilidade; }

    public String getEquilibrio() { return equilibrio; }
    public void setEquilibrio(String equilibrio) { this.equilibrio = equilibrio; }

    public String getCoordenacaoMotora() { return coordenacaoMotora; }
    public void setCoordenacaoMotora(String coordenacaoMotora) { this.coordenacaoMotora = coordenacaoMotora; }

    public String getPadraoRespiratorio() { return padraoRespiratorio; }
    public void setPadraoRespiratorio(String padraoRespiratorio) { this.padraoRespiratorio = padraoRespiratorio; }

    public Integer getEscalaDor() { return escalaDor; }
    public void setEscalaDor(Integer escalaDor) { this.escalaDor = escalaDor; }

    public String getTestesFuncionaisRealizados() { return testesFuncionaisRealizados; }
    public void setTestesFuncionaisRealizados(String testesFuncionaisRealizados) { this.testesFuncionaisRealizados = testesFuncionaisRealizados; }

    public String getDiagnosticoFisioterapeutico() { return diagnosticoFisioterapeutico; }
    public void setDiagnosticoFisioterapeutico(String diagnosticoFisioterapeutico) { this.diagnosticoFisioterapeutico = diagnosticoFisioterapeutico; }

    public String getObservacoesGerais() { return observacoesGerais; }
    public void setObservacoesGerais(String observacoesGerais) { this.observacoesGerais = observacoesGerais; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
}
