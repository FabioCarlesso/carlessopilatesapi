package com.carlesso.pilatesapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo de {@code PATCH /pacientes/{id}/tomador}.
 *
 * <p>Obrigatório mas anulável, como em {@code PATCH /aulas/{id}/profissional}: {@code null} explícito desvincula e
 * omitir o campo retorna {@code 400}. Sem a distinção, um corpo {@code {}} montado por engano apagaria o vínculo em
 * silêncio.
 */
public record PacienteTomadorRequestDTO(
        @JsonProperty(required = true)
                @Schema(
                        description = "ID do tomador a vincular; `null` desvincula. Omitir o campo retorna 400",
                        types = {"integer", "null"},
                        format = "int64",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "3")
                Long tomadorId) {}
