package com.carlesso.pilatesapi.entity.enums;

public enum FrequenciaSemanal {
    UMA_VEZ(1, 4),
    DUAS_VEZES(2, 8),
    TRES_VEZES(3, 12);

    private final int vezesPorSemana;
    private final int aulasPorMes;

    FrequenciaSemanal(int vezesPorSemana, int aulasPorMes) {
        this.vezesPorSemana = vezesPorSemana;
        this.aulasPorMes = aulasPorMes;
    }

    public int getVezesPorSemana() {
        return vezesPorSemana;
    }

    public int getAulasPorMes() {
        return aulasPorMes;
    }
}
