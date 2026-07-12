package com.carlesso.pilatesapi.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskerTest {

    @Test
    void email_mascaraLocalPreservandoInicialEDominio() {
        assertThat(LogMasker.email("joao.silva@dominio.com")).isEqualTo("j***@dominio.com");
    }

    @Test
    void email_nuloOuVazio_retornaMascaraGenerica() {
        assertThat(LogMasker.email(null)).isEqualTo("***");
        assertThat(LogMasker.email("   ")).isEqualTo("***");
    }

    @Test
    void email_semArroba_retornaMascaraGenerica() {
        assertThat(LogMasker.email("semarroba")).isEqualTo("***");
    }

    @Test
    void email_localVazio_naoExpoeNada() {
        assertThat(LogMasker.email("@dominio.com")).isEqualTo("***@dominio.com");
    }

    @Test
    void cpf_preservaApenasDoisUltimosDigitos() {
        assertThat(LogMasker.cpf("11122233344")).isEqualTo("***.***.***-44");
    }

    @Test
    void cpf_nuloOuVazio_retornaMascaraGenerica() {
        assertThat(LogMasker.cpf(null)).isEqualTo("***");
        assertThat(LogMasker.cpf("")).isEqualTo("***");
    }

    @Test
    void cpf_curto_retornaMascaraGenerica() {
        assertThat(LogMasker.cpf("1")).isEqualTo("***");
    }
}
