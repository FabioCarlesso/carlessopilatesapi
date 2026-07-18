package com.carlesso.pilatesapi.entity.enums;

public enum VistaPostural {
    FRENTE,
    COSTAS,
    LADO_DIREITO,
    LADO_ESQUERDO;

    /** Vistas laterais usam pontos únicos; frente e costas usam pares esquerdo/direito. */
    public boolean isLateral() {
        return this == LADO_DIREITO || this == LADO_ESQUERDO;
    }
}
