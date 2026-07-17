package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InitialAdminBootstrapTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void run_comAdminAtivo_naoCriaNovoAdmin() throws Exception {
        var bootstrap = bootstrap("admin@carlessopilates.com", "senha-segura");
        when(repository.countByRoleAndAtivoTrue(Role.ADMIN)).thenReturn(1L);

        bootstrap.run(new DefaultApplicationArguments());

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void run_semAdminAtivoEComSenha_criaAdminInicial() throws Exception {
        var bootstrap = bootstrap("ADMIN@CARLESSOPILATES.COM", "senha-segura");
        when(repository.countByRoleAndAtivoTrue(Role.ADMIN)).thenReturn(0L);
        when(repository.existsByEmail("admin@carlessopilates.com")).thenReturn(false);
        when(passwordEncoder.encode("senha-segura")).thenReturn("hash");

        bootstrap.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.getName()).isEqualTo("Administrador");
        assertThat(admin.getEmail()).isEqualTo("admin@carlessopilates.com");
        assertThat(admin.getPassword()).isEqualTo("hash");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isAtivo()).isTrue();
    }

    @Test
    void run_semAdminAtivoESemSenha_falhaStartup() {
        var bootstrap = bootstrap("admin@carlessopilates.com", "");
        when(repository.countByRoleAndAtivoTrue(Role.ADMIN)).thenReturn(0L);

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_INITIAL_ADMIN_PASSWORD");

        verify(passwordEncoder, never()).encode(anyString());
    }

    private InitialAdminBootstrap bootstrap(String email, String password) {
        var properties = new InitialAdminProperties(email, password);
        return new InitialAdminBootstrap(repository, passwordEncoder, properties);
    }
}
