package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Período em que o estúdio não funciona: feriado, manutenção de equipamento, evento, férias coletivas.
 *
 * <p>O bloqueio é <strong>informativo</strong>: não impede a geração automática de aulas nem o agendamento de
 * sessões. Existem exceções legítimas — um feriado em que a turma da manhã resolve treinar — e uma recusa
 * automática obrigaria a recepção a apagar o bloqueio para registrar a exceção. O contrato é avisar, via
 * {@code GET /bloqueios}, para que a agenda mostre o conflito e a decisão continue humana.
 *
 * <p>{@code horaInicio}/{@code horaFim} ausentes significam dia inteiro. A faixa é cruzada com o {@code horario} das
 * aulas pelo calendário (issue #106), sem que o backend recuse ou marque a aula.
 */
// O índice (data_inicio, data_fim) vive só na V35: com ddl-auto=validate o Hibernate não confere índices, então
// declará-lo aqui não valida nada e ainda criaria uma segunda fonte de verdade para divergir da migration.
@Entity
@Table(name = "bloqueios_agenda")
public class BloqueioAgenda extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fim")
    private LocalTime horaFim;

    @Column(nullable = false, length = 200)
    private String motivo;

    public BloqueioAgenda() {}

    /** Dia inteiro bloqueado — a ausência de faixa de horário é o que distingue os dois tipos de bloqueio. */
    public boolean isDiaInteiro() {
        return horaInicio == null && horaFim == null;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDate dataFim) {
        this.dataFim = dataFim;
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

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
