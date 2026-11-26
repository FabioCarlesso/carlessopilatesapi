package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "aulas",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"paciente_id", "data"})})
public class Aula extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pagamento_id", nullable = false)
    private Pagamento pagamento;

    @ManyToOne
    @JoinColumn(name = "profissional_id")
    private Profissional profissional;

    @Column(nullable = false)
    private LocalDate data;

    /** Nulo em aulas anteriores à #106 e nas geradas por plano sem horário. */
    private LocalTime horario;

    @Column(nullable = false)
    private boolean realizada = false;

    /** Caiu num feriado e foi marcada realizada por isso (#244): gera repasse, consome o pacote e não é falta. */
    @Column(nullable = false)
    private boolean feriado = false;

    /** Bloqueio que marcou a aula como feriado; é por ele que a marcação é desfeita, sem tocar em aula realizada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bloqueio_feriado_id")
    private BloqueioAgenda bloqueioFeriado;

    /**
     * A aula tem três escritores concorrentes ({@code realizar}, {@code profissional} e {@code remarcar}) e cada um
     * grava um campo diferente da mesma linha. Sem versão, realizar e remarcar em paralelo se sobrescrevem em
     * silêncio e a aula acaba marcada como realizada numa data em que não foi ministrada — erro que segue direto
     * para o repasse do profissional, que agrupa por {@code data}.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    public Aula() {}

    public Long getVersion() {
        return version;
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

    public Pagamento getPagamento() {
        return pagamento;
    }

    public void setPagamento(Pagamento pagamento) {
        this.pagamento = pagamento;
    }

    public Profissional getProfissional() {
        return profissional;
    }

    public void setProfissional(Profissional profissional) {
        this.profissional = profissional;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public LocalTime getHorario() {
        return horario;
    }

    public void setHorario(LocalTime horario) {
        this.horario = horario;
    }

    public boolean isRealizada() {
        return realizada;
    }

    public void setRealizada(boolean realizada) {
        this.realizada = realizada;
    }

    public boolean isFeriado() {
        return feriado;
    }

    public BloqueioAgenda getBloqueioFeriado() {
        return bloqueioFeriado;
    }

    /** Marca a aula como realizada pelo feriado do {@code bloqueio}. */
    public void marcarFeriado(BloqueioAgenda bloqueio) {
        this.realizada = true;
        this.feriado = true;
        this.bloqueioFeriado = bloqueio;
    }

    /** Desfaz a marcação de feriado: a aula volta a não realizada. */
    public void desmarcarFeriado() {
        this.realizada = false;
        this.feriado = false;
        this.bloqueioFeriado = null;
    }
}
