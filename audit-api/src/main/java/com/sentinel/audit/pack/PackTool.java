package com.sentinel.audit.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Ferramenta de linha de comando para quem escreve pacotes de regras (não precisa do servidor no ar):
 * <pre>
 *   sentinel-pack new &lt;id&gt; [--dir &lt;pasta&gt;]          cria um pacote de exemplo
 *   sentinel-pack validate [--strict] &lt;pacote|pasta&gt;  mesmas checagens do servidor (--strict: regra sem teste é erro)
 *   sentinel-pack test &lt;pacote&gt;                     roda os testes das regras (semgrep --test)
 *   sentinel-pack keygen &lt;prefixo&gt;                   gera &lt;prefixo&gt;.key e &lt;prefixo&gt;.pub (Ed25519)
 *   sentinel-pack pack &lt;pacote&gt; [--key &lt;arquivo&gt;]     grava pack.lock (e pack.sig, com chave)
 *   sentinel-pack verify &lt;pacote&gt; [--public-key &lt;arquivo&gt;]
 * </pre>
 * Códigos de saída: 0 ok, 1 problema encontrado no pacote, 2 uso incorreto ou ambiente.
 */
public final class PackTool {

    private PackTool() {}

    public static void main(String[] args) {
        System.exit(run(args, new PrintStream(System.out, true, StandardCharsets.UTF_8),
                new PrintStream(System.err, true, StandardCharsets.UTF_8)));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            usage(out);
            return args.length == 0 ? 2 : 0;
        }
        try {
            return switch (args[0]) {
                case "new" -> newPack(args, out, err);
                case "validate" -> validate(args, out, err);
                case "test" -> test(args, out, err);
                case "keygen" -> keygen(args, out, err);
                case "pack" -> pack(args, out, err);
                case "verify" -> verify(args, out, err);
                default -> {
                    err.println("Comando desconhecido: " + args[0]);
                    usage(err);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 2;
        } catch (Exception e) {
            err.println("Erro: " + e.getMessage());
            return 2;
        }
    }

    private static void usage(PrintStream out) {
        out.println("""
                Uso: sentinel-pack <comando> [opções]
                  new <id> [--dir <pasta>]               cria um pacote de exemplo
                  validate [--strict] <pacote|pasta>     valida (mesmas checagens do servidor; --strict: regra sem teste é erro)
                  test <pacote>                          roda os testes das regras (precisa do semgrep)
                  keygen <prefixo>                       gera <prefixo>.key e <prefixo>.pub (Ed25519)
                  pack <pacote> [--key <arquivo.key>]    grava pack.lock (e pack.sig, se houver chave)
                  verify <pacote> [--public-key <arquivo.pub>]""");
    }

    // ------------------------------------------------------------------ validate

    private static int validate(String[] rawArgs, PrintStream out, PrintStream err) {
        boolean strict = java.util.Arrays.asList(rawArgs).contains("--strict");
        String[] args = java.util.Arrays.stream(rawArgs).filter(a -> !a.equals("--strict")).toArray(String[]::new);
        Path target = Paths.get(arg(args, 1, "validate [--strict] <pacote|pasta>")).toAbsolutePath().normalize();
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("Pasta não encontrada: " + target);
        }
        List<Path> dirs = new ArrayList<>();
        if (Files.isRegularFile(target.resolve("pack.yml"))) {
            dirs.add(target);
        } else {
            try (Stream<Path> s = Files.list(target)) {
                s.filter(Files::isDirectory).filter(d -> !d.getFileName().toString().equals("profiles")).sorted().forEach(dirs::add);
            } catch (IOException e) {
                throw new IllegalArgumentException("Não foi possível listar " + target, e);
            }
        }
        int bad = 0;
        for (Path d : dirs) {
            PackLoader.Report r = PackLoader.inspect(d);
            if (r.ok()) {
                out.printf("OK     %s v%s — %d regra(s) Semgrep, %d remediação(ões)%n", r.pack().id(),
                        r.pack().manifest().version(), r.pack().semgrepRuleCount(), r.pack().remediation().size());
            } else {
                bad++;
                out.printf("ERRO   %s%n", d.getFileName());
                r.errors().forEach(e -> err.println("  - " + e));
            }
            if (strict && r.ok() && !r.warnings().isEmpty()) {
                bad++;
                err.printf("ERRO   %s (--strict: avisos viram erros)%n", r.pack().id());
                r.warnings().forEach(w -> err.println("  - " + w));
            } else {
                r.warnings().forEach(w -> out.println("  aviso: " + w));
            }
        }
        if (Files.isDirectory(target.resolve("profiles"))) {
            java.util.Map<String, List<String>> rejected = new java.util.LinkedHashMap<>();
            ProfileLoader.loadAll(target, rejected);
            for (var e : rejected.entrySet()) {
                bad++;
                err.println("ERRO   " + e.getKey());
                e.getValue().forEach(m -> err.println("  - " + m));
            }
        }
        if (dirs.isEmpty()) {
            err.println("Nenhum pacote encontrado em " + target);
            return 1;
        }
        return bad == 0 ? 0 : 1;
    }

    // ------------------------------------------------------------------ test

    private static int test(String[] args, PrintStream out, PrintStream err) throws IOException, InterruptedException {
        Path dir = Paths.get(arg(args, 1, "test <pacote>")).toAbsolutePath().normalize();
        Path rules = dir.resolve("rules");
        Path tests = dir.resolve("tests");
        if (!Files.isDirectory(rules) || !Files.isDirectory(tests)) {
            err.println("O pacote precisa das pastas rules/ e tests/ para ser testado");
            return 1;
        }
        PackLoader.Report r = PackLoader.inspect(dir);
        if (!r.ok()) {
            r.errors().forEach(e -> err.println("  - " + e));
            err.println("Corrija os erros de validação antes de testar (sentinel-pack validate)");
            return 1;
        }
        ProcessBuilder pb = new ProcessBuilder("semgrep", "--test", "--config", rules.toString(), tests.toString(),
                "--metrics=off", "--disable-version-check");
        pb.environment().put("SEMGREP_SEND_METRICS", "off");
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            err.println("Semgrep não encontrado no PATH (instale com: pip install semgrep)");
            return 2;
        }
        try (InputStream in = p.getInputStream()) {
            out.print(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        if (!p.waitFor(10, TimeUnit.MINUTES)) {
            p.destroyForcibly();
            err.println("Os testes passaram de 10 minutos e foram encerrados");
            return 2;
        }
        return p.exitValue() == 0 ? 0 : 1;
    }

    // ------------------------------------------------------------------ chaves, lock, verify

    private static int keygen(String[] args, PrintStream out, PrintStream err) throws Exception {
        String prefix = arg(args, 1, "keygen <prefixo>");
        Path priv = Paths.get(prefix + ".key");
        Path pub = Paths.get(prefix + ".pub");
        if (Files.exists(priv) || Files.exists(pub)) {
            err.println("Já existe " + (Files.exists(priv) ? priv : pub) + "; não vou sobrescrever uma chave");
            return 1;
        }
        String[] keys = PackIntegrity.generateKeyPair();
        Files.writeString(priv, keys[0] + "\n", StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(priv, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // sistema de arquivos sem permissões POSIX
        }
        Files.writeString(pub, keys[1] + "\n", StandardCharsets.UTF_8);
        out.println("Chave privada: " + priv + "  (guarde em cofre; NUNCA publique nem versione)");
        out.println("Chave pública: " + pub + "  (vai em sentinel.audit.packs.public-key / values.yaml)");
        return 0;
    }

    private static int pack(String[] args, PrintStream out, PrintStream err) throws Exception {
        Path dir = Paths.get(arg(args, 1, "pack <pacote> [--key <arquivo>]")).toAbsolutePath().normalize();
        String keyFile = option(args, "--key");
        PackLoader.Report r = PackLoader.inspect(dir);
        if (!r.ok()) {
            r.errors().forEach(e -> err.println("  - " + e));
            err.println("O pacote tem erros de validação; corrija antes de lacrar");
            return 1;
        }
        java.security.PrivateKey key = keyFile == null ? null : PackIntegrity.privateKey(readSmall(Paths.get(keyFile)));
        PackIntegrity.seal(dir, key);
        out.println("Gravado " + dir.resolve(PackIntegrity.LOCK) + (key == null ? "" : " e " + dir.resolve(PackIntegrity.SIG)));
        if (key == null) {
            out.println("Sem --key: o pack.lock detecta alteração, mas não prova quem publicou.");
        }
        return 0;
    }

    private static int verify(String[] args, PrintStream out, PrintStream err) throws Exception {
        Path dir = Paths.get(arg(args, 1, "verify <pacote> [--public-key <arquivo>]")).toAbsolutePath().normalize();
        String pubFile = option(args, "--public-key");
        java.security.PublicKey key = pubFile == null ? null : PackIntegrity.publicKey(readSmall(Paths.get(pubFile)));
        PackIntegrity.Result r = PackIntegrity.verify(dir, key);
        switch (r.state()) {
            case SIGNED -> {
                out.println("OK assinado: hashes e assinatura conferem");
                return 0;
            }
            case CHECKSUM_OK -> {
                out.println("OK hashes conferem" + (pubFile == null ? " (assinatura não conferida: informe --public-key)" : " (pacote sem assinatura)"));
                return pubFile == null ? 0 : 1;
            }
            case UNSIGNED -> {
                err.println("Pacote sem pack.lock (rode: sentinel-pack pack)");
                return 1;
            }
            default -> {
                err.println("INVÁLIDO: " + r.detail());
                return 1;
            }
        }
    }

    // ------------------------------------------------------------------ new

    private static int newPack(String[] args, PrintStream out, PrintStream err) throws IOException {
        String id = arg(args, 1, "new <id> [--dir <pasta>]");
        if (!PackManifest.ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id inválido: use minúsculas, números e hífen (2 a 63 caracteres)");
        }
        String root = option(args, "--dir");
        Path dir = Paths.get(root == null ? "." : root).toAbsolutePath().normalize().resolve(id);
        if (Files.exists(dir)) {
            err.println("Já existe " + dir);
            return 1;
        }
        Files.createDirectories(dir.resolve("rules"));
        Files.createDirectories(dir.resolve("tests"));
        Files.createDirectories(dir.resolve("remediation"));
        Files.writeString(dir.resolve("pack.yml"), "id: " + id + "\nname: \"" + id + "\"\nversion: 1.0.0\n"
                + "description: \"O que este pacote cobre\"\nauthor: \"Seu time\"\nlanguages: [java]\nenabled-by-default: false\n");
        String rule = id.replace('-', '_');
        Files.writeString(dir.resolve("rules/exemplo.yml"), """
                rules:
                  - id: %s-sem-system-exit
                    message: "Não encerre a JVM com System.exit: lance uma exceção e deixe o chamador decidir."
                    severity: WARNING
                    languages: [java]
                    pattern: System.exit(...)
                    metadata:
                      category: quality
                      status: experimental   # aparece no relatório, mas não pesa no score até virar 'stable'
                """.formatted(id));
        Files.writeString(dir.resolve("tests/exemplo.java"), """
                class Exemplo {
                    void ruim() {
                        // ruleid: %s-sem-system-exit
                        System.exit(1);
                    }

                    void bom() {
                        // ok: %s-sem-system-exit
                        throw new IllegalStateException("falhou");
                    }
                }
                """.formatted(id, id));
        Files.writeString(dir.resolve("remediation/remediation.yml"), """
                - key: %s-sem-system-exit
                  summary: "Lance uma exceção em vez de encerrar a JVM; quem chama decide o que fazer."
                  before: "System.exit(1);"
                  after: "throw new IllegalStateException(\\"falhou\\");"
                """.formatted(id));
        out.println("Pacote criado em " + dir);
        out.println("Próximos passos: sentinel-pack validate " + dir + " && sentinel-pack test " + dir);
        return 0;
    }

    // ------------------------------------------------------------------ utilidades

    private static String arg(String[] args, int i, String usage) {
        if (args.length <= i || args[i].startsWith("--")) {
            throw new IllegalArgumentException("Uso: sentinel-pack " + usage);
        }
        return args[i];
    }

    private static String option(String[] args, String name) {
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals(name)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("O parâmetro " + name + " precisa de um valor");
                }
                return args[i + 1];
            }
        }
        return null;
    }

    private static String readSmall(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 10_000) {
            throw new IllegalArgumentException("Arquivo de chave inválido: " + file);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
