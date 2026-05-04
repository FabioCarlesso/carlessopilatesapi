package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "planos_tratamento")
public class PlanoTratamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Column(nullable = false)
    private LocalDate dataInicio;

    private LocalDate dataFimPrevista;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String objetivosTratamento;

    @Column(columnDefinition = "TEXT")
    private String intervencoesPlanejadas;

    private Integer numeroSessoesPrevistas;

    @Column(length = 100)
    private String frequenciaSessoes;

    @Column(length = 255)
    private String responsavelTratamento;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    @Column(nullable = false)
    private boolean ativo = true;

    public PlanoTratamento() {}

    @PrePersist
    void prePersist() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFimPrevista() { return dataFimPrevista; }
    public void setDataFimPrevista(LocalDate dataFimPrevista) { this.dataFimPrevista = dataFimPrevista; }

    public String getObjetivosTratamento() { return objetivosTratamento; }
    public void setObjetivosTratamento(String objetivosTratamento) { this.objetivosTratamento = objetivosTratamento; }

    public String getIntervencoesPlanejadas() { return intervencoesPlanejadas; }
    public void setIntervencoesPlanejadas(String intervencoesPlanejadas) { this.intervencoesPlanejadas = intervencoesPlanejadas; }

    public Integer getNumeroSessoesPrevistas() { return numeroSessoesPrevistas; }
    public void setNumeroSessoesPrevistas(Integer numeroSessoesPrevistas) { this.numeroSessoesPrevistas = numeroSessoesPrevistas; }

    public String getFrequenciaSessoes() { return frequenciaSessoes; }
    public void setFrequenciaSessoes(String frequenciaSessoes) { this.frequenciaSessoes = frequenciaSessoes; }

    public String getResponsavelTratamento() { return responsavelTratamento; }
    public void setResponsavelTratamento(String responsavelTratamento) { this.responsavelTratamento = responsavelTratamento; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
