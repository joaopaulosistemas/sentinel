package com.sentinel.audit.service;

import com.sentinel.audit.dto.AuditProgressDto;
import com.sentinel.audit.dto.AuditProgressDto.AnalyzerProgress;
import com.sentinel.audit.dto.AuditProgressDto.Event;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda em memória o andamento das auditorias em execução, para a tela mostrar o que está acontecendo.
 * <p>
 * É propositalmente leve: sem tabela nova e sem migração. Se a API reiniciar, o andamento se perde, mas a
 * auditoria interrompida também (ela nunca sairia de RUNNING de qualquer forma). Tudo aqui é auxiliar: nada que
 * dê errado no registro do andamento pode afetar a auditoria.
 */
@ApplicationScoped
public class AuditProgressTracker {

    public static final String FILA = "FILA";
    public static final String CLONE = "CLONE";
    public static final String ANALISE = "ANALISE";
    public static final String PONTUACAO = "PONTUACAO";
    public static final String RELATORIO = "RELATORIO";

    private static final int MAX_EVENTS = 60;

    private final Map<Long, State> states = new ConcurrentHashMap<>();

    private static final class State {
        String phase = FILA;
        final Map<String, AnalyzerProgress> analyzers = new LinkedHashMap<>();
        final List<Event> events = new ArrayList<>();
    }

    public void queued(Long auditId) {
        if (auditId == null) return;
        State s = states.computeIfAbsent(auditId, id -> new State());
        synchronized (s) {
            log(s, "Auditoria na fila");
        }
    }

    public void phase(Long auditId, String phase, String message) {
        State s = state(auditId);
        if (s == null) return;
        synchronized (s) {
            s.phase = phase;
            log(s, message);
        }
    }

    /** Registra os analisadores previstos (já com os que não vão rodar), na ordem em que aparecem na tela. */
    public void planned(Long auditId, List<AnalyzerProgress> initial) {
        State s = state(auditId);
        if (s == null) return;
        synchronized (s) {
            // Um analisador rápido pode já ter começado ou terminado: o estado mais recente vence, a ordem é a do plano.
            Map<String, AnalyzerProgress> ordered = new LinkedHashMap<>();
            for (AnalyzerProgress a : initial) {
                AnalyzerProgress current = s.analyzers.get(a.name());
                ordered.put(a.name(), current != null ? current : a);
            }
            s.analyzers.clear();
            s.analyzers.putAll(ordered);
            log(s, initial.stream().filter(a -> "ESPERANDO".equals(a.status())).count()
                    + " analisadores vão rodar em paralelo");
        }
    }

    public void analyzerStarted(Long auditId, String name) {
        update(auditId, name, "EXECUTANDO", 0, 0, null, null);
    }

    public void analyzerFinished(Long auditId, String name, String status, int findings, long durationMs, String note) {
        String msg = "FALHOU".equals(status)
                ? name + " falhou" + (note == null ? "" : ": " + note)
                : name + " concluído: " + findings + (findings == 1 ? " achado" : " achados");
        update(auditId, name, status, findings, durationMs, note, msg);
    }

    /** A auditoria terminou (com sucesso ou não): o andamento deixa de existir. */
    public void finish(Long auditId) {
        if (auditId != null) states.remove(auditId);
    }

    public Optional<AuditProgressDto> snapshot(Long auditId) {
        State s = state(auditId);
        if (s == null) return Optional.empty();
        synchronized (s) {
            return Optional.of(new AuditProgressDto(s.phase, List.copyOf(s.analyzers.values()), List.copyOf(s.events)));
        }
    }

    private void update(Long auditId, String name, String status, int findings, long durationMs, String note, String message) {
        State s = state(auditId);
        if (s == null) return;
        synchronized (s) {
            s.analyzers.put(name, new AnalyzerProgress(name, status, findings, durationMs, note));
            if (message != null) log(s, message);
        }
    }

    private State state(Long auditId) {
        return auditId == null ? null : states.get(auditId);
    }

    private static void log(State s, String message) {
        if (s.events.size() >= MAX_EVENTS) s.events.remove(0);
        s.events.add(new Event(LocalDateTime.now(), message));
    }
}
