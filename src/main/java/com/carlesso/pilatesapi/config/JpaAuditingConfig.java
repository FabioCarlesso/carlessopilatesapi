package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.security.UsuarioAuditorAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita o Spring Data Auditing das entidades que estendem {@code EntidadeAuditavel}.
 *
 * <p>Fica fora de {@code PilatesApiApplication} para não ser carregada pelos testes {@code @WebMvcTest}, que não têm
 * contexto JPA. {@code modifyOnCreate = false} preserva o contrato anterior: {@code dataAtualizacao} só é preenchida
 * na primeira alteração.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", modifyOnCreate = false)
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return new UsuarioAuditorAware();
    }
}
