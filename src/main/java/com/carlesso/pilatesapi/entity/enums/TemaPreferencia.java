package com.carlesso.pilatesapi.entity.enums;

public enum TemaPreferencia {
    CLARO("Claro"),
    ESCURO("Escuro");

    private final String label;

    TemaPreferencia(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
