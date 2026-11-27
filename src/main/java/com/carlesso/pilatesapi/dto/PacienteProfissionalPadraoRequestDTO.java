package com.carlesso.pilatesapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo de {@code PATCH /pacientes/{id}/profissional-padrao} (#245).
 *
 * <p>Obrigatório mas anulável, como no tomador: {@code null} explícito remove o padrão e omitir o campo retorna
 * {@code 400}, para um corpo {@code {}} montado por engano não apagar o vínculo em silêncio.
 */
public record PacienteProfissionalPadraoRequestDTO(
        @JsonProperty(required = true)
                @Schema(
                        description = "ID do profissional padrão; `null` remove o padrão. Omitir o campo retorna 400",
                        types = {"integer", "null"},
                        format = "int64",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "7")
                Long profissionalId) {}
