package com.sentinel.audit.analyzer;

import com.sentinel.audit.analyzer.deps.Dependency;
import com.sentinel.audit.analyzer.deps.DependencyExtractor;
import com.sentinel.audit.analyzer.deps.OsvClient;
import com.sentinel.audit.analyzer.deps.OsvVulnerability;
import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.model.Stack;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzer de dependências (SCA).
 * <p>
 * Lê {@code pom.xml}, {@code package-lock.json}/{@code package.json}, {@code build.gradle},
 * {@code requirements.txt} e {@code *.csproj} e consulta a base pública OSV.dev (só nome e versão
 * do pacote saem da rede). Se o OSV estiver desligado ou inacessível, usa uma base <i>interna e pequena</i>
 * de CVEs conhecidos (Maven e npm), comparando a versão com a primeira versão corrigida.
 * <p>
 * Limitação: dependências cuja versão vem de um BOM/parent externo (sem versão explícita) não são avaliadas.
 */
@ApplicationScoped
public class OWASPDependencyAnalyzer implements CodeAnalyzer {

    /** Quantos identificadores de vulnerabilidade entram no texto do achado. */
    private static final int MAX_LISTED_IDS = 8;
    private static final int RANK_CRITICAL = 4;
    private static final int RANK_HIGH = 3;
    private static final int RANK_MEDIUM = 2;
    private static final int RANK_LOW = 1;


    private static final Logger LOG = Logger.getLogger(OWASPDependencyAnalyzer.class);
    private static final String ANALYZER_NAME = "OWASP-Dependency";



    /**
     * Vulnerabilidade conhecida.
     *
     * @param ecosystem     "maven" ou "npm"
     * @param artifactId    artifactId (Maven) ou nome do pacote (npm) — comparação exata
     * @param fixedVersions primeiras versões corrigidas, uma por linha de release (ex.: 2.12.7.1, 2.13.4.2)
     */
    record KnownVulnerability(String ecosystem, String artifactId, List<String> fixedVersions,
                              String cve, Severity severity, String description) {}

    private static final List<KnownVulnerability> KNOWN_VULNERABILITIES = List.of(
            // ---- Maven
            new KnownVulnerability("maven", "log4j-core", List.of("2.3.2", "2.12.4", "2.17.1"),
                    "CVE-2021-44228", Severity.CRITICAL, "Log4Shell — execução remota de código via JNDI lookup"),
            new KnownVulnerability("maven", "log4j", List.of("2.0"),
                    "CVE-2019-17571", Severity.HIGH, "Log4j 1.x está fora de suporte e é vulnerável a desserialização insegura"),
            new KnownVulnerability("maven", "spring-core", List.of("5.2.20", "5.3.18"),
                    "CVE-2022-22965", Severity.CRITICAL, "Spring4Shell — execução remota de código via data binding"),
            new KnownVulnerability("maven", "spring-webmvc", List.of("5.2.20", "5.3.18"),
                    "CVE-2022-22965", Severity.CRITICAL, "Spring4Shell — execução remota de código via data binding"),
            new KnownVulnerability("maven", "jackson-databind", List.of("2.12.7.1", "2.13.4.2"),
                    "CVE-2022-42003", Severity.HIGH, "Esgotamento de recursos na desserialização"),
            new KnownVulnerability("maven", "commons-text", List.of("1.10.0"),
                    "CVE-2022-42889", Severity.CRITICAL, "Text4Shell — execução remota de código via interpolação"),
            new KnownVulnerability("maven", "commons-collections", List.of("3.2.2"),
                    "CVE-2015-6420", Severity.HIGH, "Desserialização insegura via InvokerTransformer"),
            new KnownVulnerability("maven", "struts2-core", List.of("2.5.33", "6.3.0.2"),
                    "CVE-2023-50164", Severity.CRITICAL, "Execução remota de código via upload de arquivo"),
            new KnownVulnerability("maven", "xstream", List.of("1.4.20"),
                    "CVE-2022-40151", Severity.HIGH, "Esgotamento de pilha (DoS) e desserialização insegura"),
            new KnownVulnerability("maven", "shiro-core", List.of("1.13.0", "2.0.0"),
                    "CVE-2023-46750", Severity.HIGH, "Bypass de autenticação/redirecionamento aberto"),
            new KnownVulnerability("maven", "snakeyaml", List.of("2.0"),
                    "CVE-2022-1471", Severity.HIGH, "Execução de código na desserialização de YAML não confiável"),
            // ---- npm
            new KnownVulnerability("npm", "lodash", List.of("4.17.21"),
                    "CVE-2021-23337", Severity.HIGH, "Injeção de comando via template"),
            new KnownVulnerability("npm", "minimist", List.of("1.2.6"),
                    "CVE-2021-44906", Severity.CRITICAL, "Prototype pollution"),
            new KnownVulnerability("npm", "jsonwebtoken", List.of("9.0.0"),
                    "CVE-2022-23529", Severity.HIGH, "Validação insuficiente de chave leva a falsificação de tokens"),
            new KnownVulnerability("npm", "node-fetch", List.of("2.6.7", "3.1.1"),
                    "CVE-2022-0235", Severity.HIGH, "Exposição de cabeçalhos sensíveis em redirecionamentos"),
            new KnownVulnerability("npm", "moment", List.of("2.29.4"),
                    "CVE-2022-31129", Severity.HIGH, "ReDoS no parsing de datas"),
            new KnownVulnerability("npm", "axios", List.of("1.6.0"),
                    "CVE-2023-45857", Severity.MEDIUM, "Vazamento do token CSRF (XSRF-TOKEN) para outros hosts")
    );

    /** Cliente OSV.dev; {@code null} (ou desligado) = usa só a base interna. */
    @Inject
    OsvClient osv;

    @Override
    public AnalyzerResult analyze(Path projectPath, AuditRun auditRun) {
        Instant start = Instant.now();
        List<Finding> findings = new ArrayList<>();

        List<Dependency> deps = DependencyExtractor.extract(projectPath);
        LOG.infof("%d dependências encontradas nos manifestos", deps.size());

        boolean osvDone = false;
        if (osv != null && osv.isEnabled() && !deps.isEmpty()) {
            try {
                findings.addAll(toFindings(osv.lookup(deps), auditRun));
                osvDone = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Consulta ao OSV interrompida; usando a base interna");
            } catch (Exception e) {
                LOG.warnf("OSV.dev indisponível (%s); usando a base interna reduzida", e.getMessage());
            }
        }
        if (!osvDone) {
            for (Dependency d : deps) {
                match(ecosystemKey(d), d.shortName(), d.version(), d.filePath(), auditRun)
                        .ifPresent(findings::add);
            }
        }

        LOG.infof("OWASP Dependency Analyzer encontrou %d findings (fonte: %s)", findings.size(),
                osvDone ? "OSV.dev" : "base interna");
        return new AnalyzerResult(findings, ANALYZER_NAME, Duration.between(start, Instant.now()));
    }

    private static String ecosystemKey(Dependency d) {
        return d.ecosystem().equals("Maven") ? "maven" : d.ecosystem().equals("npm") ? "npm" : d.ecosystem();
    }

    /** Um finding por dependência (severidade = a maior; lista os ids e a versão mínima corrigida). */
    List<Finding> toFindings(Map<Dependency, List<OsvVulnerability>> found, AuditRun auditRun) {
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<Dependency, List<OsvVulnerability>> e : found.entrySet()) {
            Dependency dep = e.getKey();
            List<OsvVulnerability> vulns = new ArrayList<>(e.getValue());
            vulns.sort((x, y) -> severityRank(x.severity()) != severityRank(y.severity())
                    ? Integer.compare(severityRank(y.severity()), severityRank(x.severity()))
                    : x.id().compareTo(y.id()));
            OsvVulnerability top = vulns.get(0);

            String bestFix = null;
            for (OsvVulnerability v : vulns) {
                if (v.fixedVersion() != null && (bestFix == null
                        || compare(parseVersion(v.fixedVersion()), parseVersion(bestFix)) > 0)) {
                    bestFix = v.fixedVersion();
                }
            }
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < Math.min(vulns.size(), MAX_LISTED_IDS); i++) {
                ids.append(i == 0 ? "" : ", ").append(vulns.get(i).cve() != null ? vulns.get(i).cve() : vulns.get(i).id());
            }
            if (vulns.size() > MAX_LISTED_IDS) {
                ids.append(" e mais ").append(vulns.size() - MAX_LISTED_IDS);
            }
            Finding f = new Finding();
            f.setAuditRun(auditRun);
            f.setCategory(Category.DEPENDENCY);
            f.setSeverity(top.severity());
            f.setTitle("Dependência vulnerável: " + dep.name() + " " + dep.version()
                    + (vulns.size() > 1 ? " (" + vulns.size() + " vulnerabilidades)" : ""));
            f.setDescription(top.summary() + ". Vulnerabilidades: " + ids + "."
                    + (bestFix != null ? " Atualize para " + bestFix + " ou superior." : " Ainda sem versão corrigida informada."));
            f.setFilePath(dep.filePath());
            f.setCveId(top.cve() != null ? top.cve() : top.id());
            f.setRuleId("osv-" + dep.name());
            f.setToolSource(ANALYZER_NAME);
            findings.add(f);
        }
        return findings;
    }

    private static int severityRank(Severity s) {
        return switch (s) { case CRITICAL -> RANK_CRITICAL; case HIGH -> RANK_HIGH; case MEDIUM -> RANK_MEDIUM; case LOW -> RANK_LOW; default -> 0; };
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public boolean supports(Stack stack) {
        return stack != Stack.COBOL; // Maven, npm, Gradle, pip e NuGet
    }

    // ------------------------------------------------------------------ npm (utilitário)

    /** "^4.17.15" / "~1.2.3" / ">=2.0.0" / "1.2.3" → "4.17.15"... ; ranges não numéricos ("latest", "*", git urls) → null. */
    static String cleanNpmRange(String declared) {
        if (declared == null) {
            return null;
        }
        Matcher m = Pattern.compile("^[\\^~>=<\\s]*v?(\\d+(?:\\.\\d+){0,3})").matcher(declared.trim());
        return m.find() ? m.group(1) : null;
    }

    // ------------------------------------------------------------------ comparação de versões

    private java.util.Optional<Finding> match(String ecosystem, String artifactId, String version,
                                               String filePath, AuditRun auditRun) {
        for (KnownVulnerability vuln : KNOWN_VULNERABILITIES) {
            if (vuln.ecosystem().equals(ecosystem) && vuln.artifactId().equals(artifactId)
                    && isVulnerable(version, vuln.fixedVersions())) {
                Finding finding = new Finding();
                finding.setAuditRun(auditRun);
                finding.setCategory(Category.DEPENDENCY);
                finding.setSeverity(vuln.severity());
                finding.setTitle("Dependência vulnerável: " + artifactId + " " + version);
                finding.setDescription(vuln.description() + ". Atualize para "
                        + String.join(" ou ", vuln.fixedVersions()) + " (ou superior na mesma linha de release).");
                finding.setFilePath(filePath);
                finding.setCveId(vuln.cve());
                finding.setRuleId("owasp-dependency-" + vuln.cve() + "-" + artifactId);
                finding.setToolSource(ANALYZER_NAME);
                return java.util.Optional.of(finding);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Uma versão é vulnerável se for menor que a versão corrigida da <i>sua</i> linha de release
     * (mesmo major.minor). Se a linha não tiver correção listada, é vulnerável quando for anterior
     * à <b>maior</b> versão corrigida conhecida <i>do mesmo major</i> (ex.: log4j-core 2.14.1 &lt; 2.17.1; node-fetch
     * 2.7.0 não é vulnerável, pois 2.6.7 já corrigiu o major 2). Sem correção no major, vale a maior de todas.
     */
    static boolean isVulnerable(String version, List<String> fixedVersions) {
        int[] v = parseVersion(version);
        if (v.length == 0) {
            return false;
        }
        int[] highestFix = null;
        int[] highestFixSameMajor = null;
        for (String fixed : fixedVersions) {
            int[] f = parseVersion(fixed);
            if (f.length >= 2 && v.length >= 2 && f[0] == v[0] && f[1] == v[1]) {
                return compare(v, f) < 0;
            }
            if (highestFix == null || compare(f, highestFix) > 0) {
                highestFix = f;
            }
            if (f.length >= 1 && f[0] == v[0]
                    && (highestFixSameMajor == null || compare(f, highestFixSameMajor) > 0)) {
                highestFixSameMajor = f;
            }
        }
        int[] reference = highestFixSameMajor != null ? highestFixSameMajor : highestFix;
        return reference != null && compare(v, reference) < 0;
    }

    /** Extrai os componentes numéricos iniciais ("5.3.18.RELEASE" → [5,3,18]; "2.13.4.2" → [2,13,4,2]). */
    static int[] parseVersion(String version) {
        List<Integer> parts = new ArrayList<>();
        for (String token : version.trim().split("[.\\-+_]")) {
            if (!token.matches("\\d{1,9}")) {
                break;
            }
            parts.add(Integer.parseInt(token));
        }
        return parts.stream().mapToInt(Integer::intValue).toArray();
    }

    static int compare(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }
}
