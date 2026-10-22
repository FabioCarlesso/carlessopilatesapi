package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Interesse de um paciente em entrar num dia da semana e faixa de horário que hoje está lotado.
 *
 * <p>A fila é cronológica e só cronológica: {@code dataEntrada} é gravada pelo servidor no
 * {@code @PrePersist} e nunca aceita do cliente, porque é ela — e nada mais — que define a ordem de chegada. Não
 * existe posição manual; ver issue #172.
 *
 * <p>A saída da fila é lógica ({@code ativo = false}) e não física: quem foi chamado para uma vaga precisa continuar
 * no histórico, mesmo tendo sumido da listagem. O contrato HTTP continua sendo {@code DELETE}.
 */
// O índice (dia_semana, hora_inicio) vive só na V36: com ddl-auto=validate o Hibernate não confere índices, então
// declará-lo aqui não valida nada e ainda criaria uma segunda fonte de verdade para divergir da migration.
@Entity
@Table(name = "lista_espera")
public class ListaEspera extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 20)
    private DayOfWeek diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(name = "data_entrada", nullable = false)
    private LocalDateTime dataEntrada;

    @Column(length = 500)
    private String observacao;

    @Column(nullable = false)
    private boolean ativo = true;

    public ListaEspera() {}

    @PrePersist
    void prePersist() {
        if (this.dataEntrada == null) {
            this.dataEntrada = LocalDateTime.now();
        }
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

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(DayOfWeek diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFim() {
        return horaFim;
    }

    public void setHoraFim(LocalTime horaFim) {
        this.horaFim = horaFim;
    }

    public LocalDateTime getDataEntrada() {
        return dataEntrada;
    }

    public void setDataEntrada(LocalDateTime dataEntrada) {
        this.dataEntrada = dataEntrada;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
