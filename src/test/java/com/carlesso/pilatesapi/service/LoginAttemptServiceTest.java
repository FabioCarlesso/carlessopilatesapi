package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private static final int MAX_POR_IP = 8;
    private static final long JANELA_POR_IP_MINUTOS = 30;

    private final LoginAttemptService service = new LoginAttemptService(MAX_POR_IP, JANELA_POR_IP_MINUTOS);

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

    @Test
    void isIpBlocked_comMenosQueOLimitePorIp_retornaFalse() {
        for (int i = 0; i < MAX_POR_IP - 1; i++) {
            service.registerIpFailure("login:203.0.113.10");
        }

        assertThat(service.isIpBlocked("login:203.0.113.10")).isFalse();
    }

    @Test
    void isIpBlocked_comLimitePorIp_retornaTrue() {
        for (int i = 0; i < MAX_POR_IP; i++) {
            service.registerIpFailure("login:203.0.113.10");
        }

        assertThat(service.isIpBlocked("login:203.0.113.10")).isTrue();
        assertThat(service.isIpBlocked("login:203.0.113.11")).isFalse();
    }

    @Test
    void baldesPorEmailEPorIp_saoIndependentes() {
        for (int i = 0; i < MAX_POR_IP; i++) {
            service.registerIpFailure("compartilhada");
        }

        assertThat(service.isIpBlocked("compartilhada")).isTrue();
        assertThat(service.isBlocked("compartilhada")).isFalse();
    }

    @Test
    void registerSuccess_naoLimpaHistoricoPorIp() {
        for (int i = 0; i < MAX_POR_IP; i++) {
            service.registerIpFailure("login:203.0.113.10");
        }

        service.registerSuccess("login:203.0.113.10");

        assertThat(service.isIpBlocked("login:203.0.113.10")).isTrue();
    }

    @Test
    void isIpBlocked_usaJanelaPropriaDoBaldePorIp() {
        Deque<Instant> deque = new ArrayDeque<>();
        // Já fora da janela por e-mail (15 min), mas ainda dentro da janela por IP (30 min).
        Instant dentroDaJanelaPorIp =
                Instant.now().minus(LoginAttemptService.WINDOW).minusSeconds(60);
        for (int i = 0; i < MAX_POR_IP; i++) {
            deque.addLast(dentroDaJanelaPorIp);
        }
        mapa("ipHistory").put("login:203.0.113.10", deque);

        assertThat(service.isIpBlocked("login:203.0.113.10")).isTrue();
        assertThat(service.windowPorIp()).hasMinutes(JANELA_POR_IP_MINUTOS);
        assertThat(service.maxAttemptsPorIp()).isEqualTo(MAX_POR_IP);
    }

    @Test
    void limparEntradasExpiradas_tambemRemoveChavesPorIpExpiradas() {
        Deque<Instant> expirado = new ArrayDeque<>();
        expirado.addLast(Instant.now().minus(service.windowPorIp()).minusSeconds(1));
        mapa("ipHistory").put("login:203.0.113.10", expirado);
        service.registerIpFailure("login:203.0.113.11");

        service.limparEntradasExpiradas();

        assertThat(mapa("ipHistory")).doesNotContainKey("login:203.0.113.10").containsKey("login:203.0.113.11");
    }

    private Deque<Instant> dequeComInstanteExpirado() {
        Deque<Instant> deque = new ArrayDeque<>();
        deque.addLast(Instant.now().minus(LoginAttemptService.WINDOW).minusSeconds(1));
        return deque;
    }

    private ConcurrentHashMap<String, Deque<Instant>> history() {
        return mapa("history");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Deque<Instant>> mapa(String campo) {
        try {
            Field field = LoginAttemptService.class.getDeclaredField(campo);
            field.setAccessible(true);
            return (ConcurrentHashMap<String, Deque<Instant>>) field.get(service);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
