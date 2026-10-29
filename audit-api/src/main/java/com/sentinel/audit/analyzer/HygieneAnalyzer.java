package com.sentinel.audit.analyzer;

import com.sentinel.audit.insights.ProjectMap;
import com.sentinel.audit.insights.ProjectProfiler;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import com.sentinel.audit.pack.AuditPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Higiene do repositório, só por evidência de arquivos: README, testes, CI, lockfile, segredos versionados,
 * artefatos de build commitados e .dockerignore. Cada regra tem id {@code sentinel-proj-*} e pode ser
 * desligada ou ter a severidade alterada por perfil. Limites do perfil: {@code min-source-files-for-tests}
 * (padrão 10) e {@code readme-min-lines} (padrão 10).
 */
@ApplicationScoped
public class HygieneAnalyzer implements CodeAnalyzer {

    private static final Logger LOG = Logger.getLogger(HygieneAnalyzer.class);
    private static final String ANALYZER_NAME = "Higiene do projeto";

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        return analyze(projectPath, auditRun, null);
    }

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun, AuditPolicy policy) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();
        try {
            ProjectMap map = ProjectProfiler.profile(projectPath);
            int minSrc = policy == null ? 10 : policy.threshold("min-source-files-for-tests", 10);
            int minReadme = policy == null ? 10 : policy.threshold("readme-min-lines", 10);
            findings = check(map, auditRun, minSrc, minReadme);
        } catch (Exception e) {
            LOG.warnf("Higiene do projeto não pôde ser verificada: %s", e.getMessage());
        }
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    /** Regras puras sobre o mapa; separado para teste. */
    public static List<Finding> check(ProjectMap m, AuditRun run, int minSourceFilesForTests, int readmeMinLines) {
        List<Finding> out = new ArrayList<>();
        readmeChecks(m, run, readmeMinLines, out);
        testsAndCiChecks(m, run, minSourceFilesForTests, out);
        dependencyAndSecretChecks(m, run, out);
        repositoryHygieneChecks(m, run, out);
        return out;
    }

    private static void readmeChecks(ProjectMap m, AuditRun run, int readmeMinLines, List<Finding> out) {
        if (!m.hasReadme()) {
            out.add(f(run, new R("sentinel-proj-no-readme", Severity.MEDIUM, Category.QUALITY), ".", "Projeto sem README na raiz", "Não há README na raiz do repositório. Quem chega não sabe o que o sistema faz, como rodar nem como testar."));
        } else if (m.readmeLines() < readmeMinLines) {
            out.add(f(run, new R("sentinel-proj-readme-short", Severity.LOW, Category.QUALITY), "README.md", "README muito curto (" + m.readmeLines() + " linhas)", "O README tem menos de " + readmeMinLines + " linhas não vazias. Descreva o propósito, como executar, testar e implantar."));
        }
    }

    private static void testsAndCiChecks(ProjectMap m, AuditRun run, int minSourceFilesForTests, List<Finding> out) {
        if (m.sourceFiles() >= minSourceFilesForTests && m.testFiles() == 0) {
            out.add(f(run, new R("sentinel-proj-no-tests", Severity.HIGH, Category.QUALITY), ".", "Nenhum arquivo de teste encontrado em " + m.sourceFiles() + " arquivos de código", "Não foi encontrado nenhum arquivo de teste (padrões *Test, *.test.*, *.spec.*, test_*, pastas test/tests/__tests__). "
                            + "Se os testes ficam em outro repositório, desligue esta regra no perfil."));
        }
        if (m.sourceFiles() > 0 && m.ciConfigs().isEmpty()) {
            out.add(f(run, new R("sentinel-proj-no-ci", Severity.MEDIUM, Category.QUALITY), ".", "Nenhuma configuração de CI/CD encontrada", "Não há pipeline versionado (GitHub Actions, GitLab CI, Jenkinsfile, Azure Pipelines, Bitbucket, CircleCI, Cloud Build). "
                            + "Sem ele, build, testes e análise dependem de execução manual."));
        }
    }

    private static void dependencyAndSecretChecks(ProjectMap m, AuditRun run, List<Finding> out) {
        for (String p : m.packageJsonWithoutLock()) {
            out.add(f(run, new R("sentinel-proj-no-lockfile", Severity.MEDIUM, Category.DEPENDENCY), p, "package.json sem lockfile", "Não há package-lock.json, yarn.lock ou pnpm-lock.yaml. Builds não são reproduzíveis e a análise de vulnerabilidades perde precisão."));
        }
        for (String p : m.committedEnvFiles()) {
            out.add(f(run, new R("sentinel-proj-committed-env", Severity.HIGH, Category.SECURITY), p, "Arquivo de ambiente versionado: " + p, "Arquivos .env costumam conter credenciais. Remova do repositório (e do histórico), rotacione o que já foi exposto e versione apenas um .env.example."));
        }
        for (String p : m.committedKeyFiles()) {
            out.add(f(run, new R("sentinel-proj-committed-key", Severity.CRITICAL, Category.SECURITY), p, "Chave privada ou keystore versionado: " + p, "Chave privada, certificado com chave ou keystore dentro do repositório. Considere-a comprometida: rotacione e remova do histórico."));
        }
    }

    private static void repositoryHygieneChecks(ProjectMap m, AuditRun run, List<Finding> out) {
        if (!m.hasGitignore() && m.sourceFiles() > 0) {
            out.add(f(run, new R("sentinel-proj-no-gitignore", Severity.LOW, Category.QUALITY), ".", "Projeto sem .gitignore", "Sem .gitignore, artefatos de build, dependências e arquivos locais acabam versionados por engano."));
        }
        for (String d : m.committedArtifactDirs()) {
            out.add(f(run, new R("sentinel-proj-committed-artifacts", Severity.MEDIUM, Category.QUALITY), d, "Artefatos de build/dependências versionados: " + d, "A pasta existe no repositório. Ela infla o clone, mascara diffs e pode carregar binários não auditados. Remova e adicione ao .gitignore."));
        }
        for (String d : m.dockerfilesWithoutIgnore()) {
            out.add(f(run, new R("sentinel-proj-docker-no-dockerignore", Severity.LOW, Category.SECURITY), d, "Dockerfile sem .dockerignore", "Sem .dockerignore o contexto de build envia .git, .env e node_modules para a imagem, aumentando tamanho e risco de vazamento."));
        }
    }

    /** Identidade de uma regra de higiene: id, gravidade e categoria. */
    private record R(String id, Severity sev, Category cat) { }

    private static Finding f(AuditRun run, R rule, String file, String title, String desc) {
        Finding f = new Finding();
        f.setAuditRun(run);
        f.setToolSource(ANALYZER_NAME);
        f.setCategory(rule.cat());
        f.setSeverity(rule.sev());
        f.setRuleId(rule.id());
        f.setTitle(title);
        f.setDescription(desc);
        f.setFilePath(file);
        f.setLineNumber(1);
        return f;
    }

    @Override
    public boolean supports(Stack stack) {
        return true;
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }
}
