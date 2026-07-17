package com.carlesso.pilatesapi.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Garante a consistência das migrations versionadas do Flyway em
 * {@code classpath:db/migration}. A suíte roda em H2 com Flyway desabilitado,
 * então uma colisão de versão (dois arquivos {@code V<n>__...}) só apareceria
 * ao subir a aplicação ou no job de CI. Este teste antecipa esse erro.
 */
class FlywayMigrationsConsistencyTest {

    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(?<version>[0-9]+(?:[._][0-9]+)*)__.+\\.sql$");

    private List<String> migrationFileNames() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*.sql");
        List<String> names = new ArrayList<>();
        for (Resource resource : resources) {
            names.add(resource.getFilename());
        }
        return names;
    }

    @Test
    void naoDeveHaverVersoesDuplicadasNasMigrations() throws Exception {
        Map<String, List<String>> arquivosPorVersao = new HashMap<>();

        for (String fileName : migrationFileNames()) {
            Matcher matcher = VERSIONED_MIGRATION.matcher(fileName);
            if (matcher.matches()) {
                String versao = matcher.group("version").replace('_', '.');
                arquivosPorVersao
                        .computeIfAbsent(versao, v -> new ArrayList<>())
                        .add(fileName);
            }
        }

        List<Map.Entry<String, List<String>>> duplicadas = arquivosPorVersao.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .toList();

        assertThat(duplicadas)
                .as("Nenhuma versão de migration pode ser usada por mais de um arquivo: %s", duplicadas)
                .isEmpty();
    }

    @Test
    void deveEncontrarAsMigrationsVersionadas() throws Exception {
        long versionadas = migrationFileNames().stream()
                .filter(name -> VERSIONED_MIGRATION.matcher(name).matches())
                .count();

        assertThat(versionadas)
                .as("Esperado encontrar migrations versionadas em db/migration")
                .isGreaterThan(0);
    }
}
