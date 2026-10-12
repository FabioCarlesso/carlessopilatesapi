package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private static final int MAX_POR_IP = 8;
    private static final long JANELA_POR_IP_MINUTOS = 30;

    private final RelogioAjustavel clock = new RelogioAjustavel();
    private final LoginAttemptService service = new LoginAttemptService(MAX_POR_IP, JANELA_POR_IP_MINUTOS, clock);

    @Test
    void isBlocked_semTentativas_retornaFalse() {
        assertThat(service.isBlocked("novo@email.com")).isFalse();
    }

    @Test
    void tryAcquire_abaixoDoLimite_registraEPermite() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            assertThat(service.tryAcquire("quase@email.com")).isTrue();
        }

        assertThat(service.isBlocked("quase@email.com")).isFalse();
    }

    @Test
    void tryAcquire_noLimite_bloqueiaSemRegistrarAlemDoLimite() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            assertThat(service.tryAcquire("bloqueado@email.com")).isTrue();
        }

        assertThat(service.isBlocked("bloqueado@email.com")).isTrue();
        assertThat(service.tryAcquire("bloqueado@email.com")).isFalse();

        // Tentativas recusadas não estendem o bloqueio: ao sair da janela, libera.
        clock.avancar(LoginAttemptService.WINDOW.plusSeconds(1));
        assertThat(service.tryAcquire("bloqueado@email.com")).isTrue();
    }

    @Test
    void registerSuccess_limpaHistoricoDaChave() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.tryAcquire("recupera@email.com");
        }

        service.registerSuccess("recupera@email.com");

        assertThat(service.isBlocked("recupera@email.com")).isFalse();
        assertThat(service.contemChave("recupera@email.com")).isFalse();
    }

    @Test
    void isBlocked_removeChaveDoMapaQuandoTentativasJaExpiraram() {
        service.tryAcquire("expirado@email.com");
        clock.avancar(LoginAttemptService.WINDOW.plusSeconds(1));

        assertThat(service.isBlocked("expirado@email.com")).isFalse();
        assertThat(service.contemChave("expirado@email.com")).isFalse();
    }

    @Test
    void limparEntradasExpiradas_removeChavesComTentativasExpiradasEMantemAsRecentes() {
        service.tryAcquire("expirado@email.com");
        clock.avancar(LoginAttemptService.WINDOW.plusSeconds(1));
        service.tryAcquire("recente@email.com");

        service.limparEntradasExpiradas();

        assertThat(service.contemChave("expirado@email.com")).isFalse();
        assertThat(service.contemChave("recente@email.com")).isTrue();
    }

    @Test
    void tryAcquireIp_respeitaLimitePorOrigem() {
        for (int i = 0; i < MAX_POR_IP; i++) {
            assertThat(service.tryAcquireIp("login:203.0.113.10")).isTrue();
        }

        assertThat(service.isIpBlocked("login:203.0.113.10")).isTrue();
        assertThat(service.tryAcquireIp("login:203.0.113.10")).isFalse();
        assertThat(service.isIpBlocked("login:203.0.113.11")).isFalse();
    }

    @Test
    void baldesPorEmailEPorIp_saoIndependentes() {
        for (int i = 0; i < MAX_POR_IP; i++) {
            service.tryAcquireIp("compartilhada");
        }

        assertThat(service.isIpBlocked("compartilhada")).isTrue();
        assertThat(service.isBlocked("compartilhada")).isFalse();
    }

    @Test
    void releaseIp_devolveApenasAReservaDaTentativa() {
        for (int i = 0; i < MAX_POR_IP; i++) {
            service.tryAcquireIp("login:203.0.113.10");
        }

        service.releaseIp("login:203.0.113.10");

        assertThat(service.isIpBlocked("login:203.0.113.10")).isFalse();
        assertThat(service.tryAcquireIp("login:203.0.113.10")).isTrue();
        assertThat(service.isIpBlocked("login:203.0.113.10")).isTrue();
    }

    @Test
    void registerSuccess_naoLimpaHistoricoPorIp() {
        for (int i = 0; i < MAX_POR_IP; i++) {
            service.tryAcquireIp("login:203.0.113.10");
        }

        service.registerSuccess("login:203.0.113.10");

        assertThat(service.isIpBlocked("login:203.0.113.10")).isTrue();
    }

    @Test
    void isIpBlocked_usaJanelaPropriaDoBaldePorIp() {
        for (int i = 0; i < MAX_POR_IP; i++) {
            service.tryAcquireIp("login:203.0.113.10");
        }

        // Já fora da janela por e-mail (15 min), mas ainda dentro da janela por IP (30 min).
        clock.avancar(LoginAttemptService.WINDOW.plusMinutes(1));
        assertThat(service.isIpBlocked("login:203.0.113.10")).isTrue();

        clock.avancar(Duration.ofMinutes(JANELA_POR_IP_MINUTOS));
        assertThat(service.isIpBlocked("login:203.0.113.10")).isFalse();
        assertThat(service.windowPorIp()).hasMinutes(JANELA_POR_IP_MINUTOS);
        assertThat(service.maxAttemptsPorIp()).isEqualTo(MAX_POR_IP);
    }

    @Test
    void limparEntradasExpiradas_tambemRemoveChavesPorIpExpiradas() {
        service.tryAcquireIp("login:203.0.113.10");
        clock.avancar(service.windowPorIp().plusSeconds(1));
        service.tryAcquireIp("login:203.0.113.11");

        service.limparEntradasExpiradas();

        assertThat(service.contemChaveIp("login:203.0.113.10")).isFalse();
        assertThat(service.contemChaveIp("login:203.0.113.11")).isTrue();
    }

    @Test
    void tryAcquireIp_sobConcorrencia_naoDeixaPassarMaisQueOLimite() throws Exception {
        int requisicoes = 200;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Callable<Boolean>> tarefas = new ArrayList<>();
            for (int i = 0; i < requisicoes; i++) {
                tarefas.add(() -> {
                    largada.await();
                    return service.tryAcquireIp("login:203.0.113.50");
                });
            }
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (Callable<Boolean> tarefa : tarefas) {
                resultados.add(pool.submit(tarefa));
            }
            largada.countDown();
            long permitidas = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) permitidas++;
            }

            assertThat(permitidas).isEqualTo(MAX_POR_IP);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void construtor_rejeitaConfiguracaoInvalida() {
        assertThatThrownBy(() -> new LoginAttemptService(0, 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-tentativas");
        assertThatThrownBy(() -> new LoginAttemptService(20, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("janela-minutos");
        assertThatThrownBy(() -> new LoginAttemptService(20, -5)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class RelogioAjustavel extends Clock {

        private Instant agora = Instant.parse("2026-01-01T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
