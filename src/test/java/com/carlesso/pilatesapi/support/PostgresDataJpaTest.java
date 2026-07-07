package com.carlesso.pilatesapi.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * {@code @DataJpaTest} pré-configurado para rodar contra o PostgreSQL de
 * {@link PostgresTestcontainerSupport}: mantém o datasource do container em vez
 * de substituí-lo por um banco embarcado ({@code replace = NONE}).
 *
 * <p>As classes anotadas devem estender {@link PostgresTestcontainerSupport}.
 * Evita repetir o par {@code @DataJpaTest} + {@code @AutoConfigureTestDatabase}
 * em cada teste de repositório/integração.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public @interface PostgresDataJpaTest {
}
