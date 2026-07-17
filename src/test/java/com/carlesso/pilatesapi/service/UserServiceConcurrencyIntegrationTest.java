package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.repository.UserRepository;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class UserServiceConcurrencyIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    private UserRepository repository;

    @Autowired
    private UserService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void inativarAdminsConcorrentes_deveManterUmAdminAtivo() throws Exception {
        User admin1 = repository.save(usuario("admin1@email.com"));
        User admin2 = repository.save(usuario("admin2@email.com"));

        CountDownLatch largada = new CountDownLatch(1);

        Callable<Throwable> inativarAdmin1 = () -> executarCapturandoErro(() -> {
            aguardarLargada(largada);
            service.inativar(admin1.getId(), "operador@email.com");
        });
        Callable<Throwable> inativarAdmin2 = () -> executarCapturandoErro(() -> {
            aguardarLargada(largada);
            service.inativar(admin2.getId(), "operador@email.com");
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var resultado1 = executor.submit(inativarAdmin1);
            var resultado2 = executor.submit(inativarAdmin2);
            largada.countDown();

            List<Throwable> erros = List.of(resultado1, resultado2).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            return e;
                        }
                    })
                    .toList();

            assertThat(erros).anyMatch(erro -> erro == null);
            assertThat(erros)
                    .anyMatch(erro -> erro instanceof BusinessException
                            && erro.getMessage().equals("Não é possível inativar o último administrador ativo"));
        }

        assertThat(repository.countByRoleAndAtivoTrue(Role.ADMIN)).isEqualTo(1);
    }

    private Throwable executarCapturandoErro(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable erro) {
            return erro;
        }
    }

    private void aguardarLargada(CountDownLatch largada) {
        try {
            assertThat(largada.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private User usuario(String email) {
        User user = new User();
        user.setName("Admin Teste");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("senha1234"));
        user.setRole(Role.ADMIN);
        user.setAtivo(true);
        return user;
    }
}
