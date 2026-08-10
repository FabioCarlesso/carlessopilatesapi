package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.support.ArquivoDePropriedades;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guarda o desligamento do indicador de saúde de mail enquanto não houver SMTP contratado.
 *
 * <p>O indicador vem ativo por padrão (o starter de mail está no classpath) e, com
 * {@code spring.mail.host} vazio, falha continuamente. Isso derruba o {@code /actuator/health}
 * agregado para {@code DOWN} — o agregado soma todos os indicadores, enquanto liveness e
 * readiness seguem {@code UP} por usarem grupos restritos. Foi o que deixou a produção em 503
 * com o serviço são (#153).
 *
 * <p>A chave é declarada uma vez no {@code application.properties} principal, valendo para todos
 * os perfis. O arquivo de teste precisa repeti-la porque sombreia o principal no classpath, e é
 * essa duplicação obrigatória — a mesma do {@code CompressaoPropriedadesTest} — que este teste
 * guarda. Por isso a leitura é do disco: pelo classpath só o arquivo de teste seria alcançado.
 *
 * <p>Ao contratar SMTP, reative o indicador no perfil desejado e ajuste este teste junto.
 *
 * <p>Fora do alcance daqui: a variável de ambiente {@code MANAGEMENT_HEALTH_MAIL_ENABLED}
 * sobrepõe os dois arquivos e vive na configuração da plataforma de deploy. Contra isso, só um
 * smoke check pós-deploy no {@code /actuator/health} (ver {@code docs/operacao.md}).
 */
class MailHealthPropriedadesTest {

    private static final String CHAVE = "management.health.mail.enabled";

    private static final Path PRINCIPAL = Path.of("src/main/resources/application.properties");
    private static final Path TESTE = Path.of("src/test/resources/application.properties");

    @Test
    void arquivosDeConfiguracao_devemDesabilitarOIndicadorDeMail() {
        assertThat(List.of(PRINCIPAL, TESTE)).allSatisfy(arquivo -> assertThat(
                        ArquivoDePropriedades.ler(arquivo).getProperty(CHAVE))
                .as("%s deve declarar %s=false enquanto não houver SMTP contratado", arquivo, CHAVE)
                .isEqualTo("false"));
    }
}
