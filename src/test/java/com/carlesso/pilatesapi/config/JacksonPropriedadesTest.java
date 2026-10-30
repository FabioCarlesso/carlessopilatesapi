package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.support.ArquivoDePropriedades;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guarda a configuração do Jackson nos dois {@code application.properties}.
 *
 * <p>O Jackson 3, que veio com o Spring Boot 4, ordena as propriedades alfabeticamente por padrão. A API desliga isso
 * para manter a ordem que sempre teve — o {@code ProblemDetail} das respostas de erro começaria por {@code detail}. Como
 * o arquivo de teste sombreia o principal no classpath, a chave precisa estar nos dois, senão a suíte rodaria com uma
 * serialização diferente da de produção.
 */
class JacksonPropriedadesTest {

    private static final String PREFIXO = "spring.jackson.";

    private static final Path PRINCIPAL = Path.of("src/main/resources/application.properties");
    private static final Path TESTE = Path.of("src/test/resources/application.properties");

    @Test
    void configuracaoDoJackson_deveSerIdenticaNosDoisArquivos() {
        assertThat(jackson(TESTE))
                .as("%s deve espelhar %s nas chaves %s*", TESTE, PRINCIPAL, PREFIXO)
                .isEqualTo(jackson(PRINCIPAL));
    }

    @Test
    void arquivoPrincipal_deveDesligarAOrdenacaoAlfabetica() {
        assertThat(jackson(PRINCIPAL)).containsEntry(PREFIXO + "mapper.sort-properties-alphabetically", "false");
    }

    private static Map<String, String> jackson(Path arquivo) {
        return ArquivoDePropriedades.lerComPrefixo(arquivo, PREFIXO);
    }
}
