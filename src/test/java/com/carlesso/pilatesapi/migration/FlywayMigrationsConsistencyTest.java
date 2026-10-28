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
 *
 * <p>O perfil {@code dev} lê {@code db/migration} e {@code db/seed} no mesmo
 * espaço de versões, e nenhum teste sobe o Flyway com as seeds: uma seed que
 * reaproveite o número de uma migration estrutural só quebraria o
 * {@code docker compose} de desenvolvimento. Por isso as duas pastas também são
 * verificadas juntas.
 */
class FlywayMigrationsConsistencyTest {

    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(?<version>[0-9]+(?:[._][0-9]+)*)__.+\\.sql$");

    private List<String> migrationFileNames() throws Exception {
        return fileNames("db/migration");
    }

    private List<String> fileNames(String pasta) throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:" + pasta + "/*.sql");
        List<String> names = new ArrayList<>();
        for (Resource resource : resources) {
            names.add(resource.getFilename());
        }
        return names;
    }

    @Test
    void naoDeveHaverVersoesDuplicadasNasMigrations() throws Exception {
        assertSemVersoesDuplicadas(migrationFileNames());
    }

    @Test
    void naoDeveHaverVersoesDuplicadasEntreMigrationsESeedsDoPerfilDev() throws Exception {
        List<String> perfilDev = new ArrayList<>(migrationFileNames());
        perfilDev.addAll(fileNames("db/seed"));

        assertSemVersoesDuplicadas(perfilDev);
    }

    private void assertSemVersoesDuplicadas(List<String> fileNames) {
        Map<String, List<String>> arquivosPorVersao = new HashMap<>();

        for (String fileName : fileNames) {
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
