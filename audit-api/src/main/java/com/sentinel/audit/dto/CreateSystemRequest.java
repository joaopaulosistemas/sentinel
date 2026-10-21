package com.sentinel.audit.dto;

import com.sentinel.audit.model.Stack;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para criação de um novo sistema auditado.
 */
public class CreateSystemRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @NotBlank
    @Size(max = 500)
    @Pattern(regexp = "^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/[^\\s]+$",
             message = "repoUrl deve ser uma URL https:// de repositório Git")
    private String repoUrl;

    @NotNull
    private Stack stack;

    private String team;

    /** Perfil de regras (ver profiles/ nos pacotes). Vazio = "default". */
    @Size(max = 63)
    @Pattern(regexp = "^$|^[a-z0-9][a-z0-9-]{1,62}$", message = "perfil inválido (minúsculas, números e hífen)")
    private String profile;

    public CreateSystemRequest() {
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public Stack getStack() {
        return stack;
    }

    public void setStack(Stack stack) {
        this.stack = stack;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }
}
