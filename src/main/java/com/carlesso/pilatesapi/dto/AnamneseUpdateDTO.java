package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

public record AnamneseUpdateDTO(
        @Pattern(regexp = ".*\\S.*", message = "queixaPrincipal não pode ser vazio") String queixaPrincipal,
        String historicoDoencas,
        String historicoCirurgias,
        String historicoLesoes,
        String medicamentosUso,
        String alergias,
        String nivelAtividadeFisica,
        String restricoesMedicas,
        @Pattern(regexp = ".*\\S.*", message = "objetivos não pode ser vazio") String objetivos,
        String observacoes,
        @Schema(
                        description =
                                "Versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem (modo legado)",
                        example = "0")
                Long version) {}
