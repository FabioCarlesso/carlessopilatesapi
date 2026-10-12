package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.support.ArquivoDePropriedades;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * O formato da página ({@code {content, page:{...}}}) vem de {@code spring.data.web.pageable.serialization-mode}, e não
 * mais de um {@code @EnableSpringDataWebSupport} — a anotação desligava a autoconfiguração do Boot e, com ela, o
 * {@code default-page-size}. Como o arquivo de teste sombreia o principal, a chave precisa estar nos dois: sem ela no
 * principal, produção voltaria ao formato antigo da {@code PageImpl} com os testes verdes. Mesmo raciocínio do
 * {@link CompressaoPropriedadesTest}.
 */
class PaginacaoPropriedadesTest {

    private static final String CHAVE = "spring.data.web.pageable.serialization-mode";

    @Test
    void modoDeSerializacao_deveSerViaDtoNosDoisArquivos() {
        assertThat(ArquivoDePropriedades.lerComPrefixo(Path.of("src/main/resources/application.properties"), CHAVE))
                .containsEntry(CHAVE, "via-dto");
        assertThat(ArquivoDePropriedades.lerComPrefixo(Path.of("src/test/resources/application.properties"), CHAVE))
                .containsEntry(CHAVE, "via-dto");
    }
}
