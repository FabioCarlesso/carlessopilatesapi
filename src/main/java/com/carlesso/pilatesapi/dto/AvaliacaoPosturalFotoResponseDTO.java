package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.storage.FotoArmazenada;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Metadados da foto da análise postural (o binário é servido pelo GET da foto)")
public record AvaliacaoPosturalFotoResponseDTO(
        Long avaliacaoPosturalId,
        String contentType,
        int tamanhoBytes,
        int larguraPx,
        int alturaPx,
        LocalDateTime dataCriacao) {

    public static AvaliacaoPosturalFotoResponseDTO from(Long avaliacaoPosturalId, FotoArmazenada foto) {
        return new AvaliacaoPosturalFotoResponseDTO(
                avaliacaoPosturalId,
                foto.contentType(),
                foto.tamanhoBytes(),
                foto.larguraPx(),
                foto.alturaPx(),
                foto.dataCriacao());
    }
}
