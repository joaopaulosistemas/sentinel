package com.sentinel.audit.dto;

import com.sentinel.audit.model.Severity;

import java.time.LocalDateTime;
import java.util.Map;

/** Um ponto da linha do tempo de score de um sistema. */
public record TrendPoint(Long auditId, String branch, LocalDateTime completedAt,
                         Double overall, Double security, Double quality, Double architecture,
                         Double dependencies, Double observability, Double performance,
                         Map<Severity, Long> findings) {}
