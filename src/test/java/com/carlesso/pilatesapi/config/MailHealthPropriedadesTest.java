package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Guarda o desligamento do indicador de saúde de mail nos perfis que rodam sem SMTP.
 *
 * <p>O indicador vem ativo por padrão (o starter de mail está no classpath) e, com
 * {@code spring.mail.host} vazio, falha continuamente. Isso derruba o {@code /actuator/health}
 * agregado para {@code DOWN} — o agregado soma todos os indicadores, enquanto liveness e
 * readiness seguem {@code UP} por usarem grupos restritos. Foi exatamente o que aconteceu em
 * produção (#153): dev e test desligavam o indicador, {@code application-prod.properties} não.
 *
 * <p>A verificação é feita lendo os arquivos do disco porque o {@code application.properties} de
 * teste sombreia o principal no classpath: nenhum teste de contexto consegue observar o valor que
 * o perfil {@code prod} realmente aplica. Mesma razão do {@code CompressaoPropriedadesTest}.
 */
class MailHealthPropriedadesTest {

    private static final String CHAVE = "management.health.mail.enabled";

    private static final List<Path> PERFIS_SEM_SMTP = List.of(
            Path.of("src/main/resources/application-prod.properties"),
            Path.of("src/main/resources/application-dev.properties"),
            Path.of("src/test/resources/application.properties"));

    @Test
    void perfisSemSmtp_devemDesabilitarOIndicadorDeMail() {
        assertThat(PERFIS_SEM_SMTP)
                .allSatisfy(arquivo -> assertThat(propriedades(arquivo).getProperty(CHAVE))
                        .as("%s deve declarar %s=false enquanto não houver SMTP contratado", arquivo, CHAVE)
                        .isEqualTo("false"));
    }

    private static Properties propriedades(Path arquivo) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
