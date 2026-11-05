package com.sentinel.audit.analyzer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Escolhe o motor que executa as regras YAML: Opengrep (padrão) ou Semgrep (alternativa).
 * <p>
 * Os dois leem o mesmo formato de regra e devolvem o mesmo JSON, mas a linha de comando difere em pontos que
 * quebram a execução se ignorados: o Opengrep não aceita {@code --metrics} e, como binário empacotado, lê os
 * arquivos de regra com a codificação do ambiente (sem {@code LC_ALL} em UTF-8 ele falha nas regras com acento).
 * Este ponto único concentra essas diferenças para o analisador, o laboratório de regras e os testes.
 * <p>
 * A variável {@value #ENV_ENGINE} força um motor: {@code opengrep}, {@code semgrep} ou, vazia, o primeiro que
 * estiver no PATH (Opengrep antes de Semgrep).
 */
public final class SastEngine {

    /** Variável de ambiente que força o motor ({@code opengrep} ou {@code semgrep}). */
    public static final String ENV_ENGINE = "SENTINEL_SAST_ENGINE";

    /**
     * Nome do analisador SAST nos achados, no relatório e na tela. O motor é o Opengrep (o Semgrep é só alternativa),
     * e o nome é sempre este para o histórico e a cobertura por área não se dividirem entre dois nomes.
     */
    public static final String TOOL_NAME = "Opengrep";

    private static final String NOME_ANTIGO = "Semgrep";

    /** {@code true} para o nome atual ({@value #TOOL_NAME}) e para o antigo, que bancos antigos ainda podem ter. */
    public static boolean isSastTool(String name) {
        return name != null && (TOOL_NAME.equalsIgnoreCase(name) || NOME_ANTIGO.equalsIgnoreCase(name));
    }

    /** Mesma ferramenta, tratando o nome antigo e o atual do SAST como um só. */
    public static boolean sameTool(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b) || (isSastTool(a) && isSastTool(b));
    }

    private static final List<String> SUFIXOS = List.of("", ".exe", ".cmd", ".bat");

    /** O motor encontrado: caminho do executável e se é o Opengrep. */
    public record Engine(Path executable, boolean opengrep) {

        /** Nome para mensagens e notas de resultado. */
        public String displayName() {
            return opengrep ? "Opengrep" : "Semgrep";
        }

        /** Opções que desligam telemetria e checagem de versão pela rede, na forma que cada motor aceita. */
        public List<String> quietFlags() {
            return opengrep
                    ? List.of("--disable-version-check")
                    : List.of("--metrics=off", "--disable-version-check");
        }
    }

    private SastEngine() {
    }

    /** Motor disponível no PATH, respeitando {@value #ENV_ENGINE}. */
    public static Optional<Engine> find(String pathEnv) {
        return find(pathEnv, System.getenv(ENV_ENGINE));
    }

    static Optional<Engine> find(String pathEnv, String preferred) {
        String wanted = preferred == null ? "" : preferred.trim().toLowerCase(Locale.ROOT);
        if (!wanted.equals("semgrep")) {
            Optional<Engine> opengrep = locate("opengrep", pathEnv).map(p -> new Engine(p, true));
            if (opengrep.isPresent() || wanted.equals("opengrep")) {
                return opengrep;
            }
        }
        return locate("semgrep", pathEnv).map(p -> new Engine(p, false));
    }

    /** Variáveis de ambiente do processo do motor: sem telemetria e com leitura de regras em UTF-8. */
    public static void prepare(Map<String, String> environment) {
        environment.put("SEMGREP_SEND_METRICS", "off");
        environment.put("SEMGREP_ENABLE_VERSION_CHECK", "0");
        environment.put("LC_ALL", "C.UTF-8");
        environment.put("PYTHONUTF8", "1");
    }

    /** Procura o executável no PATH sem rodá-lo (a checagem de versão do motor usa a rede e pode demorar minutos). */
    private static Optional<Path> locate(String name, String pathEnv) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String suffix : SUFIXOS) {
                Path candidate = Paths.get(dir, name + suffix);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }
}
