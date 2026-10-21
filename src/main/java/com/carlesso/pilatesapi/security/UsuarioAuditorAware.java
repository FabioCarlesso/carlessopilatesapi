package com.carlesso.pilatesapi.security;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolve o autor gravado em {@code criadoPor}/{@code alteradoPor}: o e-mail do usuário autenticado — o principal
 * que o {@link JwtAuthenticationFilter} coloca no {@code SecurityContext} — ou {@value #SISTEMA} quando não há
 * usuário, como nos schedulers.
 */
public class UsuarioAuditorAware implements AuditorAware<String> {

    public static final String SISTEMA = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of(SISTEMA);
        }
        return Optional.of(authentication.getName());
    }
}
