package com.carlesso.pilatesapi.entity;

/**
 * Teste funcional realizado e o seu resultado, como gravado no JSONB {@code testes_funcionais} (#248).
 *
 * <p>Os dois campos são o {@code name()} de {@link com.carlesso.pilatesapi.entity.enums.TesteFuncional} e de
 * {@link com.carlesso.pilatesapi.entity.enums.ResultadoTesteFuncional}. Ficam como texto, e não como o enum, para que a
 * leitura de um registro antigo nunca dependa de a constante ainda existir.
 */
public record TesteFuncionalRealizado(String teste, String resultado) {}
