package com.sentinel.audit.analyzer;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Finding;
import com.sentinel.audit.model.Severity;
import com.sentinel.audit.pack.ArchitecturePolicy;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Set;

/** Confere os arquivos que a política de arquitetura exige no projeto (caminho exato ou glob). */
final class ArchRequiredFiles {

    private ArchRequiredFiles() {
    }

    static void check(Path root, Set<String> relPaths, ArchitecturePolicy pol, AuditRun run, List<Finding> out) {
        for (ArchitecturePolicy.RequiredFile rf : pol.requiredFiles()) {
            boolean exists;
            if (rf.path().contains("*")) {
                PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + rf.path());
                exists = relPaths.stream().anyMatch(p -> m.matches(Path.of(p)));
            } else {
                exists = relPaths.contains(rf.path()) || Files.exists(root.resolve(rf.path()), LinkOption.NOFOLLOW_LINKS);
            }
            if (!exists) {
                out.add(ArchitecturePolicyAnalyzer.archFinding(run, new ArchitecturePolicyAnalyzer.ArchPlace(rf.path(), 0, ""), new ArchitecturePolicyAnalyzer.ArchRule("sentinel-arch-required-file",
                        rf.severity() == null ? Severity.LOW : rf.severity(), Category.ARCHITECTURE,
                        "Arquivo obrigatório ausente: " + rf.path(),
                        rf.message() == null ? "A política exige o arquivo " + rf.path() + "." : rf.message())));
            }
        }
    }
}
