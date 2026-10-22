package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.security.UsuarioAuditorAware;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita o Spring Data Auditing das entidades que estendem {@code EntidadeAuditavel}.
 *
 * <p>Fica fora de {@code PilatesApiApplication} para não ser carregada pelos testes {@code @WebMvcTest}, que não têm
 * contexto JPA. {@code modifyOnCreate = false} preserva o contrato anterior: {@code dataAtualizacao} só é preenchida
 * na primeira alteração.
 *
 * <p>Os timestamps saem no fuso {@code app.fuso-horario}, não no da JVM: o container roda em UTC e o frontend exibe
 * a hora como veio, como já acontece com {@code registradoEm} dos contatos de cobrança.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "horarioDoEstudio", modifyOnCreate = false)
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return new UsuarioAuditorAware();
    }

    @Bean
    public DateTimeProvider horarioDoEstudio(@Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        ZoneId fuso = ZoneId.of(fusoHorario);
        return () -> Optional.of(LocalDateTime.now(fuso));
    }
}
