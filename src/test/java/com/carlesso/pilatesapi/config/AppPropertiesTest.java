package com.carlesso.pilatesapi.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = AppPropertiesTest.Config.class,
        initializers = ConfigDataApplicationContextInitializer.class)
class AppPropertiesTest {

    @EnableConfigurationProperties(AppProperties.class)
    static class Config {}

    @Autowired
    AppProperties appProperties;

    @Test
    void deveCarregarConfiguracoesDeCobranca() {
        assertThat(appProperties.cobranca()).isNotNull();
        assertThat(appProperties.cobranca().cronVencidos()).isEqualTo("0 0 6 * * *");
        assertThat(appProperties.cobranca().cronCobrancasFuturas()).isEqualTo("0 0 7 * * *");
        assertThat(appProperties.cobranca().vencimentoDias()).isEqualTo(10);
    }
}
