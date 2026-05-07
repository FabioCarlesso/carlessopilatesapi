package com.carlesso.pilatesapi.entity.enums;

public enum Role {
    USER("Usuário"),
    ADMIN("Administrador");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
