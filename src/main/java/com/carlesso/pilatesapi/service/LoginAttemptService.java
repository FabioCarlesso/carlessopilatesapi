package com.carlesso.pilatesapi.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

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
        Deque<Instant> deque = history.get(key);
        if (deque == null) return false;
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
        return deque.size() >= MAX_ATTEMPTS;
    }
}
