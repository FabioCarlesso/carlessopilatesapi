package com.carlesso.pilatesapi.entity;

import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "planos")
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoPagamento tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "frequencia_semanal", nullable = false)
    @Enumerated(EnumType.STRING)
    private FrequenciaSemanal frequenciaSemanal;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plano_dias_semana", joinColumns = @JoinColumn(name = "plano_id"))
    @Column(name = "dia_semana")
    @Enumerated(EnumType.STRING)
    private List<DayOfWeek> diasSemana;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(nullable = false)
    private boolean ativo = true;

    public Plano() {}

    public Long getId() { return id; }

    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }

    public TipoPagamento getTipo() { return tipo; }
    public void setTipo(TipoPagamento tipo) { this.tipo = tipo; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public FrequenciaSemanal getFrequenciaSemanal() { return frequenciaSemanal; }
    public void setFrequenciaSemanal(FrequenciaSemanal frequenciaSemanal) { this.frequenciaSemanal = frequenciaSemanal; }

    public List<DayOfWeek> getDiasSemana() { return diasSemana; }
    public void setDiasSemana(List<DayOfWeek> diasSemana) { this.diasSemana = diasSemana; }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
