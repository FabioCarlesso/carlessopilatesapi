package com.carlesso.pilatesapi.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private final LoginAttemptService service = new LoginAttemptService();

    @Test
    void isBlocked_semTentativas_retornaFalse() {
        assertThat(service.isBlocked("novo@email.com")).isFalse();
    }

    @Test
    void isBlocked_comMenosQueMaxAttempts_retornaFalse() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            service.registerFailure("quase@email.com");
        }

        assertThat(service.isBlocked("quase@email.com")).isFalse();
    }

    @Test
    void isBlocked_comMaxAttempts_retornaTrue() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.registerFailure("bloqueado@email.com");
        }

        assertThat(service.isBlocked("bloqueado@email.com")).isTrue();
    }

    @Test
    void registerSuccess_limpaHistoricoDaChave() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.registerFailure("recupera@email.com");
        }

        service.registerSuccess("recupera@email.com");

        assertThat(service.isBlocked("recupera@email.com")).isFalse();
        assertThat(history()).doesNotContainKey("recupera@email.com");
    }

    @Test
    void isBlocked_removeChaveDoMapaQuandoTentativasJaExpiraram() {
        history().put("expirado@email.com", dequeComInstanteExpirado());

        boolean bloqueado = service.isBlocked("expirado@email.com");

        assertThat(bloqueado).isFalse();
        assertThat(history()).doesNotContainKey("expirado@email.com");
    }

    @Test
    void limparEntradasExpiradas_removeChavesComTentativasExpiradasEMantemAsRecentes() {
        history().put("expirado@email.com", dequeComInstanteExpirado());
        service.registerFailure("recente@email.com");

        service.limparEntradasExpiradas();

        assertThat(history()).doesNotContainKey("expirado@email.com").containsKey("recente@email.com");
    }

    private Deque<Instant> dequeComInstanteExpirado() {
        Deque<Instant> deque = new ArrayDeque<>();
        deque.addLast(Instant.now().minus(LoginAttemptService.WINDOW).minusSeconds(1));
        return deque;
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Deque<Instant>> history() {
        try {
            Field field = LoginAttemptService.class.getDeclaredField("history");
            field.setAccessible(true);
            return (ConcurrentHashMap<String, Deque<Instant>>) field.get(service);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
