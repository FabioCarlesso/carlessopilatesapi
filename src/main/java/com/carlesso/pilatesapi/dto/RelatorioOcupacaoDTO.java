package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Ocupação da grade semanal (#235): por dia, horário e profissional, quantos alunos ocupam o horário, quantas vagas
 * restam e quantos inscritos da lista de espera cobrem aquele dia e horário.
 */
public record RelatorioOcupacaoDTO(
        Semana semana,
        @Schema(description = "Capacidade por horário de cada profissional (`app.agenda.capacidade-por-horario`)")
                int capacidadePorHorario,
        Filtros filtros,
        Totais totais,
        @Schema(
                        description = "Células ordenadas por data, horário (sem horário por último) e profissional (sem"
                                + " profissional por último)")
                List<Celula> celulas,
        LocalDateTime geradoEm) {

    @Schema(description = "Semana de segunda a domingo que contém a data pedida")
    public record Semana(LocalDate inicio, LocalDate fim) {}

    public record Filtros(Long profissionalId, String profissionalNome) {}

    /**
     * @param ocupacao alunos nas células com profissional (a soma é o número de aulas da semana com profissional)
     * @param semProfissional aulas da semana sem profissional, fora da conta de vagas
     * @param capacidade soma da capacidade das células que contam vaga
     * @param vagas soma das vagas das células que contam vaga
     */
    public record Totais(long aulas, long ocupacao, long semProfissional, long capacidade, long vagas) {}

    /**
     * Um horário de um profissional num dia da semana.
     *
     * @param capacidade {@code null} quando a célula não conta vaga (sem profissional ou sem horário)
     * @param vagas {@code null} quando a célula não conta vaga; {@code 0} fora da disponibilidade do profissional
     * @param dentroDaDisponibilidade {@code null} quando a célula não conta vaga; {@code true} também para o
     *     profissional sem faixas cadastradas
     * @param listaEspera inscritos ativos cuja faixa contém o horário da célula, no mesmo dia da semana ({@code 0} sem
     *     horário)
     */
    public record Celula(
            LocalDate data,
            DayOfWeek diaSemana,
            LocalTime horario,
            ProfissionalResumo profissional,
            int ocupacao,
            Integer capacidade,
            Integer vagas,
            boolean lotado,
            Boolean dentroDaDisponibilidade,
            int listaEspera,
            List<Aluno> alunos) {}

    public record ProfissionalResumo(Long id, String nome) {}

    public record Aluno(Long id, String nome) {}
}
