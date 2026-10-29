package com.sentinel.audit.settings;

import com.sentinel.audit.settings.SettingDef.Kind;
import com.sentinel.audit.settings.SettingDef.Option;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Catálogo das configurações que a tela mostra. É a única lista: a tela, a validação e a leitura no
 * {@link DbConfigSource} saem daqui. Os padrões repetem os de application.properties.
 */
public final class SettingsCatalog {

    public static final String SEG = "seg";
    public static final String BD = "bd";
    public static final String FERR = "ferr";
    public static final String PACOTES = "pacotes";
    public static final String ORIGEM = "origem";

    /** Nomes que a ferramenta de verificações do Trivy aceita. */
    static final Set<String> TRIVY_SCANNERS = Set.of("vuln", "misconfig", "secret", "license");
    /** Pacotes embutidos, mantidos neste repositório, cujas ferramentas podem rodar sem liberar pacotes de terceiros. */
    static final Set<String> PACOTES_COM_FERRAMENTA = Set.of("market-tools", "java-google-style");
    private static final Pattern ID_PACOTE = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private static final int TIMEOUT_MIN = 10;
    private static final int TIMEOUT_MAX = 7200;
    private static final int CURTO_MAX = 600;
    private static final int ACHADOS_MAX = 100000;
    private static final int LINHAS_FUNCAO_MAX = 5000;
    private static final int LINHAS_COMPONENTE_MAX = 10000;
    private static final int PARAMETROS_MAX = 50;
    private static final int COMPLEXIDADE_MAX = 200;
    private static final int ANINHAMENTO_MAX = 20;
    private static final int PERCENTUAL_MAX = 100;

    private static final List<SettingDef> ALL = build();
    private static final Map<String, SettingDef> BY_KEY = index(ALL);

    private SettingsCatalog() {
    }

    public static List<SettingDef> all() {
        return ALL;
    }

    public static Optional<SettingDef> find(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** Devolve a mensagem de erro, ou null se o valor é aceito para essa configuração. */
    public static String validate(SettingDef def, String value) {
        if (!def.editable()) {
            return def.label() + " só pode ser alterada no ambiente (" + def.key() + ")";
        }
        if (value == null) {
            return def.label() + ": valor ausente";
        }
        String v = value.trim();
        return switch (def.kind()) {
            case BOOL -> v.equals("true") || v.equals("false") ? null : def.label() + ": use true ou false";
            case NUMBER -> validateNumber(def, v);
            case ENUM -> def.options().stream().anyMatch(o -> o.value().equals(v)) ? null : def.label() + ": opção inválida";
            case TEXT -> validateText(def, v);
        };
    }

    private static String validateNumber(SettingDef def, String v) {
        try {
            long n = Long.parseLong(v);
            if (n < def.min() || n > def.max()) {
                return def.label() + ": use um valor entre " + def.min() + " e " + def.max();
            }
            return null;
        } catch (NumberFormatException e) {
            return def.label() + ": use um número inteiro";
        }
    }

    private static String validateText(SettingDef def, String v) {
        return switch (def.key()) {
            case "TRIVY_SCANNERS" -> validateTrivyScanners(def, v);
            case "OSV_URL" -> httpUrl(v) ? null : def.label() + ": informe uma URL http ou https";
            case "PACKS_DISABLED" -> firstInvalid(csv(v), s -> !ID_PACOTE.matcher(s).matches(),
                    s -> def.label() + ": '" + s + "' não é um id de pacote");
            case "PACKS_TRUSTED_TOOL_PACKS" -> firstInvalid(csv(v), s -> !PACOTES_COM_FERRAMENTA.contains(s),
                    s -> def.label() + ": '" + s + "' não é um pacote embutido com ferramenta (use "
                            + String.join(", ", new java.util.TreeSet<>(PACOTES_COM_FERRAMENTA)) + ")");
            default -> v.length() > 1000 ? def.label() + ": texto longo demais" : null;
        };
    }

    private static String validateTrivyScanners(SettingDef def, String v) {
        String invalid = firstInvalid(csv(v), s -> !TRIVY_SCANNERS.contains(s),
                s -> def.label() + ": '" + s + "' não é válido (use " + String.join(", ", new java.util.TreeSet<>(TRIVY_SCANNERS)) + ")");
        if (invalid != null) {
            return invalid;
        }
        return csv(v).isEmpty() ? def.label() + ": informe ao menos uma verificação" : null;
    }

    /** A mensagem para o primeiro item inválido, ou nulo se todos servem. */
    private static String firstInvalid(List<String> items, java.util.function.Predicate<String> invalid,
                                       java.util.function.Function<String, String> message) {
        for (String s : items) {
            if (invalid.test(s)) {
                return message.apply(s);
            }
        }
        return null;
    }

    static List<String> csv(String v) {
        List<String> out = new ArrayList<>();
        for (String s : v.split(",")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    private static boolean httpUrl(String v) {
        try {
            URI u = URI.create(v);
            return ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && u.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Map<String, SettingDef> index(List<SettingDef> list) {
        Map<String, SettingDef> m = new LinkedHashMap<>();
        for (SettingDef d : list) {
            if (m.put(d.key(), d) != null) {
                throw new IllegalStateException("Configuração repetida no catálogo: " + d.key());
            }
        }
        return m;
    }

    // ---- construção ----

    /** Identificação e textos de uma configuração, comuns a todos os tipos. */
    private record Head(String key, String prop, String section, String group, String label, String help) { }

    private static SettingDef ro(Head h, String def, boolean secret) {
        return new SettingDef(h.key(), h.prop(), h.section(), h.group(), h.label(), h.help(), Kind.TEXT, def, List.of(), "", 0, 0, false, secret);
    }

    private static SettingDef bool(Head h, boolean def) {
        return new SettingDef(h.key(), h.prop(), h.section(), h.group(), h.label(), h.help(), Kind.BOOL, String.valueOf(def), List.of(), "", 0, 0, true, false);
    }

    private static SettingDef num(Head h, String def, int min, int max, String unit) {
        return new SettingDef(h.key(), h.prop(), h.section(), h.group(), h.label(), h.help(), Kind.NUMBER, def, List.of(), unit, min, max, true, false);
    }

    private static SettingDef text(Head h, String def) {
        return new SettingDef(h.key(), h.prop(), h.section(), h.group(), h.label(), h.help(), Kind.TEXT, def, List.of(), "", 0, 0, true, false);
    }

    private static SettingDef choice(Head h, String def, List<Option> options) {
        return new SettingDef(h.key(), h.prop(), h.section(), h.group(), h.label(), h.help(), Kind.ENUM, def, options, "", 0, 0, true, false);
    }

    private static List<SettingDef> build() {
        List<SettingDef> l = new ArrayList<>();
        accessAndDatabase(l);
        analysisTools(l);
        rulePacks(l);
        sourceAndEvidence(l);
        return List.copyOf(l);
    }

    /** Acesso à API, login corporativo e banco de dados. */
    private static void accessAndDatabase(List<SettingDef> l) {
        String p = "sentinel.audit.";
        // Segurança e acesso (somente leitura: são a base para a API subir e proteger a si mesma)
        l.add(ro(new Head("API_KEY", p + "api-key", SEG, "Acesso à API", "Chave da API", "Exigida em toda a API (header X-API-Key ou Authorization: Bearer). Vazia, a API fica aberta."), "", true));
        l.add(ro(new Head("SECRETS_KEY", p + "secrets-key", SEG, "Acesso à API", "Chave dos segredos dos sistemas", "Cifra o token Git, o segredo de webhook e a URL de notificação de cada sistema. Vazia, usa a chave da API."), "", true));
        l.add(ro(new Head("CORS_ORIGINS", "quarkus.http.cors.origins", SEG, "Acesso à API", "Origens permitidas (CORS)", "Só necessário se o dashboard for servido de outra origem."), "http://localhost:3000", false));
        l.add(ro(new Head("OIDC_AUTH_SERVER_URL", "quarkus.oidc.auth-server-url", SEG, "Login corporativo (OIDC)", "URL do servidor de login", "Realm do Keycloak ou equivalente. Vazio, o login corporativo fica desligado."), "", false));
        l.add(ro(new Head("OIDC_CLIENT_ID", "quarkus.oidc.client-id", SEG, "Login corporativo (OIDC)", "Client ID", "Identificador do cliente cadastrado no servidor de login."), "sentinel-audit", false));

        // Banco de dados (somente leitura: a API precisa dele para ler qualquer configuração)
        l.add(ro(new Head("DB_URL", "quarkus.datasource.jdbc.url", BD, "PostgreSQL", "URL de conexão", "Endereço JDBC do banco do Sentinel."), "jdbc:postgresql://127.0.0.1:5432/sentinel_audit", false));
        l.add(ro(new Head("DB_USER", "quarkus.datasource.username", BD, "PostgreSQL", "Usuário", "Usuário do banco."), "sentinel_audit", false));
        l.add(ro(new Head("DB_PASSWORD", "quarkus.datasource.password", BD, "PostgreSQL", "Senha", "Use senha forte fora do ambiente local."), "sentinel_audit", true));
        l.add(ro(new Head("DB_SCHEMA_MODE", "quarkus.hibernate-orm.database.generation", BD, "PostgreSQL", "Modo do esquema", "Como o Hibernate trata as tabelas ao subir."), "update", false));
    }

    /** Ferramentas de análise. */
    private static void analysisTools(List<SettingDef> l) {
        String p = "sentinel.audit.";
        // Ferramentas de análise
        l.add(num(new Head("SEMGREP_TIMEOUT", p + "semgrep.timeout-seconds", FERR, "Semgrep", "Tempo limite", "Segundos antes de encerrar a análise."), "600", TIMEOUT_MIN, TIMEOUT_MAX, "s"));
        l.add(bool(new Head("TRIVY_ENABLED", p + "trivy.enabled", FERR, "Trivy", "Ligado", "Dependências, configuração e infraestrutura como código."), true));
        l.add(text(new Head("TRIVY_SCANNERS", p + "trivy.scanners", FERR, "Trivy", "Verificações", "Lista separada por vírgula: vuln, misconfig, secret, license."), "vuln,misconfig"));
        l.add(num(new Head("TRIVY_TIMEOUT_SECONDS", p + "trivy.timeout-seconds", FERR, "Trivy", "Tempo limite", "Segundos antes de encerrar."), "900", TIMEOUT_MIN, TIMEOUT_MAX, "s"));
        l.add(bool(new Head("GITLEAKS_ENABLED", p + "gitleaks.enabled", FERR, "Gitleaks", "Ligado", "Procura segredos no código e no histórico."), true));
        l.add(num(new Head("GITLEAKS_TIMEOUT_SECONDS", p + "gitleaks.timeout-seconds", FERR, "Gitleaks", "Tempo limite", "Segundos antes de encerrar."), "600", TIMEOUT_MIN, TIMEOUT_MAX, "s"));
        l.add(bool(new Head("OSV_ENABLED", p + "osv.enabled", FERR, "Dependências (OSV.dev)", "Ligado", "Só nome e versão dos pacotes são enviados. Em rede fechada, desligue ou use um espelho interno."), true));
        l.add(text(new Head("OSV_URL", p + "osv.url", FERR, "Dependências (OSV.dev)", "Endereço", "Troque por um espelho interno se precisar."), "https://api.osv.dev"));
        l.add(num(new Head("OSV_TIMEOUT", p + "osv.timeout-seconds", FERR, "Dependências (OSV.dev)", "Tempo limite", "Segundos por consulta."), "20", 1, CURTO_MAX, "s"));
        l.add(bool(new Head("PMD_ENABLED", p + "pmd.enabled", FERR, "PMD (Java)", "Ligado", "Só roda se o executável estiver instalado."), true));
        l.add(num(new Head("PMD_TIMEOUT_SECONDS", p + "pmd.timeout-seconds", FERR, "PMD (Java)", "Tempo limite", "Segundos antes de encerrar."), "600", TIMEOUT_MIN, TIMEOUT_MAX, "s"));
        l.add(ro(new Head("PMD_RULESET", p + "pmd.ruleset", FERR, "PMD (Java)", "Conjunto de regras", "Caminho de um ruleset próprio. Vazio, usa o do Sentinel. Só se muda no ambiente."), "", false));
        l.add(num(new Head("PMD_MAX_FINDINGS", p + "pmd.max-findings", FERR, "PMD (Java)", "Máximo de achados", "Limite por auditoria."), "500", 1, ACHADOS_MAX, ""));
        l.add(bool(new Head("ESLINT_ENABLED", p + "eslint.enabled", FERR, "ESLint (TypeScript)", "Ligado", "Só roda se o ESLint estiver instalado na imagem."), true));
        l.add(choice(new Head("ESLINT_CONFIG", p + "eslint.config", FERR, "ESLint (TypeScript)", "Configuração", "Uma só configuração vale por vez para toda a instalação. Combine com o perfil escolhido no sistema."), "/opt/eslint/eslint.config.mjs", List.of(
                new Option("/opt/eslint/eslint.config.mjs", "Padrão do Sentinel", "/opt/eslint/eslint.config.mjs"),
                new Option("/opt/eslint/eslint.padrao-ts.config.mjs", "Padrão da casa (TypeScript/Angular)", "/opt/eslint/eslint.padrao-ts.config.mjs"),
                new Option("/opt/eslint/eslint.google-angular.config.mjs", "Google + Angular + Sonar", "/opt/eslint/eslint.google-angular.config.mjs"))));
        l.add(num(new Head("ESLINT_TIMEOUT_SECONDS", p + "eslint.timeout-seconds", FERR, "ESLint (TypeScript)", "Tempo limite", "Segundos antes de encerrar."), "600", TIMEOUT_MIN, TIMEOUT_MAX, "s"));
        l.add(num(new Head("ESLINT_MAX_FINDINGS", p + "eslint.max-findings", FERR, "ESLint (TypeScript)", "Máximo de achados", "Limite por auditoria."), "500", 1, ACHADOS_MAX, ""));
        l.add(bool(new Head("CLEANCODE_ENABLED", p + "cleancode.enabled", FERR, "Clean code (métricas internas)", "Ligado", "Sem ferramenta externa: o Sentinel mede sozinho."), true));
        l.add(num(new Head("CLEANCODE_MAX_FUNCTION_LINES", p + "cleancode.max-function-lines", FERR, "Clean code (métricas internas)", "Linhas por função", "Acima disso vira achado."), "50", 1, LINHAS_FUNCAO_MAX, "linhas"));
        l.add(num(new Head("CLEANCODE_MAX_COMPONENT_LINES", p + "cleancode.max-component-lines", FERR, "Clean code (métricas internas)", "Linhas por componente", "Acima disso vira achado."), "100", 1, LINHAS_COMPONENTE_MAX, "linhas"));
        l.add(num(new Head("CLEANCODE_MAX_PARAMETERS", p + "cleancode.max-parameters", FERR, "Clean code (métricas internas)", "Parâmetros por função", "Acima disso vira achado."), "5", 1, PARAMETROS_MAX, ""));
        l.add(num(new Head("CLEANCODE_MAX_COMPLEXITY", p + "cleancode.max-complexity", FERR, "Clean code (métricas internas)", "Complexidade por função", "Complexidade ciclomática máxima."), "10", 1, COMPLEXIDADE_MAX, ""));
        l.add(num(new Head("CLEANCODE_MAX_NESTING", p + "cleancode.max-nesting", FERR, "Clean code (métricas internas)", "Aninhamento", "Níveis de if/for/while dentro de outros."), "4", 1, ANINHAMENTO_MAX, ""));
        l.add(num(new Head("CLEANCODE_MAX_FINDINGS", p + "cleancode.max-findings", FERR, "Clean code (métricas internas)", "Máximo de achados", "Limite por auditoria."), "300", 1, ACHADOS_MAX, ""));
        l.add(ro(new Head("SEMGREP_CONFIG", p + "semgrep.config", FERR, "Semgrep", "Configuração extra", "Regras adicionais do Semgrep (caminho ou registro). Só se muda no ambiente."), "", false));
    }

    /** Pacotes de regras e laboratório. */
    private static void rulePacks(List<SettingDef> l) {
        String p = "sentinel.audit.";
        // Pacotes de regras
        l.add(ro(new Head("PACKS_DIRS", p + "packs.dirs", PACOTES, "Onde buscar pacotes", "Pastas extras", "Pastas com pacotes de regras, separadas por vírgula. Só se muda no ambiente."), "", false));
        l.add(ro(new Head("PACKS_GIT_URL", p + "packs.git-url", PACOTES, "Onde buscar pacotes", "Repositório Git central", "Repositório (somente leitura) com os pacotes da empresa. Só se muda no ambiente."), "", false));
        l.add(ro(new Head("PACKS_GIT_REF", p + "packs.git-ref", PACOTES, "Onde buscar pacotes", "Branch ou tag", "Referência usada no repositório central."), "main", false));
        l.add(ro(new Head("PACKS_GIT_PATH", p + "packs.git-path", PACOTES, "Onde buscar pacotes", "Subpasta", "Pasta dentro do repositório onde ficam os pacotes."), "", false));
        l.add(ro(new Head("PACKS_CACHE_DIR", p + "packs.cache-dir", PACOTES, "Onde buscar pacotes", "Pasta de cache", "Onde a cópia do repositório central é guardada."), "/tmp/sentinel-packs", false));
        l.add(ro(new Head("PACKS_REFRESH_INTERVAL", p + "packs.refresh-interval", PACOTES, "Onde buscar pacotes", "Atualizar a cada", "Intervalo para buscar mudanças no repositório central."), "10m", false));
        l.add(text(new Head("PACKS_DISABLED", p + "packs.disabled", PACOTES, "Ativação e segurança", "Pacotes desativados", "Ids de pacotes que não devem rodar, separados por vírgula."), ""));
        l.add(ro(new Head("PACKS_ALLOW_TOOLS", p + "packs.allow-tools", PACOTES, "Ativação e segurança", "Permitir pacotes com ferramenta externa", "Pacote de terceiros que executa programa só roda se for liberado. Por segurança, só se muda no ambiente."), "false", false));
        l.add(text(new Head("PACKS_TRUSTED_TOOL_PACKS", p + "packs.trusted-tool-packs", PACOTES, "Ativação e segurança", "Pacotes de confiança", "Pacotes embutidos cujas ferramentas (Checkstyle, Hadolint...) rodam mesmo com a opção acima desligada. Aceita market-tools e java-google-style."), "market-tools,java-google-style"));
        l.add(ro(new Head("PACKS_PUBLIC_KEY", p + "packs.public-key", PACOTES, "Ativação e segurança", "Chave pública de assinatura", "Base64. Gere o par com sentinel-pack keygen."), "", false));
        l.add(ro(new Head("PACKS_REQUIRE_SIGNATURE", p + "packs.require-signature", PACOTES, "Ativação e segurança", "Exigir assinatura", "Recusa pacote sem assinatura válida."), "false", false));
        l.add(bool(new Head("PLAYGROUND_ENABLED", p + "playground.enabled", PACOTES, "Laboratório de regras", "Ligado", "Executa o Semgrep sobre texto enviado pelo usuário."), true));
        l.add(num(new Head("PLAYGROUND_TIMEOUT_SECONDS", p + "playground.timeout-seconds", PACOTES, "Laboratório de regras", "Tempo limite", "Segundos por teste."), "30", 1, CURTO_MAX, "s"));
    }

    /** Origem do código, evidências e agendamento. */
    private static void sourceAndEvidence(List<SettingDef> l) {
        String p = "sentinel.audit.";
        // Origem e evidências
        l.add(ro(new Head("CLONE_DIR", p + "git.clone-dir", ORIGEM, "Clone e histórico Git", "Pasta dos clones", "Onde os repositórios são clonados para auditar."), "/tmp/sentinel-audit", false));
        l.add(num(new Head("GIT_CLONE_TIMEOUT", p + "git.clone-timeout-seconds", ORIGEM, "Clone e histórico Git", "Tempo limite do clone", "Segundos."), "300", TIMEOUT_MIN, TIMEOUT_MAX, "s"));
        l.add(num(new Head("GIT_HISTORY_TIMEOUT", p + "git.history-timeout-seconds", ORIGEM, "Clone e histórico Git", "Tempo limite do histórico", "Segundos."), "120", TIMEOUT_MIN, TIMEOUT_MAX, "s"));
        l.add(ro(new Head("SENTINEL_LOCAL_ROOT", p + "local.root", ORIGEM, "Pasta local como origem", "Raiz permitida", "Vazio, a origem por pasta local fica desligada. Só se muda no ambiente (exige montar a pasta no contêiner)."), "", false));
        l.add(ro(new Head("SENTINEL_EVIDENCE_DIR", p + "evidence.dir", ORIGEM, "Evidências offline", "Pasta de evidências", "Uma subpasta por sistema (cobertura, relatórios). Só se muda no ambiente."), "", false));
        l.add(num(new Head("EVIDENCE_MIN_LINE_COVERAGE", p + "evidence.min-line-coverage", ORIGEM, "Evidências offline", "Cobertura mínima de linhas", "Abaixo disso vira achado."), "60", 0, PERCENTUAL_MAX, "%"));
        l.add(bool(new Head("SCHEDULER_ENABLED", p + "scheduler.enabled", ORIGEM, "Agendamento", "Auditorias agendadas", "Desligado, só roda auditoria manual ou por webhook."), true));
    }
}
