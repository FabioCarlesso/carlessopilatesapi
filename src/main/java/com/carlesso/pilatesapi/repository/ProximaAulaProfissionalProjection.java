package com.carlesso.pilatesapi.repository;

/** Paciente e profissional de uma aula futura não realizada: ver {@link AulaRepository#findProfissionaisDasProximasAulas}. */
public interface ProximaAulaProfissionalProjection {

    Long getPacienteId();

    Long getProfissionalId();

    String getProfissionalNome();
}
