package com.carlesso.pilatesapi.entity.enums;

public enum IdiomaPreferencia {
    PT_BR("Português (Brasil)"),
    EN_US("English (US)"),
    ES_ES("Español (España)");

    private final String label;

    IdiomaPreferencia(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
