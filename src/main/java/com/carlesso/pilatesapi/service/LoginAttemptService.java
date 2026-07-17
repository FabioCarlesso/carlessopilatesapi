package com.carlesso.pilatesapi.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Deque<Instant>> history = new ConcurrentHashMap<>();

    public void registerFailure(String key) {
        history.compute(key, (k, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            deque.addLast(Instant.now());
            return deque;
        });
    }

    public void registerSuccess(String key) {
        history.remove(key);
    }

    public boolean isBlocked(String key) {
        Deque<Instant> deque = prunirExpirados(key);
        return deque != null && deque.size() >= MAX_ATTEMPTS;
    }

    /**
     * Remove do mapa as chaves cujas tentativas já saíram da janela. Sem isso,
     * chaves que nunca são revisitadas (ex.: um e-mail de recuperação de senha
     * usado uma única vez) ficariam retidas para sempre, permitindo crescimento
     * ilimitado do mapa em memória por um endpoint não autenticado.
     */
    public void limparEntradasExpiradas() {
        history.keySet().forEach(this::prunirExpirados);
    }

    private Deque<Instant> prunirExpirados(String key) {
        Instant cutoff = Instant.now().minus(WINDOW);
        return history.computeIfPresent(key, (k, deque) -> {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            return deque.isEmpty() ? null : deque;
        });
    }
}
