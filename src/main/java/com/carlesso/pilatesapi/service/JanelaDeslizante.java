package com.carlesso.pilatesapi.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contador de eventos por chave em janela deslizante, mantido em memória.
 *
 * <p>Toda leitura e escrita de um deque acontece dentro do {@code compute} do mapa, que
 * serializa o acesso por chave: o {@link ArrayDeque} não é thread-safe e lê-lo fora dali
 * permitiria enxergar um tamanho desatualizado sob concorrência.
 */
final class JanelaDeslizante {

    private final int limite;
    private final Duration janela;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> eventos = new ConcurrentHashMap<>();

    JanelaDeslizante(int limite, Duration janela, Clock clock) {
        this.limite = limite;
        this.janela = janela;
        this.clock = clock;
    }

    int limite() {
        return limite;
    }

    Duration janela() {
        return janela;
    }

    /**
     * Registra um evento se a chave ainda estiver abaixo do limite. Checar e registrar numa
     * única operação atômica impede que uma rajada de requisições concorrentes passe toda
     * pela checagem antes de a primeira ser contada.
     *
     * @return {@code false} quando a chave já atingiu o limite (nada é registrado)
     */
    boolean tentarRegistrar(String chave) {
        AtomicBoolean registrado = new AtomicBoolean();
        Instant agora = clock.instant();
        eventos.compute(chave, (k, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            prunir(deque, agora);
            if (deque.size() < limite) {
                deque.addLast(agora);
                registrado.set(true);
            }
            return deque.isEmpty() ? null : deque;
        });
        return registrado.get();
    }

    /** Desfaz um registro feito por {@link #tentarRegistrar}, quando o evento não deve contar. */
    void desfazer(String chave) {
        eventos.computeIfPresent(chave, (k, deque) -> {
            deque.pollLast();
            return deque.isEmpty() ? null : deque;
        });
    }

    void remover(String chave) {
        eventos.remove(chave);
    }

    boolean atingiuLimite(String chave) {
        AtomicInteger tamanho = new AtomicInteger();
        Instant agora = clock.instant();
        eventos.computeIfPresent(chave, (k, deque) -> {
            prunir(deque, agora);
            tamanho.set(deque.size());
            return deque.isEmpty() ? null : deque;
        });
        return tamanho.get() >= limite;
    }

    /**
     * Remove as chaves cujos eventos já saíram da janela. Sem isso, chaves que nunca são
     * revisitadas ficariam retidas para sempre, permitindo crescimento ilimitado do mapa
     * em memória por um endpoint não autenticado.
     */
    void limparExpirados() {
        Instant agora = clock.instant();
        eventos.keySet()
                .forEach(chave -> eventos.computeIfPresent(chave, (k, deque) -> {
                    prunir(deque, agora);
                    return deque.isEmpty() ? null : deque;
                }));
    }

    boolean contem(String chave) {
        return eventos.containsKey(chave);
    }

    void limpar() {
        eventos.clear();
    }

    private void prunir(Deque<Instant> deque, Instant agora) {
        Instant corte = agora.minus(janela);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(corte)) {
            deque.pollFirst();
        }
    }
}
