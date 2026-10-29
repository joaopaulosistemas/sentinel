package com.sentinel.audit.pack;

import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regras de arquitetura e de componentes, declaradas em YAML ({@code architecture.yml}) em vez de código:
 * camadas e dependências proibidas entre elas, convenções de arquivo, arquivos obrigatórios e
 * política de bibliotecas (proibidas, versão mínima, obrigatórias, lista de aprovadas).
 */
public record ArchitecturePolicy(List<Layer> layers, List<LayerRule> forbid, List<LayerRule> allow, boolean strict,
                                 List<Convention> conventions, List<RequiredFile> requiredFiles,
                                 Components components) {

    public static final ArchitecturePolicy EMPTY = new ArchitecturePolicy(List.of(), List.of(), List.of(), false,
            List.of(), List.of(), new Components(List.of(), List.of(), List.of(), List.of()));

    /** Camada: arquivos por glob de caminho; pacotes (Java) ou módulos (TS) por glob de import. */
    public record Layer(String name, List<String> paths, List<String> packages) {}

    /** {@code from} não pode depender de {@code to} (forbid) / pode depender (allow). */
    public record LayerRule(String from, String to, String message, Severity severity) {}

    /** Arquivos que casam com {@code files} (e não com {@code exclude}) devem conter {@code require} / não conter {@code forbid}. */
    public record Convention(String id, String title, List<String> files, List<String> exclude, String require,
                             String forbid, String message, Severity severity, Category category) {}

    public record RequiredFile(String path, String message, Severity severity) {}

    /** {@code dep}: glob no formato {@code grupo:artefato} (Maven) ou nome do pacote (npm). */
    public record DepRule(String dep, String version, String reason, Severity severity) {}

    public record Components(List<DepRule> forbidden, List<DepRule> minVersion, List<DepRule> required,
                             List<DepRule> approvedOnly) {}

    private boolean noComponentRules() {
        return components.forbidden().isEmpty() && components.minVersion().isEmpty()
                && components.required().isEmpty() && components.approvedOnly().isEmpty();
    }

    public boolean isEmpty() {
        return layers.isEmpty() && forbid.isEmpty() && allow.isEmpty() && conventions.isEmpty()
                && requiredFiles.isEmpty() && noComponentRules();
    }

    /** Junta as políticas de vários pacotes; camada com o mesmo nome é substituída pela do pacote mais adiante. */
    public static ArchitecturePolicy merge(List<ArchitecturePolicy> policies) {
        Map<String, Layer> layers = new LinkedHashMap<>();
        List<LayerRule> forbid = new ArrayList<>();
        List<LayerRule> allow = new ArrayList<>();
        List<Convention> conventions = new ArrayList<>();
        List<RequiredFile> files = new ArrayList<>();
        List<DepRule> forbidden = new ArrayList<>();
        List<DepRule> min = new ArrayList<>();
        List<DepRule> required = new ArrayList<>();
        List<DepRule> approved = new ArrayList<>();
        boolean strict = false;
        for (ArchitecturePolicy p : policies) {
            p.layers().forEach(l -> layers.put(l.name(), l));
            forbid.addAll(p.forbid());
            allow.addAll(p.allow());
            conventions.addAll(p.conventions());
            files.addAll(p.requiredFiles());
            forbidden.addAll(p.components().forbidden());
            min.addAll(p.components().minVersion());
            required.addAll(p.components().required());
            approved.addAll(p.components().approvedOnly());
            strict |= p.strict();
        }
        return new ArchitecturePolicy(new ArrayList<>(layers.values()), forbid, allow, strict, conventions, files,
                new Components(forbidden, min, required, approved));
    }
}
