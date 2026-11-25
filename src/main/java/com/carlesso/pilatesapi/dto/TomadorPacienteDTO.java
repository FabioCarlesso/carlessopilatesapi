package com.carlesso.pilatesapi.dto;

/** Paciente vinculado a um tomador, na listagem {@code GET /tomadores/{id}/pacientes}. */
public record TomadorPacienteDTO(Long id, String nome, boolean ativo, boolean cpfNaNota) {}
