package com.carlesso.pilatesapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class UsuarioAuditorAwareTest {

    private final UsuarioAuditorAware auditorAware = new UsuarioAuditorAware();

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void comUsuarioAutenticado_deveRetornarEmail() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "fisio@carlessopilates.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThat(auditorAware.getCurrentAuditor()).contains("fisio@carlessopilates.com");
    }

    @Test
    void semAutenticacao_deveRetornarSystem() {
        assertThat(auditorAware.getCurrentAuditor()).contains(UsuarioAuditorAware.SISTEMA);
    }

    @Test
    void comAutenticacaoAnonima_deveRetornarSystem() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "chave", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(auditorAware.getCurrentAuditor()).contains(UsuarioAuditorAware.SISTEMA);
    }

    @Test
    void comAutenticacaoNaoConcluida_deveRetornarSystem() {
        // Construtor sem authorities: token ainda não autenticado (ex.: credenciais submetidas ao login).
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("fisio@carlessopilates.com", "senha"));

        assertThat(auditorAware.getCurrentAuditor()).contains(UsuarioAuditorAware.SISTEMA);
    }
}
