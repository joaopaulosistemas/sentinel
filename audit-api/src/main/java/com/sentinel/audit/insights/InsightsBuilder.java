package com.sentinel.audit.insights;

import com.sentinel.audit.dto.RemediationDto;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.service.RemediationCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Transforma os findings ativos (mais o mapa do projeto e a situação dos analisadores) em uma auditoria completa:
 * relatório por área, tabela de dívida técnica, prioridades, quick wins e plano em fases.
 * <p>
 * É determinístico e só usa o que foi observado: nada é inventado. O que uma análise estática não alcança
 * (cobertura real, Core Web Vitals, contraste, produção) aparece em {@link AuditInsights#unverified()}.
 */
public final class InsightsBuilder {

    private static final int ROADMAP_ITEMS = 15;
    private static final int TOP = 10;

    /** Quais analisadores dão cobertura a cada área (nomes como em {@code CodeAnalyzer.getName()}). */
    private static final Map<AuditArea, Set<String>> COVERAGE = new EnumMap<>(AuditArea.class);

    static {
        COVERAGE.put(AuditArea.SECURITY, Set.of("Semgrep", "Secrets", "Gitleaks", "Trivy", "OWASP-Dependency", "Higiene do projeto"));
        COVERAGE.put(AuditArea.ARCHITECTURE, Set.of("Architecture", "Arquitetura (pacotes)"));
        COVERAGE.put(AuditArea.CODE_QUALITY, Set.of("CleanCode", "Semgrep", "PMD", "ESLint"));
        COVERAGE.put(AuditArea.TESTING, Set.of("Higiene do projeto"));
        COVERAGE.put(AuditArea.PERFORMANCE, Set.of("Semgrep"));
        COVERAGE.put(AuditArea.FRONTEND, Set.of("Semgrep"));
        COVERAGE.put(AuditArea.DEVOPS, Set.of("Semgrep", "Higiene do projeto"));
        COVERAGE.put(AuditArea.DOCUMENTATION, Set.of("Higiene do projeto"));
        COVERAGE.put(AuditArea.DEPENDENCIES, Set.of("OWASP-Dependency", "Trivy"));
        COVERAGE.put(AuditArea.OBSERVABILITY, Set.of("Semgrep", "CleanCode"));
        COVERAGE.put(AuditArea.MAINTAINABILITY, Set.of("CleanCode", "Higiene do projeto"));
    }

    /** O que nenhuma análise estática consegue afirmar, por área. */
    private static final Map<AuditArea, List<String>> LIMITS = new EnumMap<>(AuditArea.class);

    static {
        LIMITS.put(AuditArea.SECURITY, List.of("Configuração real de produção, permissões de nuvem e teste de intrusão"));
        LIMITS.put(AuditArea.ARCHITECTURE, List.of("Aderência às decisões de negócio e à arquitetura pretendida (só regras declaradas em pacote são checadas)"));
        LIMITS.put(AuditArea.TESTING, List.of("Cobertura real e qualidade dos testes (exige executar a suíte com cobertura)"));
        LIMITS.put(AuditArea.PERFORMANCE, List.of("Tempo de resposta, memória e comportamento sob carga (exige execução)",
                "Core Web Vitals e Lighthouse (exige navegador e ambiente publicado)"));
        LIMITS.put(AuditArea.FRONTEND, List.of("Contraste de cores, ordem de foco e leitura por leitor de tela (exige renderização)",
                "Responsividade em dispositivos reais"));
        LIMITS.put(AuditArea.DEVOPS, List.of("Estado real do pipeline e do ambiente de implantação"));
        LIMITS.put(AuditArea.OBSERVABILITY, List.of("Métricas, alertas e rastreamento em produção"));
        LIMITS.put(AuditArea.DOCUMENTATION, List.of("Qualidade e atualidade do conteúdo (só a presença é verificada)"));
    }

    private static final Map<AuditArea, String> CONSEQUENCE = new EnumMap<>(AuditArea.class);

    static {
        CONSEQUENCE.put(AuditArea.SECURITY, "exposição a ataque ou vazamento de dados");
        CONSEQUENCE.put(AuditArea.ARCHITECTURE, "acoplamento que encarece e atrasa mudanças");
        CONSEQUENCE.put(AuditArea.CODE_QUALITY, "bugs e dificuldade de manutenção");
        CONSEQUENCE.put(AuditArea.TESTING, "regressões sem detecção");
        CONSEQUENCE.put(AuditArea.PERFORMANCE, "lentidão, travamentos e falhas sob carga");
        CONSEQUENCE.put(AuditArea.FRONTEND, "usuários excluídos ou com experiência degradada");
        CONSEQUENCE.put(AuditArea.DEVOPS, "implantações frágeis e ambientes inseguros");
        CONSEQUENCE.put(AuditArea.DOCUMENTATION, "dependência de conhecimento oral e onboarding lento");
        CONSEQUENCE.put(AuditArea.DEPENDENCIES, "vulnerabilidades conhecidas e builds não reproduzíveis");
        CONSEQUENCE.put(AuditArea.OBSERVABILITY, "falhas difíceis de diagnosticar");
        CONSEQUENCE.put(AuditArea.MAINTAINABILITY, "código difícil de entender e alterar");
    }

    private InsightsBuilder() {
    }

    public static AuditInsights build(String systemName, List<Finding> active, AuditContextData ctx) {
        ProjectMap map = ctx == null ? null : ctx.projectMap();
        List<AnalyzerRunInfo> runs = ctx == null || ctx.analyzerRuns() == null ? List.of() : ctx.analyzerRuns();

        // ---- agrupa por problema
        Map<String, List<Finding>> groups = new LinkedHashMap<>();
        for (Finding f : active) {
            String key = f.getRuleId() != null && !f.getRuleId().isBlank() ? f.getRuleId() : "t:" + f.getTitle();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }
        List<DebtItem> debt = new ArrayList<>();
        for (Map.Entry<String, List<Finding>> e : groups.entrySet()) {
            debt.add(toItem(e.getKey(), e.getValue()));
        }
        debt.sort(Comparator.comparing(DebtItem::priority).thenComparing(Comparator.comparingInt(DebtItem::occurrences).reversed())
                .thenComparing(DebtItem::problem));

        Map<String, Integer> bySev = countBySeverity(active);
        List<AreaReport> areas = new ArrayList<>();
        for (AuditArea a : AuditArea.values()) {
            areas.add(areaReport(a, debt, runs));
        }

        List<DebtItem> topProblems = debt.stream().limit(TOP).toList();
        List<DebtItem> byScore = debt.stream().sorted(Comparator.comparingDouble(InsightsBuilder::score).reversed()
                .thenComparing(DebtItem::problem)).toList();
        List<DebtItem> topImprovements = byScore.stream().limit(TOP).toList();
        List<DebtItem> quickWins = byScore.stream()
                .filter(d -> "S".equals(d.effort()) && !"P3".equals(d.priority())).limit(TOP).toList();
        List<DebtItem> critical = debt.stream().filter(d -> d.priority().equals("P0") || d.priority().equals("P1")).limit(TOP).toList();

        List<String> unverified = unverified(areas, runs, map);
        String verdict = verdict(debt);
        return new AuditInsights(summary(systemName, bySev, active.size(), areas, debt, verdict, unverified.size()), verdict,
                bySev, active.size(), areas, debt, topProblems, topImprovements, quickWins, critical, roadmap(debt),
                unverified, runs, map);
    }

    // ------------------------------------------------------------------ itens

    static DebtItem toItem(String key, List<Finding> list) {
        Finding first = list.stream().max(Comparator.comparingInt(f -> -ordinal(f.getSeverity()))).orElse(list.get(0));
        Severity sev = first.getSeverity() == null ? Severity.INFO : first.getSeverity();
        AuditArea area = AreaClassifier.classify(first);
        RemediationDto rem = RemediationCatalog.forFinding(first);
        String effort = rem != null && rem.effort() != null ? rem.effort() : "M";
        boolean estimated = rem == null || rem.effort() == null;
        String risk = rem != null && rem.regressionRisk() != null ? rem.regressionRisk() : null;
        String recommendation = rem != null && rem.summary() != null ? rem.summary()
                : first.getAiRecommendation() != null && !first.getAiRecommendation().isBlank() ? first.getAiRecommendation()
                : "Revisar o trecho indicado e corrigir conforme a descrição do achado.";
        String example = first.getFilePath() == null ? null
                : first.getLineNumber() == null ? first.getFilePath() : first.getFilePath() + ":" + first.getLineNumber();
        String id = key.startsWith("t:") ? key.substring(2) : key;
        String title = first.getTitle() == null ? id : first.getTitle();
        return new DebtItem(id, title, area.name(), area.label(), sev, priorityOf(sev), priorityLabel(priorityOf(sev)),
                list.size(), impact(area, sev), effort, estimated, risk, recommendation, example);
    }

    static String priorityOf(Severity s) {
        return switch (s) {
            case CRITICAL -> "P0";
            case HIGH -> "P1";
            case MEDIUM -> "P2";
            case LOW, INFO -> "P3";
        };
    }

    static String priorityLabel(String p) {
        return switch (p) {
            case "P0" -> "Bloqueador";
            case "P1" -> "Alta";
            case "P2" -> "Importante";
            default -> "Futura";
        };
    }

    static String impact(AuditArea area, Severity sev) {
        String level = switch (sev) {
            case CRITICAL -> "Crítico";
            case HIGH -> "Alto";
            case MEDIUM -> "Moderado";
            case LOW -> "Baixo";
            case INFO -> "Informativo";
        };
        return level + ": risco de " + CONSEQUENCE.get(area);
    }

    private static int ordinal(Severity s) {
        return s == null ? 4 : s.ordinal();
    }

    static double score(DebtItem d) {
        double sev = switch (d.severity()) {
            case CRITICAL -> 10;
            case HIGH -> 6;
            case MEDIUM -> 3;
            case LOW -> 1;
            case INFO -> 0.5;
        };
        double cost = switch (d.effort()) {
            case "S" -> 1;
            case "L" -> 4;
            default -> 2;
        };
        return sev * (1 + Math.log(Math.max(1, d.occurrences())) / Math.log(2)) / cost;
    }

    // ------------------------------------------------------------------ áreas

    private static Map<String, Integer> countBySeverity(List<Finding> list) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Severity s : Severity.values()) {
            m.put(s.name(), 0);
        }
        for (Finding f : list) {
            m.merge((f.getSeverity() == null ? Severity.INFO : f.getSeverity()).name(), 1, Integer::sum);
        }
        return m;
    }

    private static AreaReport areaReport(AuditArea area, List<DebtItem> allDebt, List<AnalyzerRunInfo> runs) {
        List<DebtItem> items = allDebt.stream().filter(d -> d.area().equals(area.name())).toList();
        Map<String, Integer> sev = new LinkedHashMap<>();
        for (Severity s : Severity.values()) {
            sev.put(s.name(), 0);
        }
        int total = 0;
        for (DebtItem d : items) {
            sev.merge(d.severity().name(), d.occurrences(), Integer::sum);
            total += d.occurrences();
        }
        boolean verified = runs.isEmpty() || runs.stream().anyMatch(r -> r.ok() && COVERAGE.get(area).contains(r.name()));
        String state;
        String current;
        if (!verified && total == 0) {
            state = "NAO_VERIFICADO";
            current = "Não foi possível verificar: nenhum analisador que cobre esta área executou.";
        } else if (sev.get("CRITICAL") > 0 || sev.get("HIGH") >= 3) {
            state = "CRITICO";
            current = total + " achado(s), incluindo " + sev.get("CRITICAL") + " crítico(s) e " + sev.get("HIGH") + " alto(s).";
        } else if (sev.get("HIGH") > 0 || sev.get("MEDIUM") > 0) {
            state = "ATENCAO";
            current = total + " achado(s): " + sev.get("HIGH") + " alto(s), " + sev.get("MEDIUM") + " médio(s).";
        } else if (total > 0) {
            state = "ATENCAO";
            current = total + " achado(s) de baixa gravidade.";
        } else {
            state = "SEM_ACHADOS";
            current = "Nenhum achado nas verificações automáticas desta área.";
        }
        List<String> problems = items.stream().limit(5)
                .map(d -> d.problem() + " (" + d.occurrences() + "×, " + d.priority() + ")").toList();
        List<String> risks = items.stream().filter(d -> d.priority().equals("P0") || d.priority().equals("P1")).limit(3)
                .map(d -> d.impact() + " — " + d.problem()).toList();
        List<String> recs = new ArrayList<>(new LinkedHashSet<>(items.stream().limit(3).map(DebtItem::recommendation).toList()));
        List<String> notVerified = new ArrayList<>(LIMITS.getOrDefault(area, List.of()));
        return new AreaReport(area.name(), area.label(), state, current, sev, total, problems, risks, recs, notVerified);
    }

    // ------------------------------------------------------------------ plano

    static List<RoadmapPhase> roadmap(List<DebtItem> debt) {
        String[][] phases = {
                {"Correções críticas", "Eliminar bloqueadores (P0) antes de qualquer outra coisa"},
                {"Qualidade estrutural", "Arquitetura, código limpo e manutenibilidade"},
                {"Performance", "Gargalos, consultas em laço e chamadas sem limite"},
                {"Segurança", "Vulnerabilidades, segredos e dependências"},
                {"Testes", "Ter rede de segurança antes de refatorar mais"},
                {"UX/UI e acessibilidade", "Experiência, acessibilidade e responsividade"},
                {"DevOps", "Pipeline, contêineres, infraestrutura e observabilidade"},
                {"Melhorias futuras", "Baixa prioridade e documentação"}};
        List<List<DebtItem>> buckets = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            buckets.add(new ArrayList<>());
        }
        for (DebtItem d : debt) {
            buckets.get(phaseOf(d)).add(d);
        }
        List<RoadmapPhase> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            List<DebtItem> b = buckets.get(i);
            out.add(new RoadmapPhase(i + 1, phases[i][0], phases[i][1], b.size(), b.stream().limit(ROADMAP_ITEMS).toList()));
        }
        return out;
    }

    private static int phaseOf(DebtItem d) {
        if (d.priority().equals("P0")) {
            return 0;
        }
        if (d.priority().equals("P3")) {
            return 7;
        }
        return switch (AuditArea.valueOf(d.area())) {
            case ARCHITECTURE, CODE_QUALITY, MAINTAINABILITY -> 1;
            case PERFORMANCE -> 2;
            case SECURITY, DEPENDENCIES -> 3;
            case TESTING -> 4;
            case FRONTEND -> 5;
            case DEVOPS, OBSERVABILITY -> 6;
            case DOCUMENTATION -> 7;
        };
    }

    // ------------------------------------------------------------------ texto

    static String verdict(List<DebtItem> debt) {
        if (debt.stream().anyMatch(d -> d.priority().equals("P0"))) {
            return "REPROVADO: há bloqueadores (P0)";
        }
        if (debt.stream().anyMatch(d -> d.priority().equals("P1"))) {
            return "ATENÇÃO: há problemas de prioridade alta (P1)";
        }
        if (debt.stream().anyMatch(d -> d.priority().equals("P2"))) {
            return "APROVADO COM RESSALVAS: há pontos importantes (P2)";
        }
        return "APROVADO: sem pendências relevantes nas verificações automáticas";
    }

    private static String summary(String system, Map<String, Integer> sev, int total, List<AreaReport> areas,
                                  List<DebtItem> debt, String verdict, int unverified) {
        long p0 = debt.stream().filter(d -> d.priority().equals("P0")).count();
        long p1 = debt.stream().filter(d -> d.priority().equals("P1")).count();
        List<String> worst = areas.stream().filter(a -> a.total() > 0)
                .sorted(Comparator.comparingInt((AreaReport a) -> -(a.bySeverity().get("CRITICAL") * 100 + a.bySeverity().get("HIGH") * 10 + a.bySeverity().get("MEDIUM"))))
                .limit(3).map(AreaReport::label).toList();
        StringBuilder sb = new StringBuilder();
        sb.append(system == null ? "Auditoria" : "Auditoria de " + system).append(": ").append(total).append(" achado(s) ativo(s) (")
                .append(sev.get("CRITICAL")).append(" críticos, ").append(sev.get("HIGH")).append(" altos, ")
                .append(sev.get("MEDIUM")).append(" médios, ").append(sev.get("LOW") + sev.get("INFO")).append(" baixos/informativos). ");
        sb.append("Parecer: ").append(verdict).append(". ");
        if (!worst.isEmpty()) {
            sb.append("Áreas mais afetadas: ").append(String.join(", ", worst)).append(". ");
        }
        if (p0 + p1 > 0) {
            sb.append("Comece pela Fase 1 (").append(p0).append(" bloqueador(es)) e siga para os ").append(p1).append(" item(ns) de prioridade alta. ");
        }
        sb.append(unverified).append(" ponto(s) não puderam ser verificados por análise estática (veja a lista).");
        return sb.toString();
    }

    private static List<String> unverified(List<AreaReport> areas, List<AnalyzerRunInfo> runs, ProjectMap map) {
        List<String> out = new ArrayList<>();
        for (AnalyzerRunInfo r : runs) {
            if (!r.ok() && !"NAO_SUPORTADO".equals(r.status()) && !"SUBSTITUIDO".equals(r.status())) {
                out.add("Analisador " + r.name() + " não executou (" + r.status().toLowerCase(Locale.ROOT) + (r.note() == null ? "" : ": " + r.note())
                        + "): o que ele cobriria não foi verificado");
            }
        }
        if (map != null && map.truncated()) {
            out.add("Repositório muito grande: o mapa do projeto foi truncado em " + ProjectProfiler.MAX_FILES + " arquivos");
        }
        for (AreaReport a : areas) {
            if (a.state().equals("NAO_VERIFICADO")) {
                out.add(a.label() + ": nenhum analisador que cobre esta área executou");
            }
            for (String n : a.notVerified()) {
                out.add(a.label() + ": " + n);
            }
        }
        return out;
    }
}
