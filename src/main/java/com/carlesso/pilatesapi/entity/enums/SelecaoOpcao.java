package com.carlesso.pilatesapi.entity.enums;

/** Quantas opções um campo estruturado da avaliação aceita (#248). */
public enum SelecaoOpcao {
    /** No máximo uma opção; o campo é um texto (ou nulo). */
    UNICA,
    /** Qualquer quantidade; o campo é uma lista, e valores repetidos são descartados. */
    MULTIPLA
}
