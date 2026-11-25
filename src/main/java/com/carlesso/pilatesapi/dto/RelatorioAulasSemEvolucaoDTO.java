package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Aulas realizadas no período sem evolução clínica registrada para o mesmo paciente na mesma data (#234): o controle
 * do prontuário por profissional.
 */
public record RelatorioAulasSemEvolucaoDTO(
        PeriodoDTO periodo, Filtros filtros, Resumo resumo, List<AulaSemEvolucao> aulas, LocalDateTime geradoEm) {

    @Schema(description = "Filtros aplicados; null quando o filtro não foi informado")
    public record Filtros(Long profissionalId, String profissionalNome) {}

    @Schema(description = "Totais das linhas do relatório; a soma de porProfissional é totalAulas")
    public record Resumo(long totalAulas, List<TotalPorProfissional> porProfissional) {}

    @Schema(
            description = "Quantidade de aulas por profissional, por nome; as aulas sem profissional definido vêm por"
                    + " último, com profissionalId null e profissionalNome \"Sem profissional\"")
    public record TotalPorProfissional(Long profissionalId, String profissionalNome, long quantidade) {}

    @Schema(description = "Aula realizada sem evolução; profissionalId e profissionalNome são null sem profissional")
    public record AulaSemEvolucao(
            Long aulaId,
            LocalDate data,
            LocalTime horario,
            Long pacienteId,
            String pacienteNome,
            Long profissionalId,
            String profissionalNome) {}
}
