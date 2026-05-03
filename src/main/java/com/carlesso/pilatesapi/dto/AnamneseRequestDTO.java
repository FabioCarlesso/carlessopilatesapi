package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnamneseRequestDTO(
        @NotNull Long pacienteId,
        @NotBlank String queixaPrincipal,
        String historicoDoencas,
        String historicoCirurgias,
        String historicoLesoes,
        String medicamentosUso,
        String alergias,
        String nivelAtividadeFisica,
        String restricoesMedicas,
        @NotBlank String objetivos,
        String observacoes
) {}
