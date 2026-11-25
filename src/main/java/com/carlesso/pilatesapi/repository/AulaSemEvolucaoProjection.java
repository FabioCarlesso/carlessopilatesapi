package com.carlesso.pilatesapi.repository;

import java.time.LocalDate;
import java.time.LocalTime;

/** Aula realizada sem evolução: ver {@link AulaRepository#findRealizadasSemEvolucao}. */
public interface AulaSemEvolucaoProjection {

    Long getAulaId();

    LocalDate getData();

    LocalTime getHorario();

    Long getPacienteId();

    String getPacienteNome();

    Long getProfissionalId();

    String getProfissionalNome();
}
