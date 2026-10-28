package com.sentinel.audit.mcp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Limite de chamadas por janela deslizante de um minuto, em memória. Serve a duas coisas: chamadas com chave válida
 * (por sistema) e tentativas com chave errada (por nome informado), para que adivinhar uma chave seja impraticável.
 * Sem banco: reiniciar a API zera a contagem, o que é aceitável para um limite de abuso.
 */
public final class McpRateLimiter {

    private static final long MILLIS_PER_SECOND = 1000;


    static final long WINDOW_MS = 60_000L;
    static final int MAX_KEYS = 10_000;

    private final int limit;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public McpRateLimiter(int limitPerMinute, LongSupplier clockMillis) {
        this.limit = Math.max(1, limitPerMinute);
        this.clock = clockMillis;
    }

    public McpRateLimiter(int limitPerMinute) {
        this(limitPerMinute, System::currentTimeMillis);
    }

    /** Registra a tentativa e diz se passa. Tentativa recusada não prolonga o bloqueio. */
    public synchronized boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        if (hits.size() >= MAX_KEYS && !hits.containsKey(key)) {
            hits.values().removeIf(q -> q.isEmpty() || now - q.peekLast() >= WINDOW_MS);
            if (hits.size() >= MAX_KEYS) {
                return false;
            }
        }
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) {
            q.pollFirst();
        }
        if (q.size() >= limit) {
            return false;
        }
        q.addLast(now);
        return true;
    }

    /** Já atingiu o limite? Só consulta, não registra. */
    public synchronized boolean blocked(String key) {
        Deque<Long> q = hits.get(key);
        if (q == null) {
            return false;
        }
        long now = clock.getAsLong();
        while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) {
            q.pollFirst();
        }
        return q.size() >= limit;
    }

    /** Segundos até a janela abrir de novo para a chave. */
    public synchronized int retryAfterSeconds(String key) {
        Deque<Long> q = hits.get(key);
        if (q == null || q.isEmpty()) {
            return 1;
        }
        long wait = WINDOW_MS - (clock.getAsLong() - q.peekFirst());
        return (int) Math.max(1, (wait + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND);
    }
}
