package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.PreferenciasUsuario;
import com.carlesso.pilatesapi.entity.enums.IdiomaPreferencia;
import com.carlesso.pilatesapi.entity.enums.TemaPreferencia;

public record PreferenciasUsuarioResponseDTO(
        IdiomaPreferencia idioma,
        TemaPreferencia tema,
        boolean notificacoesEmail,
        boolean notificacoesPush,
        Long version) {
    public static PreferenciasUsuarioResponseDTO from(PreferenciasUsuario preferencias) {
        return new PreferenciasUsuarioResponseDTO(
                preferencias.getIdioma(),
                preferencias.getTema(),
                preferencias.isNotificacoesEmail(),
                preferencias.isNotificacoesPush(),
                preferencias.getVersion());
    }
}
