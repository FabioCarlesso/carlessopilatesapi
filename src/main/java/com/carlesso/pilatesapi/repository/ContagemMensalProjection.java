package com.carlesso.pilatesapi.repository;

/** Linha de uma agregação mensal: o mês ({@code ano}/{@code mes}) e a contagem dele. */
public interface ContagemMensalProjection {
    Integer getAno();

    Integer getMes();

    Long getTotal();
}
