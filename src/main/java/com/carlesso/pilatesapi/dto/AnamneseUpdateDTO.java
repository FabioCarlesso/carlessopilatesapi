package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.Pattern;

public record AnamneseUpdateDTO(
        @Pattern(regexp = ".*\\S.*", message = "queixaPrincipal não pode ser vazio")
        String queixaPrincipal,
        String historicoDoencas,
        String historicoCirurgias,
        String historicoLesoes,
        String medicamentosUso,
        String alergias,
        String nivelAtividadeFisica,
        String restricoesMedicas,
        @Pattern(regexp = ".*\\S.*", message = "objetivos não pode ser vazio")
        String objetivos,
        String observacoes
) {}
