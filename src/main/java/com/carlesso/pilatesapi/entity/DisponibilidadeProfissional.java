package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Faixa semanal em que um profissional atende: um dia da semana e um intervalo {@code [horaInicio, horaFim)} no
 * horário local do estúdio (#216).
 *
 * <p>A disponibilidade é <strong>informativa</strong>, como o bloqueio de agenda: alimenta o aviso de horário fora
 * do expediente, mas não impede gerar, atribuir ou remarcar aula. Faixas do mesmo profissional no mesmo dia não se
 * sobrepõem — a regra fica no {@code DisponibilidadeProfissionalService}, serializada por lock no profissional.
 */
@Entity
@Table(name = "disponibilidades_profissional")
public class DisponibilidadeProfissional extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Locking otimista: ver {@code VersaoGuard}. */
    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 20)
    private DayOfWeek diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    public DisponibilidadeProfissional() {}

    /** Faixa semiaberta: o início está dentro, o fim não — quem sai às 12:00 não atende às 12:00. */
    public boolean contem(LocalTime horario) {
        return !horario.isBefore(horaInicio) && horario.isBefore(horaFim);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public Profissional getProfissional() {
        return profissional;
    }

    public void setProfissional(Profissional profissional) {
        this.profissional = profissional;
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
}
