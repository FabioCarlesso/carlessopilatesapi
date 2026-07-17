package com.carlesso.pilatesapi.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para testes que precisam exercitar o schema real da aplicação.
 *
 * <p>Sobe um PostgreSQL 16 via Testcontainers e deixa o Flyway criar o schema —
 * igual à produção — em vez do H2 com {@code ddl-auto=create-drop} usado pelos
 * demais testes. Assim as migrations são efetivamente exercitadas e recursos
 * específicos do Postgres (ex.: o índice parcial de unicidade da V23) passam a
 * ser cobertos.
 *
 * <p>O container é um <em>singleton</em>: iniciado uma única vez no primeiro uso
 * e reaproveitado por toda a suíte (o Testcontainers/Ryuk o encerra ao final da
 * JVM), evitando subir um container por classe. As classes que herdam desta
 * substituem, via {@link DynamicPropertySource}, a configuração de datasource e
 * JPA definida em {@code src/test/resources/application.properties}.
 *
 * <p>Testes de <em>slice</em> ({@code @DataJpaTest}) precisam adicionar
 * {@code @AutoConfigureTestDatabase(replace = Replace.NONE)} para não trocarem o
 * datasource do container por um embarcado.
 */
public abstract class PostgresTestcontainerSupport {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }
}
