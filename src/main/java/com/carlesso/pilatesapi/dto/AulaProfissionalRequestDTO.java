package com.carlesso.pilatesapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo de {@code PATCH /aulas/{id}/profissional}.
 *
 * <p>O campo é obrigatório mas anulável, e a diferença importa: {@code null} explícito desvincula o profissional,
 * enquanto omitir o campo é recusado com {@code 400}. Sem essa distinção, um corpo {@code {}} montado por engano
 * apagaria o vínculo em silêncio.
 */
public record AulaProfissionalRequestDTO(
        @JsonProperty(required = true)
                @Schema(
                        description = "ID do profissional a vincular; `null` desvincula. Omitir o campo retorna 400",
                        // O contrato é OpenAPI 3.1: `null` precisa entrar como tipo, senão um cliente gerado
                        // a partir do schema não consegue expressar o desvínculo. `nullable` sozinho não basta.
                        types = {"integer", "null"},
                        format = "int64",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "7")
                Long profissionalId) {}
