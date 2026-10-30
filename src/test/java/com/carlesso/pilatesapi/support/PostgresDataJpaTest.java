package com.carlesso.pilatesapi.support;

import com.carlesso.pilatesapi.config.JpaAuditingConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * {@code @DataJpaTest} pré-configurado para rodar contra o PostgreSQL de
 * {@link PostgresTestcontainerSupport}: mantém o datasource do container em vez
 * de substituí-lo por um banco embarcado ({@code replace = NONE}).
 *
 * <p>As classes anotadas devem estender {@link PostgresTestcontainerSupport}.
 * Evita repetir o par {@code @DataJpaTest} + {@code @AutoConfigureTestDatabase}
 * em cada teste de repositório/integração.
 *
 * <p>Importa {@link JpaAuditingConfig}: o slice não carrega classes {@code @Configuration} da aplicação, e sem o
 * Spring Data Auditing as entidades auditáveis seriam gravadas sem {@code data_criacao}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
public @interface PostgresDataJpaTest {}
