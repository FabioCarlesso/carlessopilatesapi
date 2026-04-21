package com.carlesso.pilatesapi.entity.enums;

public enum TipoPagamento {
    MENSAL(1),
    TRIMESTRAL(3),
    ANUAL(12);

    private final int meses;

    TipoPagamento(int meses) {
        this.meses = meses;
    }

    public int getMeses() {
        return meses;
    }
}
