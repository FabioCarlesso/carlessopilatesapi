package com.carlesso.pilatesapi.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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

    @Test
    void ip_v4_mascaraUltimoOcteto() {
        assertThat(LogMasker.ip("203.0.113.10")).isEqualTo("203.0.113.***");
    }

    @Test
    void ip_v6_preservaApenasOsDoisPrimeirosGrupos() {
        assertThat(LogMasker.ip("2001:db8:aa:bb:0:0:0:0/64")).isEqualTo("2001:db8:***");
    }

    @Test
    void ip_nuloVazioOuMalformado_retornaMascaraGenerica() {
        assertThat(LogMasker.ip(null)).isEqualTo("***");
        assertThat(LogMasker.ip(" ")).isEqualTo("***");
        assertThat(LogMasker.ip("semponto")).isEqualTo("***");
        assertThat(LogMasker.ip("::1")).isEqualTo("::***");
    }
}
