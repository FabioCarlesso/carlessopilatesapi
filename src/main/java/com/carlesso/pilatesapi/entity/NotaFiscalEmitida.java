package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "notas_fiscais_emitidas",
        uniqueConstraints = @UniqueConstraint(columnNames = {"paciente_id", "competencia"}))
public class NotaFiscalEmitida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    /**
     * Competência fiscal da nota, armazenada como o primeiro dia do mês para
     * permitir ordenação e comparação ({@code MM/AAAA} na API).
     */
    @Column(nullable = false)
    private LocalDate competencia;

    @Column(name = "numero_nota", length = 60)
    private String numeroNota;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    public NotaFiscalEmitida() {}

    @PrePersist
    void prePersist() {
        this.dataCriacao = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.dataAtualizacao = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }

    public LocalDate getCompetencia() { return competencia; }
    public void setCompetencia(LocalDate competencia) { this.competencia = competencia; }

    public String getNumeroNota() { return numeroNota; }
    public void setNumeroNota(String numeroNota) { this.numeroNota = numeroNota; }

    public LocalDate getDataEmissao() { return dataEmissao; }
    public void setDataEmissao(LocalDate dataEmissao) { this.dataEmissao = dataEmissao; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
}
