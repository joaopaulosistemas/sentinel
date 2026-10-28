package com.sentinel.audit.service;

import com.sentinel.audit.dto.AuditProgressDto;
import com.sentinel.audit.dto.AuditProgressDto.AnalyzerProgress;
import com.sentinel.audit.dto.AuditProgressDto.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda o andamento das auditorias em execução, para a tela mostrar o que está acontecendo.
 * <p>
 * A réplica que executa a auditoria mantém o estado em memória e o espelha no banco ({@link ProgressStore}); as
 * outras réplicas leem o espelho, então a tela mostra o andamento e o botão de cancelar funciona qualquer que
 * seja a réplica que atendeu a requisição. O cancelamento é lido do status da auditoria no banco. Sem o
 * {@code ProgressStore} (testes unitários) tudo funciona só em memória. Tudo aqui é auxiliar: nada que dê errado
 * no registro do andamento pode afetar a auditoria.
 */
@ApplicationScoped
public class AuditProgressTracker {

    public static final String FILA = "FILA";
    public static final String CLONE = "CLONE";
    public static final String ANALISE = "ANALISE";
    public static final String PONTUACAO = "PONTUACAO";
    public static final String RELATORIO = "RELATORIO";

    private static final int MAX_EVENTS = 60;
    private static final Logger LOG = Logger.getLogger(AuditProgressTracker.class);
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    /** Intervalo mínimo entre consultas do cancelamento ao banco e entre batimentos. */
    private static final long CHECK_MILLIS = 1000L;
    private static final long HEARTBEAT_MILLIS = 15_000L;

    /** Nulo quando o tracker é criado fora do CDI (testes): então só há memória. */
    @Inject
    ProgressStore store;

    private final Map<Long, Long> lastCancelCheck = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastBeat = new ConcurrentHashMap<>();

    private final Map<Long, State> states = new ConcurrentHashMap<>();
    /** Auditorias que o usuário mandou cancelar; a orquestração consulta nos pontos de parada. */
    private final java.util.Set<Long> cancelRequested = ConcurrentHashMap.newKeySet();

    private static final class State {
        String phase = FILA;
        final Map<String, AnalyzerProgress> analyzers = new LinkedHashMap<>();
        final List<Event> events = new ArrayList<>();
        String repoUrl;
        long filesRead;
        long linesOfCode;
        /** Achados por severidade de cada analisador concluído; a soma é o resultado parcial. */
        final Map<String, Map<String, Integer>> severities = new LinkedHashMap<>();
    }

    public void queued(Long auditId) {
        if (auditId == null) return;
        State s = states.computeIfAbsent(auditId, id -> new State());
        synchronized (s) {
            log(s, "Auditoria na fila");
        }
        mirror(auditId, s);
    }

    public void phase(Long auditId, String phase, String message) {
        State s = state(auditId);
        if (s == null) return;
        synchronized (s) {
            s.phase = phase;
            log(s, message);
        }
        mirror(auditId, s);
    }

    /** Repositório clonado e tamanho do que será analisado. */
    public void sourceStats(Long auditId, String repoUrl, long filesRead, long linesOfCode) {
        State s = state(auditId);
        if (s == null) return;
        synchronized (s) {
            s.repoUrl = repoUrl;
            s.filesRead = filesRead;
            s.linesOfCode = linesOfCode;
        }
        mirror(auditId, s);
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
                ordered.put(a.name(), current != null ? current : withDescription(a));
            }
            s.analyzers.clear();
            s.analyzers.putAll(ordered);
            log(s, initial.stream().filter(a -> "ESPERANDO".equals(a.status())).count()
                    + " analisadores vão rodar em paralelo");
        }
        mirror(auditId, s);
    }

    public void analyzerStarted(Long auditId, String name) {
        update(auditId, name, "EXECUTANDO", 0, 0, null, null);
    }

    public void analyzerFinished(Long auditId, String name, String status, int findings, long durationMs, String note) {
        analyzerFinished(auditId, name, status, findings, durationMs, note, Map.of());
    }

    /** Como acima, guardando também os achados do analisador por severidade (resultado parcial). */
    public void analyzerFinished(Long auditId, String name, String status, int findings, long durationMs, String note,
                                 Map<String, Integer> bySeverity) {
        State st = state(auditId);
        if (st != null && bySeverity != null) {
            synchronized (st) {
                st.severities.put(name, Map.copyOf(bySeverity));
            }
        }
        String msg = "FALHOU".equals(status)
                ? name + " falhou" + (note == null ? "" : ": " + note)
                : "PARCIAL".equals(status)
                ? name + " terminou de forma parcial (" + findings + (findings == 1 ? " achado" : " achados") + ")"
                        + (note == null ? "" : ": " + note)
                : name + " concluído: " + findings + (findings == 1 ? " achado" : " achados");
        update(auditId, name, status, findings, durationMs, note, msg);
    }

    /** A auditoria terminou (com sucesso, falha ou cancelamento): o andamento deixa de existir. */
    public void finish(Long auditId) {
        if (auditId == null) return;
        states.remove(auditId);
        cancelRequested.remove(auditId);
        lastCancelCheck.remove(auditId);
        lastBeat.remove(auditId);
        if (store != null) {
            try {
                store.delete(auditId);
            } catch (RuntimeException e) {
                LOG.debugf("Não foi possível apagar o andamento da auditoria %d: %s", auditId, e.getMessage());
            }
        }
    }

    /** Pede o cancelamento; a orquestração para na próxima verificação. */
    public void requestCancel(Long auditId) {
        if (auditId != null) cancelRequested.add(auditId);
    }

    public boolean isCancelRequested(Long auditId) {
        if (auditId == null) return false;
        if (cancelRequested.contains(auditId)) return true;
        if (store == null) return false;
        // O cancelamento pode ter sido pedido em outra réplica: o status CANCELLED no banco é o sinal.
        long now = System.currentTimeMillis();
        Long last = lastCancelCheck.get(auditId);
        if (last != null && now - last < CHECK_MILLIS) return false;
        lastCancelCheck.put(auditId, now);
        try {
            if (store.isCancelled(auditId)) {
                cancelRequested.add(auditId);
                return true;
            }
        } catch (RuntimeException e) {
            LOG.debugf("Não foi possível consultar o cancelamento da auditoria %d: %s", auditId, e.getMessage());
        }
        return false;
    }

    /** Para testes: esquece a última consulta de cancelamento ao banco. */
    void lastCancelCheckReset() {
        lastCancelCheck.clear();
    }

    /** Batimento: mantém o andamento "vivo" no banco durante análises longas sem mudança de estado. */
    public void heartbeat(Long auditId) {
        State s = state(auditId);
        if (s == null || store == null) return;
        long now = System.currentTimeMillis();
        Long last = lastBeat.get(auditId);
        if (last != null && now - last < HEARTBEAT_MILLIS) return;
        mirror(auditId, s);
    }

    public Optional<AuditProgressDto> snapshot(Long auditId) {
        State s = state(auditId);
        if (s == null) return fromStore(auditId);
        synchronized (s) {
            return Optional.of(toDto(s));
        }
    }

    private static AuditProgressDto toDto(State s) {
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Map<String, Integer> one : s.severities.values()) {
            one.forEach((sev, n) -> total.merge(sev, n, Integer::sum));
        }
        return new AuditProgressDto(s.phase, List.copyOf(s.analyzers.values()), List.copyOf(s.events),
                s.repoUrl, s.filesRead, s.linesOfCode, total);
    }

    private static AnalyzerProgress withDescription(AnalyzerProgress a) {
        if (a.description() != null && !a.description().isBlank()) return a;
        return new AnalyzerProgress(a.name(), a.status(), a.findings(), a.durationMs(), a.note(),
                AnalyzerCatalog.describe(a.name()));
    }

    /** Andamento de uma auditoria que roda em outra réplica. */
    private Optional<AuditProgressDto> fromStore(Long auditId) {
        if (store == null || auditId == null) return Optional.empty();
        try {
            Optional<String> payload = store.load(auditId);
            return payload.isPresent() ? Optional.of(JSON.readValue(payload.get(), AuditProgressDto.class)) : Optional.empty();
        } catch (Exception e) {
            LOG.debugf("Não foi possível ler o andamento da auditoria %d: %s", auditId, e.getMessage());
            return Optional.empty();
        }
    }

    private void mirror(Long auditId, State s) {
        if (store == null || auditId == null) return;
        try {
            AuditProgressDto dto;
            synchronized (s) {
                dto = toDto(s);
            }
            store.save(auditId, JSON.writeValueAsString(dto));
            lastBeat.put(auditId, System.currentTimeMillis());
        } catch (Exception e) {
            LOG.debugf("Não foi possível gravar o andamento da auditoria %d: %s", auditId, e.getMessage());
        }
    }

    private void update(Long auditId, String name, String status, int findings, long durationMs, String note, String message) {
        State s = state(auditId);
        if (s == null) return;
        synchronized (s) {
            AnalyzerProgress before = s.analyzers.get(name);
            String description = before != null && before.description() != null && !before.description().isBlank()
                    ? before.description() : AnalyzerCatalog.describe(name);
            s.analyzers.put(name, new AnalyzerProgress(name, status, findings, durationMs, note, description));
            if (message != null) log(s, message);
        }
        mirror(auditId, s);
    }

    private State state(Long auditId) {
        return auditId == null ? null : states.get(auditId);
    }

    private static void log(State s, String message) {
        if (s.events.size() >= MAX_EVENTS) s.events.remove(0);
        s.events.add(new Event(LocalDateTime.now(), message));
    }
}
