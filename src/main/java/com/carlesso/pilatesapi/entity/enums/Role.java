package com.carlesso.pilatesapi.entity.enums;

public enum Role {
    ADMIN("Administrador", true),
    PROFISSIONAL("Profissional", true),
    RECEPCAO("Recepção", true),
    FINANCEIRO("Financeiro", true),
    // Reservado para o portal do paciente (#173): não aparece em GET /users/roles e
    // nenhuma rota de negócio o aceita, só /users/me.
    PACIENTE("Paciente", false);

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
