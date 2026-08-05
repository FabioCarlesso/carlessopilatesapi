package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Guarda contra divergência silenciosa entre os dois {@code application.properties}.
 *
 * <p>O arquivo de teste sombreia o principal no classpath — é o primeiro
 * {@code classpath:/application.properties} encontrado que vence —, então a configuração
 * de compressão precisa estar declarada nos dois para o
 * {@code web.CompressaoRespostaIntegrationTest} exercitar o que roda em produção.
 *
 * <p>Sem esta verificação, alterar os mime-types apenas no arquivo principal deixaria
 * aquele teste verde validando configuração obsoleta — uma garantia falsa. Os arquivos
 * são lidos do disco (e não do classpath) justamente porque o sombreamento impede
 * alcançar os dois por recurso.
 */
class CompressaoPropriedadesTest {

    private static final String PREFIXO = "server.compression.";

    private static final Path PRINCIPAL = Path.of("src/main/resources/application.properties");
    private static final Path TESTE = Path.of("src/test/resources/application.properties");

    @Test
    void configuracaoDeCompressao_deveSerIdenticaNosDoisArquivos() throws IOException {
        assertThat(compressao(TESTE))
                .as("%s deve espelhar %s nas chaves %s*", TESTE, PRINCIPAL, PREFIXO)
                .isEqualTo(compressao(PRINCIPAL));
    }

    @Test
    void arquivoPrincipal_deveDeclararAsTresChavesDeCompressao() throws IOException {
        assertThat(compressao(PRINCIPAL))
                .containsOnlyKeys(PREFIXO + "enabled", PREFIXO + "mime-types", PREFIXO + "min-response-size");
    }

    private static TreeMap<String, String> compressao(Path arquivo) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        TreeMap<String, String> filtradas = new TreeMap<>();
        properties.stringPropertyNames().stream()
                .filter(chave -> chave.startsWith(PREFIXO))
                .forEach(chave -> filtradas.put(chave, properties.getProperty(chave)));
        return filtradas;
    }
}
