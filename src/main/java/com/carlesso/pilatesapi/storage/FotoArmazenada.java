package com.carlesso.pilatesapi.storage;

import java.time.LocalDateTime;

/** Foto da análise postural com seus metadados, tal como persistida pelo {@link FotoStorage}. */
public record FotoArmazenada(
        byte[] conteudo,
        String contentType,
        int tamanhoBytes,
        int larguraPx,
        int alturaPx,
        LocalDateTime dataCriacao) {}
