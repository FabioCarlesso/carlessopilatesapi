package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.IdiomaPreferencia;
import com.carlesso.pilatesapi.entity.enums.TemaPreferencia;
import jakarta.validation.constraints.NotNull;

public record PreferenciasUsuarioRequestDTO(
        @NotNull IdiomaPreferencia idioma,
        @NotNull TemaPreferencia tema,
        @NotNull Boolean notificacoesEmail,
        @NotNull Boolean notificacoesPush
) {
}
