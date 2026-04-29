package com.carlesso.pilatesapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    @EnableConfigurationProperties(AppProperties.class)
    static class Config {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "app.cobranca.cron-vencidos=0 0 6 * * *",
                    "app.cobranca.cron-cobrancas-futuras=0 0 7 * * *",
                    "app.cobranca.vencimento-dias=10");

    @Test
    void deveCarregarConfiguracoesDeCobranca() {
        contextRunner.run(context -> {
            AppProperties appProperties = context.getBean(AppProperties.class);

            assertThat(appProperties.cobranca()).isNotNull();
            assertThat(appProperties.cobranca().cronVencidos()).isEqualTo("0 0 6 * * *");
            assertThat(appProperties.cobranca().cronCobrancasFuturas()).isEqualTo("0 0 7 * * *");
            assertThat(appProperties.cobranca().vencimentoDias()).isEqualTo(10);
        });
    }

    @Test
    void deveRejeitarCronComFormatoInvalido() {
        contextRunner
                .withPropertyValues("app.cobranca.cron-vencidos=invalido")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("cobranca.cronVencidos")
                        .hasStackTraceContaining("Deve ser uma cron expression com 6 campos"));
    }

    @Test
    void deveRejeitarVencimentoDiasMenorQueUm() {
        contextRunner
                .withPropertyValues("app.cobranca.vencimento-dias=0")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("cobranca.vencimentoDias")
                        .hasStackTraceContaining("must be greater than or equal to 1"));
    }
}
