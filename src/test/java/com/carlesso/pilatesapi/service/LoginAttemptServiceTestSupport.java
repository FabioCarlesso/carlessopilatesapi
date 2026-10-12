package com.carlesso.pilatesapi.service;

/**
 * Acesso de testes ao estado em memória do {@link LoginAttemptService}. O bean é
 * compartilhado entre as classes de teste que reutilizam o contexto Spring, então cada
 * teste que exercita o rate limit precisa começar com os baldes vazios.
 */
public final class LoginAttemptServiceTestSupport {

    private LoginAttemptServiceTestSupport() {}

    public static void limpar(LoginAttemptService service) {
        service.limparTudo();
    }
}
