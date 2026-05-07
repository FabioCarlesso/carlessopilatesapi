package com.carlesso.pilatesapi.entity.enums;

public enum Role {
    USER("Usuário", true),
    ADMIN("Administrador", true);

    private final String label;
    private final boolean visivel;

    Role(String label, boolean visivel) {
        this.label = label;
        this.visivel = visivel;
    }

    public String getLabel() {
        return label;
    }

    public boolean isVisivel() {
        return visivel;
    }
}
