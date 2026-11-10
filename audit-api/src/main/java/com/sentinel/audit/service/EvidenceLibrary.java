package com.sentinel.audit.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * As evidências de um sistema na pasta {@code SENTINEL_EVIDENCE_DIR/<nome-do-sistema>/} (a mesma que o
 * {@link EvidenceStager} lê na auditoria): listar, receber e remover arquivos pela API. Só arquivos comuns da raiz da
 * pasta; o nome nunca sai da pasta do sistema. O conteúdo não é interpretado aqui, quem reconhece o formato é o
 * analisador de evidências.
 */
@ApplicationScoped
public class EvidenceLibrary {

    @ConfigProperty(name = "sentinel.audit.evidence.dir")
    Optional<String> evidenceDir = Optional.empty();

    long maxFileBytes = EvidenceStager.MAX_FILE_BYTES;
    int maxFiles = EvidenceStager.MAX_FILES;

    public record EvidenceFile(String name, long sizeBytes, Instant modifiedAt) { }

    public boolean enabled() {
        return evidenceDir.isPresent() && !evidenceDir.get().isBlank();
    }

    public List<EvidenceFile> list(String systemName, String repoUrl) {
        if (!enabled()) {
            return List.of();
        }
        try {
            Path source = EvidenceStager.findSource(Paths.get(evidenceDir.get()).toRealPath(), systemName, repoUrl);
            if (source == null) {
                return List.of();
            }
            try (Stream<Path> files = Files.list(source)) {
                List<EvidenceFile> found = new ArrayList<>();
                for (Path f : files.filter(x -> Files.isRegularFile(x, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                    found.add(new EvidenceFile(f.getFileName().toString(), Files.size(f), Files.getLastModifiedTime(f).toInstant()));
                }
                return found;
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Grava (ou substitui) um arquivo; recusa nome inválido, arquivo acima do limite e excesso de arquivos. */
    public EvidenceFile save(String systemName, String repoUrl, String fileName, InputStream body) {
        if (!enabled()) {
            throw new IllegalStateException("A pasta de evidências não está configurada neste servidor (SENTINEL_EVIDENCE_DIR)");
        }
        String name = validName(fileName);
        try {
            Path root = Paths.get(evidenceDir.get()).toRealPath();
            Path dir = EvidenceStager.findSource(root, systemName, repoUrl);
            if (dir == null) {
                String slug = EvidenceStager.slug(systemName);
                if (slug.isEmpty()) {
                    throw new IllegalArgumentException("Nome de sistema inválido para a pasta de evidências");
                }
                dir = Files.createDirectories(root.resolve(slug));
            }
            Path target = dir.resolve(name);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && list(systemName, repoUrl).size() >= maxFiles) {
                throw new IllegalArgumentException("Limite de " + maxFiles + " arquivos de evidência por sistema");
            }
            Path tmp = Files.createTempFile(dir, ".envio-", ".tmp");
            try {
                long copied = copyLimited(body, tmp);
                // O horário é lido antes de mover: o rename preserva a data, e em pasta montada do Windows (/mnt/c, 9p)
                // consultar o destino logo após o rename pode falhar com NoSuchFileException, mesmo com o arquivo gravado.
                Instant modified = Files.getLastModifiedTime(tmp).toInstant();
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return new EvidenceFile(name, copied, modified);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean delete(String systemName, String repoUrl, String fileName) {
        if (!enabled()) {
            return false;
        }
        String name = validName(fileName);
        try {
            Path dir = EvidenceStager.findSource(Paths.get(evidenceDir.get()).toRealPath(), systemName, repoUrl);
            if (dir == null) {
                return false;
            }
            Path f = dir.resolve(name);
            return Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS) && Files.deleteIfExists(f);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Maior nome de arquivo aceito, em caracteres. */
    private static final int MAX_NAME_LENGTH = 150;

    private long copyLimited(InputStream in, Path out) throws IOException {
        long total = 0;
        byte[] buf = new byte[64 * 1024];
        try (var os = Files.newOutputStream(out)) {
            int n = in.read(buf);
            while (n > 0) {
                total += n;
                if (total > maxFileBytes) {
                    throw new IllegalArgumentException("Arquivo acima do limite de " + (maxFileBytes / (1024 * 1024)) + " MB");
                }
                os.write(buf, 0, n);
                n = in.read(buf);
            }
        }
        return total;
    }

    private static String validName(String fileName) {
        String n = fileName == null ? "" : fileName.strip();
        if (n.isEmpty() || n.startsWith(".") || n.length() > MAX_NAME_LENGTH || n.matches(".*[/\\\\\\p{Cntrl}].*")) {
            throw new IllegalArgumentException("Nome de arquivo inválido");
        }
        return n;
    }
}
