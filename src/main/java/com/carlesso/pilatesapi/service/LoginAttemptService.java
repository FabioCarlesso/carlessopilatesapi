package com.carlesso.pilatesapi.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Rate limit em memória dos endpoints públicos de autenticação, em dois baldes
 * independentes: um por chave (e-mail) e outro por IP de origem. O balde por IP
 * pega quem varia o e-mail a cada tentativa (credential stuffing); por isso tem
 * janela e limite próprios, mais folgados, para não punir vários usuários legítimos
 * atrás do mesmo NAT.
 *
 * <p>O estado vive no heap desta instância: zera a cada restart e não é
 * compartilhado entre réplicas.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final int maxAttemptsPorIp;
    private final Duration windowPorIp;

    private final ConcurrentHashMap<String, Deque<Instant>> history = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> ipHistory = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.rate-limit.ip.max-tentativas:20}") int maxAttemptsPorIp,
            @Value("${app.rate-limit.ip.janela-minutos:15}") long janelaPorIpMinutos) {
        this.maxAttemptsPorIp = maxAttemptsPorIp;
        this.windowPorIp = Duration.ofMinutes(janelaPorIpMinutos);
    }

    public void registerFailure(String key) {
        registrar(history, key);
    }

    public void registerSuccess(String key) {
        history.remove(key);
    }

    public boolean isBlocked(String key) {
        Deque<Instant> deque = prunirExpirados(history, key, WINDOW);
        return deque != null && deque.size() >= MAX_ATTEMPTS;
    }

    /**
     * Não há contrapartida de sucesso de propósito: se um login bem-sucedido zerasse o
     * balde do IP, o atacante intercalaria logins na própria conta para nunca atingir o limite.
     */
    public void registerIpFailure(String key) {
        registrar(ipHistory, key);
    }

    public boolean isIpBlocked(String key) {
        Deque<Instant> deque = prunirExpirados(ipHistory, key, windowPorIp);
        return deque != null && deque.size() >= maxAttemptsPorIp;
    }

    public int maxAttemptsPorIp() {
        return maxAttemptsPorIp;
    }

    public Duration windowPorIp() {
        return windowPorIp;
    }

    /**
     * Remove do mapa as chaves cujas tentativas já saíram da janela. Sem isso,
     * chaves que nunca são revisitadas (ex.: um e-mail de recuperação de senha
     * usado uma única vez) ficariam retidas para sempre, permitindo crescimento
     * ilimitado do mapa em memória por um endpoint não autenticado.
     */
    public void limparEntradasExpiradas() {
        history.keySet().forEach(key -> prunirExpirados(history, key, WINDOW));
        ipHistory.keySet().forEach(key -> prunirExpirados(ipHistory, key, windowPorIp));
    }

    private static void registrar(ConcurrentHashMap<String, Deque<Instant>> mapa, String key) {
        mapa.compute(key, (k, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            deque.addLast(Instant.now());
            return deque;
        });
    }

    private static Deque<Instant> prunirExpirados(
            ConcurrentHashMap<String, Deque<Instant>> mapa, String key, Duration window) {
        Instant cutoff = Instant.now().minus(window);
        return mapa.computeIfPresent(key, (k, deque) -> {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            return deque.isEmpty() ? null : deque;
        });
    }
}
