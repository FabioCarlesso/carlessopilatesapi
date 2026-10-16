package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.IdiomaPreferencia;
import com.carlesso.pilatesapi.entity.enums.TemaPreferencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PreferenciasUsuarioRequestDTO(
        @NotNull IdiomaPreferencia idioma,
        @NotNull TemaPreferencia tema,
        @NotNull Boolean notificacoesEmail,
        @NotNull Boolean notificacoesPush,
        @Schema(
                        description =
                                "Versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem (modo legado)",
                        example = "0")
                Long version) {}
