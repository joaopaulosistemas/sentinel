package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.evidence.EvidenceAnalyzer;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Leva as evidências do sistema (dump do esquema, estatísticas, cobertura, histograma de memória) para dentro do
 * projeto clonado, em {@code .sentinel/evidence/}, onde o {@link EvidenceAnalyzer} as lê.
 * <p>
 * A pasta de origem é {@code SENTINEL_EVIDENCE_DIR/<nome-do-sistema>/} (o nome vira minúsculo, sem acento, com
 * hífens), com o nome do repositório como segunda opção. Só arquivos comuns da raiz da pasta são copiados: links
 * simbólicos, subpastas e arquivos enormes ficam de fora, e nada sai do diretório configurado. A pasta é só lida.
 */
@ApplicationScoped
public class EvidenceStager {

    private static final String GIT_SUFFIX = ".git";


    private static final Logger LOG = Logger.getLogger(EvidenceStager.class);
    static final long MAX_FILE_BYTES = 200L * 1024 * 1024;
    static final int MAX_FILES = 50;
    static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;

    @ConfigProperty(name = "sentinel.audit.evidence.dir")
    Optional<String> evidenceDir = Optional.empty();

    /** Copia as evidências do sistema para o projeto; devolve quantos arquivos foram copiados (0 se não há pasta). */
    public int stage(String systemName, String repoUrl, Path projectPath) {
        if (evidenceDir.isEmpty() || evidenceDir.get().isBlank()) {
            return 0;
        }
        try {
            Path root = Paths.get(evidenceDir.get()).toRealPath();
            Path source = null;
            for (String candidate : List.of(slug(systemName), slug(repoName(repoUrl)))) {
                if (candidate.isEmpty()) {
                    continue;
                }
                Path dir = root.resolve(candidate).normalize();
                if (dir.startsWith(root) && Files.isDirectory(dir) && !Files.isSymbolicLink(dir)) {
                    source = dir;
                    break;
                }
            }
            if (source == null) {
                return 0;
            }
            Path target = projectPath.resolve(EvidenceAnalyzer.DIR);
            Files.createDirectories(target);
            int copied = 0;
            long total = 0;
            try (Stream<Path> files = Files.list(source)) {
                for (Path f : files.sorted().toList()) {
                    if (!Files.isRegularFile(f, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    long size = Files.size(f);
                    if (size > MAX_FILE_BYTES || total + size > MAX_TOTAL_BYTES || copied >= MAX_FILES) {
                        LOG.warnf("Evidência %s ignorada (limite de tamanho ou de quantidade)", f.getFileName());
                        continue;
                    }
                    Files.copy(f, target.resolve(f.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                    total += size;
                }
            }
            LOG.infof("Evidências de '%s': %d arquivo(s) copiados de %s", systemName, copied, source);
            return copied;
        } catch (IOException | RuntimeException e) {
            LOG.warnf("Não foi possível preparar as evidências de '%s': %s", systemName, e.getMessage());
            return 0;
        }
    }

    static String repoName(String repoUrl) {
        if (repoUrl == null) {
            return "";
        }
        String s = repoUrl.strip().replaceAll("/+$", "");
        s = s.substring(Math.max(s.lastIndexOf('/'), s.lastIndexOf(':')) + 1);
        return s.endsWith(GIT_SUFFIX) ? s.substring(0, s.length() - GIT_SUFFIX.length()) : s;
    }

    /** "Sistema de Câmbio" -> "sistema-de-cambio". */
    static String slug(String name) {
        if (name == null) {
            return "";
        }
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return s.equals(".") || s.equals("..") ? "" : s;
    }
}
