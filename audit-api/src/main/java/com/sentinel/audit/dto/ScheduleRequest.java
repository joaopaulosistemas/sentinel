package com.sentinel.audit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Configuração de auditoria automática de um sistema.
 *
 * @param auditIntervalHours a cada quantas horas auditar (0 ou ausente = desligado; máximo 720 = 30 dias)
 * @param branch             branch auditada automaticamente (vazio = main)
 * @param auditOnPush        auditar a cada push na branch (exige o webhook configurado no GitHub/GitLab)
 */
public record ScheduleRequest(
        @Min(0) @Max(720) Integer auditIntervalHours,
        @Size(max = 255) @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9._/-]*$", message = "Nome de branch inválido") String branch,
        Boolean auditOnPush) {}
