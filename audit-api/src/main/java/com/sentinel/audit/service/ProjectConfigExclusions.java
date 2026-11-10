package com.sentinel.audit.service;

import com.sentinel.audit.analyzer.ProjectExclusions;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;
import java.util.function.Predicate;

/** Lê o {@code exclude} do {@code .sentinel.yml} do projeto clonado. */
@ApplicationScoped
public class ProjectConfigExclusions implements ProjectExclusions {

    @Override
    public Predicate<String> forProject(Path root) {
        return ProjectConfig.load(root)::isExcluded;
    }
}
