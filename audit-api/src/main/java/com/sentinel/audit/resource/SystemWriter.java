package com.sentinel.audit.resource;

import com.sentinel.audit.dto.CreateSystemRequest;
import com.sentinel.audit.model.AuditedSystem;
import com.sentinel.audit.model.LocalSnapshot;
import com.sentinel.audit.model.SourceType;
import com.sentinel.audit.pack.PackRegistry;
import com.sentinel.audit.service.IngestionService;
import com.sentinel.audit.service.LocalSource;
import com.sentinel.audit.service.NotificationService;
import com.sentinel.audit.service.SecretBox;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.Locale;

/** Grava num sistema o que veio do cadastro: dados básicos, origem do código, perfil, notificações e segredos (cifrados). */
@ApplicationScoped
public class SystemWriter {

    @Inject
    PackRegistry packRegistry;

    @Inject
    IngestionService ingestionService;

    @Inject
    SecretBox secretBox;

    /** @param create no cadastro o perfil é sempre gravado; na edição só se vier informado */
    void apply(AuditedSystem system, CreateSystemRequest request, boolean create) {
        system.setName(request.name());
        system.setDescription(request.description());
        applySource(system, request);
        system.setStack(request.stack());
        system.setTeam(request.team());
        if (create || request.profile() != null) {
            system.setProfile(validProfile(request.profile()));
        }
        applyGitAccess(system, request);
    }

    /** Perfil conhecido (ou vazio = default); desconhecido é erro 400 para o erro de digitação aparecer na hora. */
    String validProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return null;
        }
        if (!packRegistry.profileExists(profile)) {
            throw new BadRequestException("Perfil não encontrado: " + profile
                    + ". Perfis disponíveis: " + String.join(", ", packRegistry.snapshot().profiles().keySet()));
        }
        return profile.trim().toLowerCase(Locale.ROOT);
    }

    /** Token (cifrado) e hosts do acesso ao repositório: token vazio mantém o salvo; "clearGitToken" apaga. */
    /**
     * Grava de onde vem o código: a URL https (padrão) ou uma pasta local validada contra a raiz do servidor.
     * Uma origem não carrega dados da outra, para o cadastro não ficar com lixo da escolha anterior.
     */
    private void applySource(AuditedSystem system, CreateSystemRequest request) {
        SourceType type = request.sourceType() == null ? SourceType.GIT_URL : request.sourceType();
        boolean sameFolder = system.isLocalSource() && LocalSource.normalizeInput(request.localPath()).equals(system.getLocalPath());
        system.setSourceType(type);
        if (type == SourceType.LOCAL_PATH) {
            if (!sameFolder) {
                // Pasta nova: precisa existir, estar dentro da raiz e ser um repositório Git. Editar outros campos de
                // um sistema que já usa a pasta não depende de ela estar acessível agora.
                try {
                    system.setLocalPath(ingestionService.validateLocalPath(request.localPath(), null));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(e.getMessage(), e);
                }
            }
            system.setLocalSnapshot(request.localSnapshot() == null ? LocalSnapshot.COMMIT : request.localSnapshot());
            system.setRepoUrl(null);
            return;
        }
        system.setRepoUrl(request.repoUrl());
        system.setLocalPath(null);
        system.setLocalSnapshot(null);
    }

    private void applyGitAccess(AuditedSystem system, CreateSystemRequest request) {
        applyGitSettings(system, request);
        applyNotifySettings(system, request);
        applySecrets(system, request);
    }

    private static String blankToNull(String trimmed) {
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void applyGitSettings(AuditedSystem system, CreateSystemRequest request) {
        if (request.gitTokenHosts() != null) {
            system.setGitTokenHosts(blankToNull(request.gitTokenHosts().trim()));
        }
        if (Boolean.TRUE.equals(request.clearGitToken())) {
            system.setGitTokenEnc(null);
        }
        if (request.gitHistoryDepth() != null) {
            system.setGitHistoryDepth(request.gitHistoryDepth());
        }
    }

    private void applyNotifySettings(AuditedSystem system, CreateSystemRequest request) {
        if (request.notifyFormat() != null) {
            system.setNotifyFormat(blankToNull(request.notifyFormat().trim().toLowerCase(Locale.ROOT)));
        }
        if (request.notifyMinSeverity() != null) {
            system.setNotifyMinSeverity(blankToNull(request.notifyMinSeverity().trim().toUpperCase(Locale.ROOT)));
        }
        if (request.notifyMinScoreDrop() != null) {
            system.setNotifyMinScoreDrop(request.notifyMinScoreDrop());
        }
        if (request.notifyPublicUrl() != null) {
            system.setNotifyPublicUrl(blankToNull(request.notifyPublicUrl().trim()));
        }
        if (Boolean.TRUE.equals(request.clearNotifyUrl())) {
            system.setNotifyUrlEnc(null);
        }
        String notifyUrl = request.notifyUrl();
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            try {
                NotificationService.validateTarget(notifyUrl.trim());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage(), e);
            }
            system.setNotifyUrlEnc(encrypted(notifyUrl, "a URL"));
        }
    }

    /** Segredos novos (webhook e token Git): cifrados antes de gravar; "remover" apaga o que já estava salvo. */
    private void applySecrets(AuditedSystem system, CreateSystemRequest request) {
        if (Boolean.TRUE.equals(request.clearWebhookSecret())) {
            system.setWebhookSecretEnc(null);
        }
        String webhook = request.webhookSecret();
        if (webhook != null && !webhook.isBlank()) {
            system.setWebhookSecretEnc(encrypted(webhook, "o segredo"));
        }
        String token = request.gitToken();
        if (token != null && !token.isBlank()) {
            system.setGitTokenEnc(encrypted(token, "o token"));
        }
    }

    private String encrypted(String value, String what) {
        if (!secretBox.available()) {
            throw new BadRequestException("Não foi possível guardar " + what + ": a API não tem chave de cifragem (defina SECRETS_KEY ou deixe-a gravar o arquivo de chave em um volume).");
        }
        return secretBox.encrypt(value.trim());
    }
}
