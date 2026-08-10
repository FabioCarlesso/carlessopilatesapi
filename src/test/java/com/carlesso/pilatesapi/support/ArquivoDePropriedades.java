package com.carlesso.pilatesapi.support;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Lê um {@code .properties} do disco, para testes que comparam arquivos de configuração entre si.
 *
 * <p>A leitura é do disco, e não do classpath, porque o {@code application.properties} de teste
 * sombreia o principal: pelo classpath só o primeiro é alcançável, e é justamente a divergência
 * entre os dois que esses testes precisam enxergar.
 *
 * <p>Os caminhos são relativos ao diretório do módulo — o Maven roda os testes com esse diretório
 * como <em>working directory</em>.
 */
public final class ArquivoDePropriedades {

    private ArquivoDePropriedades() {}

    /** Todas as propriedades declaradas no arquivo. */
    public static Properties ler(Path arquivo) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + arquivo, e);
        }
        return properties;
    }

    /** Apenas as chaves iniciadas por {@code prefixo}, ordenadas, para comparação entre arquivos. */
    public static TreeMap<String, String> lerComPrefixo(Path arquivo, String prefixo) {
        Properties properties = ler(arquivo);
        TreeMap<String, String> filtradas = new TreeMap<>();
        properties.stringPropertyNames().stream()
                .filter(chave -> chave.startsWith(prefixo))
                .forEach(chave -> filtradas.put(chave, properties.getProperty(chave)));
        return filtradas;
    }
}
