package com.carlesso.pilatesapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AppPropertiesTest {

    @Autowired
    AppProperties appProperties;

    @Test
    void deveCarregarConfiguracoesDeCobranca() {
        assertThat(appProperties.cobranca()).isNotNull();
        assertThat(appProperties.cobranca().cronVencidos()).isEqualTo("0 0 6 * * *");
        assertThat(appProperties.cobranca().cronCobrancasFuturas()).isEqualTo("0 0 7 * * *");
        assertThat(appProperties.cobranca().vencimentoDias()).isEqualTo(10);
    }

    @Test
    void deveCarregarConfiguracoesDePaginacao() {
        assertThat(appProperties.paginacao()).isNotNull();
        assertThat(appProperties.paginacao().tamanhoPadrao()).isEqualTo(10);
    }
}
