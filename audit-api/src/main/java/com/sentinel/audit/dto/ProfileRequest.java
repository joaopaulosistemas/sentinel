package com.sentinel.audit.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Troca o perfil de regras de um sistema (vazio volta ao "default"). */
public record ProfileRequest(
        @Size(max = 63) @Pattern(regexp = "^$|^[a-z0-9][a-z0-9-]{1,62}$", message = "perfil inválido (minúsculas, números e hífen)")
        String profile) {}
