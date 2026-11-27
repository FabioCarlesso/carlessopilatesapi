package com.carlesso.pilatesapi.entity.enums;

/**
 * Vista do mapa corporal em que o ponto de dor foi marcado (#249).
 *
 * <p>Enum próprio, separado de {@link VistaPostural}: o mapa corporal é um desenho do FE com nomes de vista do seu
 * contrato, e as duas listas podem evoluir de forma independente.
 */
public enum VistaCorporal {
    FRENTE,
    COSTAS,
    LATERAL_DIREITA,
    LATERAL_ESQUERDA
}
