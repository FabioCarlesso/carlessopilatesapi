package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.repository.UserRepository;
import com.carlesso.pilatesapi.util.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
@Profile("prod")
public class InitialAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    private static final String DEFAULT_ADMIN_EMAIL = "admin@carlessopilates.com";

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final InitialAdminProperties initialAdminProperties;

    public InitialAdminBootstrap(UserRepository repository,
                                 PasswordEncoder passwordEncoder,
                                 InitialAdminProperties initialAdminProperties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.initialAdminProperties = initialAdminProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.countByRoleAndAtivoTrue(Role.ADMIN) > 0) {
            return;
        }

        log.info("Nenhum administrador ativo encontrado; criando admin inicial");

        String email = initialAdminEmail();
        String password = initialAdminPassword();
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("APP_INITIAL_ADMIN_PASSWORD deve ser configurada para criar o admin inicial em produção");
        }
        if (password.length() < 8) {
            throw new IllegalStateException("APP_INITIAL_ADMIN_PASSWORD deve ter pelo menos 8 caracteres");
        }
        if (repository.existsByEmail(email)) {
            throw new IllegalStateException("Já existe usuário com o e-mail do admin inicial, mas nenhum ADMIN ativo");
        }

        User admin = new User();
        admin.setName("Administrador");
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setAtivo(true);

        repository.save(admin);
        log.info("Admin inicial criado: email={}", LogMasker.email(email));
    }

    private String initialAdminEmail() {
        if (initialAdminProperties == null || !StringUtils.hasText(initialAdminProperties.email())) {
            return DEFAULT_ADMIN_EMAIL;
        }
        return initialAdminProperties.email().toLowerCase(Locale.ROOT);
    }

    private String initialAdminPassword() {
        return initialAdminProperties == null ? null : initialAdminProperties.password();
    }
}
