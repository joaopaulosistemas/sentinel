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
 * Procura segredos (arquivos atuais <b>e histórico do git</b>) com o Gitleaks.
 * <p>
 * Só atua quando o executável {@code gitleaks} está no PATH; nesse caso substitui os analisadores internos
 * de segredos ({@link SecretsAnalyzer} e {@link HistorySecretsAnalyzer}), evitando contar o mesmo problema duas vezes.
 * <p>
 * <b>O segredo nunca é gravado.</b> O relatório do Gitleaks (que contém o valor em claro) fica em arquivo
 * temporário de uso exclusivo do processo e é apagado ao final; do valor só se guarda um hash curto, usado para
 * reconhecer o mesmo segredo entre auditorias.
 */
@ApplicationScoped
public class GitleaksAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(GitleaksAnalyzer.class);
    private static final String ANALYZER_NAME = "Gitleaks";

    @ConfigProperty(name = "sentinel.audit.gitleaks.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "sentinel.audit.gitleaks.timeout-seconds", defaultValue = "600")
    long timeoutSeconds;

    /** Substituível em testes. */
    String pathEnv = System.getenv("PATH");

    @Override
    public boolean isAvailable() {
        return enabled && ExternalTools.find("gitleaks", pathEnv) != null;
    }

    @Override
    public List<Class<? extends CodeAnalyzer>> replaces() {
        return List.of(SecretsAnalyzer.class, HistorySecretsAnalyzer.class);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Path exe = enabled ? ExternalTools.find("gitleaks", pathEnv) : null;
        if (exe == null) {
            return ToolRun.unavailable(ANALYZER_NAME,
                    "Gitleaks indisponível; análise de segredos por esta ferramenta ignorada", LOG);
        }
        return ToolRun.run(ANALYZER_NAME, "gitleaks", LOG, (report, stderr) -> {
            List<String> cmd = new ArrayList<>(List.of(exe.toString(), "detect",
                    "--source", projectPath.toString(),
                    "--report-format", "json", "--report-path", report.toString(),
                    "--no-banner", "--log-level", "error", "--exit-code", "0"));
            if (!Files.isDirectory(projectPath.resolve(".git"))) {
                cmd.add("--no-git");
            }
            int code = ExternalTools.runTo(cmd, null, Map.of(), timeoutSeconds, new ExternalTools.Output(null, stderr));
            if (code != 0) {
                String partial = ExternalTools.falha(code, timeoutSeconds, stderr);
                LOG.warnf("Gitleaks %s", partial);
                return new ToolRun.Outcome(new ArrayList<>(), partial);
            }
            List<Finding> found = parse(Files.readString(report, StandardCharsets.UTF_8), projectPath, auditRun);
            LOG.infof("Gitleaks encontrou %d segredos", found.size());
            return new ToolRun.Outcome(found, null);
        });
    }

    /** Converte o relatório JSON (lista) em findings; o valor do segredo é descartado aqui. */
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
