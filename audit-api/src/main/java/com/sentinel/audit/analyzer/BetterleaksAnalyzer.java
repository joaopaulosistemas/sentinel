package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Stack;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Procura segredos (arquivos atuais <b>e histórico do git</b>) com o Betterleaks, sucessor do Gitleaks.
 * <p>
 * Roda a série 1.x (a 2.x ainda é candidata e muda a linha de comando e o formato do relatório). Vem <b>desligado
 * por padrão</b>: ligado e com o executável {@code betterleaks} no PATH, substitui o Gitleaks e os analisadores
 * internos de segredos, para o mesmo segredo não aparecer duas vezes. Sem ele, nada muda.
 * <p>
 * O Betterleaks 1.x não carrega o {@code .betterleaks.toml} do projeto auditado sem pedir (só com
 * {@code --unsafe-auto-load-config}, que aqui nunca é passado). <b>O segredo nunca é gravado:</b> o relatório fica em
 * arquivo temporário e é apagado; do valor só se guarda um hash curto.
 */
@ApplicationScoped
public class BetterleaksAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(BetterleaksAnalyzer.class);
    private static final String ANALYZER_NAME = "Betterleaks";
    private static final String EXE = "betterleaks";

    @ConfigProperty(name = "sentinel.audit.betterleaks.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "sentinel.audit.betterleaks.timeout-seconds", defaultValue = "600")
    long timeoutSeconds;

    /** Substituível em testes. */
    String pathEnv = System.getenv("PATH");

    @Override
    public boolean isAvailable() {
        return enabled && ExternalTools.find(EXE, pathEnv) != null;
    }

    @Override
    public List<Class<? extends CodeAnalyzer>> replaces() {
        return List.of(SecretsAnalyzer.class, HistorySecretsAnalyzer.class, GitleaksAnalyzer.class);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Path exe = enabled ? ExternalTools.find(EXE, pathEnv) : null;
        if (exe == null) {
            return ToolRun.unavailable(ANALYZER_NAME,
                    "Betterleaks indisponível; análise de segredos por esta ferramenta ignorada", LOG);
        }
        return ToolRun.run(ANALYZER_NAME, EXE, LOG, (report, stderr) -> {
            // com .git: histórico inteiro; sem: só os arquivos
            String modo = Files.isDirectory(projectPath.resolve(".git")) ? "git" : "dir";
            List<String> cmd = new ArrayList<>(List.of(exe.toString(), modo, projectPath.toString(),
                    "--report-format", "json", "--report-path", report.toString(),
                    "--no-banner", "--log-level", "error", "--exit-code", "0"));
            int code = ExternalTools.runTo(cmd, null, Map.of(), timeoutSeconds, new ExternalTools.Output(null, stderr));
            if (code != 0) {
                String partial = ExternalTools.falha(code, timeoutSeconds, stderr);
                LOG.warnf("Betterleaks %s", partial);
                return new ToolRun.Outcome(new ArrayList<>(), partial);
            }
            List<Finding> found = parse(Files.readString(report, StandardCharsets.UTF_8), projectPath, auditRun);
            LOG.infof("Betterleaks encontrou %d segredos", found.size());
            return new ToolRun.Outcome(found, null);
        });
    }

    /** Converte o relatório JSON (lista, mesmo formato do Gitleaks) em findings; o valor do segredo é descartado aqui. */
    List<Finding> parse(String json, Path projectPath, AuditRun auditRun) {
        return SecretReportParser.parse(ANALYZER_NAME, json, projectPath, auditRun);
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return true;
    }
}
