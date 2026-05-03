package com.carlesso.pilatesapi.dto;

public record AnamneseUpdateDTO(
        String queixaPrincipal,
        String historicoDoencas,
        String historicoCirurgias,
        String historicoLesoes,
        String medicamentosUso,
        String alergias,
        String nivelAtividadeFisica,
        String restricoesMedicas,
        String objetivos,
        String observacoes
) {}
