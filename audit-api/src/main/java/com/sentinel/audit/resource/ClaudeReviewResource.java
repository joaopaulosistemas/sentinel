package com.sentinel.audit.resource;

import com.sentinel.audit.review.RuleProposal;
import com.sentinel.audit.review.ReviewService;
import com.sentinel.audit.review.SuggestionStatus;
import com.sentinel.audit.review.ReviewSuggestion;
import com.sentinel.audit.review.ReviewMetrics;
import jakarta.annotation.security.PermitAll;
import com.sentinel.audit.service.SystemCatalog;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * Revisão do Claude: sugestões e regras propostas de um sistema e as decisões das pessoas sobre elas.
 * Fica sob a API key global. Nada aqui altera achado ou nota.
 */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/claude-review/{id}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Revisão do Claude", description = "Sugestões do Claude e funil de promoção de regras")
public class ClaudeReviewResource {

    public record SuggestionDto(@Schema(description = "Identificador da sugestão.") Long id, @Schema(description = "Título da sugestão.") String title, @Schema(description = "Detalhe da sugestão.") String detail, @Schema(description = "Regra a que a sugestão se refere.") String ruleRef, @Schema(description = "Arquivo citado.") String file, @Schema(description = "Linha citada.") Integer line,
                                @Schema(description = "Situação: aberta, tratada ou dispensada.") String status, @Schema(description = "Quando foi feita.") String createdAt) {}

    public record EvidenceDto(@Schema(description = "Achados que a regra deu durante a observação.") int hits, @Schema(description = "Quantos deles foram marcados como falso positivo.") int falsePositives, @Schema(description = "Auditorias desde que a observação começou.") int auditsSinceStart, @Schema(description = "Proporção de acertos (0 a 1).") double precision) {}

    public record ProposalDto(@Schema(description = "Identificador da regra proposta.") Long id, @Schema(description = "Nome curto da regra.") String slug, @Schema(description = "Título.") String title, @Schema(description = "Por que o Claude propôs a regra.") String rationale, @Schema(description = "A regra, em YAML do Semgrep.") String semgrepYaml, @Schema(description = "Estágio da regra.") String stage,
                              @Schema(description = "Estágio, em texto para exibição.") String stageLabel, @Schema(description = "Regra de origem, quando foi copiada de outro sistema.") Long copiedFromId, @Schema(description = "Observação do teste de amostra.") String sampleNote, @Schema(description = "Quando a observação começou.") String observationStartedAt,
                              @Schema(description = "Observação de quem decidiu.") String decisionNote, @Schema(description = "Resultado medido durante a observação.") EvidenceDto evidence, @Schema(description = "Precisão mínima configurada para a regra poder ser promovida.") double minPrecision, @Schema(description = "Mínimo de achados configurado para a regra poder ser promovida.") int minHits,
                              @Schema(description = "Verdadeiro quando a regra pode ser copiada para outros sistemas.") boolean sharedEligible, @Schema(description = "Verdadeiro quando a regra já roda nas auditorias.") boolean runsInAudits) {}

    public record ReviewResponse(@Schema(description = "Quantas sugestões estão abertas.") int openSuggestions, @Schema(description = "Sugestões do Claude para o sistema.") List<SuggestionDto> suggestions, @Schema(description = "Regras propostas pelo Claude.") List<ProposalDto> proposals) {}

    public record StatusRequest(@Schema(description = "Novo estado da sugestão: tratada, dispensada ou aberta.") String status) {}

    public record SampleRequest(@Schema(description = "Se o teste de amostra, feito fora do Sentinel, passou.") Boolean passed, @Schema(description = "Observação sobre o teste.") String note) {}

    public record NoteRequest(@Schema(description = "Observação de quem decidiu.") String note) {}

    public record CopyRequest(@Schema(description = "Sistema que recebe a cópia da regra.") Long targetSystemId) {}

    @Inject
    SystemCatalog systemCatalog;
    @Inject
    ReviewService service;

    @GET
    @Operation(summary = "Sugestões e regras propostas do sistema",
            description = "Lista as sugestões feitas pelo Claude para o sistema e as regras que ele propôs, com o estado de cada uma.")
    public ReviewResponse get(@PathParam("id") Long id) {
        requireSystem(id);
        return response(id);
    }

    @GET
    @Path("/metrics")
    @Operation(summary = "Quanto o Claude acerta: sugestões tratadas e regras promovidas, segundo as pessoas",
            description = "Devolve as contagens de sugestões tratadas e de regras promovidas, conforme a decisão registrada pelas pessoas.")
    public ReviewMetrics.Result metrics(@PathParam("id") Long id) {
        requireSystem(id);
        return ReviewMetrics.compute(
                service.suggestions(id).stream().map(ReviewSuggestion::getStatus).toList(),
                service.proposals(id).stream().map(v -> v.proposal().getStage()).toList());
    }

    @POST
    @Path("/suggestions/{sid}/status")
    @Operation(summary = "Marcar uma sugestão como tratada, dispensada ou aberta",
            description = "Muda o estado de uma sugestão do Claude para o sistema (tratada, dispensada ou aberta).")
    public ReviewResponse suggestionStatus(@PathParam("id") Long id, @PathParam("sid") Long sid, StatusRequest body) {
        requireSystem(id);
        SuggestionStatus status = parseStatus(body);
        return act(id, () -> service.setSuggestionStatus(id, sid, status));
    }

    @POST
    @Path("/rules/{rid}/review")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Uma pessoa revisou a regra proposta",
            description = "Registra que uma pessoa revisou a regra proposta pelo Claude.")
    public ReviewResponse review(@PathParam("id") Long id, @PathParam("rid") Long rid) {
        requireSystem(id);
        return act(id, () -> service.review(id, rid));
    }

    @POST
    @Path("/rules/{rid}/sample")
    @Operation(summary = "Registrar o resultado do teste de amostra feito fora do Sentinel",
            description = "Guarda o resultado do teste de amostra da regra proposta, executado fora do Sentinel.")
    public ReviewResponse sample(@PathParam("id") Long id, @PathParam("rid") Long rid, SampleRequest body) {
        requireSystem(id);
        if (body == null || body.passed() == null) {
            throw new BadRequestException("Informe se passou.");
        }
        return act(id, () -> service.sampleTest(id, rid, body.passed(), body.note()));
    }

    @POST
    @Path("/rules/{rid}/observe")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Começar a observação: a regra passa a rodar fora da nota",
            description = "Coloca a regra proposta em observação: ela passa a rodar sem pesar na nota do sistema.")
    public ReviewResponse observe(@PathParam("id") Long id, @PathParam("rid") Long rid) {
        requireSystem(id);
        return act(id, () -> service.startObservation(id, rid));
    }

    @POST
    @Path("/rules/{rid}/promote")
    @Operation(summary = "Promover a regra: passa a pesar na nota",
            description = "Promove a regra proposta: a partir daí ela pesa na nota do sistema.")
    public ReviewResponse promote(@PathParam("id") Long id, @PathParam("rid") Long rid, NoteRequest body) {
        requireSystem(id);
        return act(id, () -> service.promote(id, rid, body == null ? null : body.note()));
    }

    @POST
    @Path("/rules/{rid}/discard")
    @Operation(summary = "Descartar a regra",
            description = "Descarta a regra proposta pelo Claude.")
    public ReviewResponse discard(@PathParam("id") Long id, @PathParam("rid") Long rid, NoteRequest body) {
        requireSystem(id);
        return act(id, () -> service.discard(id, rid, body == null ? null : body.note()));
    }

    @POST
    @Path("/rules/{rid}/copy")
    @Operation(summary = "Copiar uma regra promovida para outro sistema, em observação",
            description = "Copia uma regra já promovida para outro sistema, onde ela começa em observação.")
    public ReviewResponse copy(@PathParam("id") Long id, @PathParam("rid") Long rid, CopyRequest body) {
        requireSystem(id);
        return act(id, () -> service.copyTo(id, rid, body == null ? null : body.targetSystemId()));
    }

    private static SuggestionStatus parseStatus(StatusRequest body) {
        try {
            return SuggestionStatus.valueOf(body == null || body.status() == null ? "" : body.status().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Situação inválida.", e);
        }
    }

    private ReviewResponse act(Long id, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
        return response(id);
    }

    private ReviewResponse response(Long id) {
        List<SuggestionDto> suggestions = service.suggestions(id).stream()
                .map(s -> new SuggestionDto(s.getId(), s.getTitle(), s.getDetail(), s.getRuleRef(), s.getFilePath(),
                        s.getLineNumber(), s.getStatus().name(), s.getCreatedAt().toString()))
                .toList();
        List<ProposalDto> proposals = service.proposals(id).stream().map(v -> {
            RuleProposal p = v.proposal();
            return new ProposalDto(p.getId(), p.getSlug(), p.getTitle(), p.getRationale(), p.getSemgrepYaml(),
                    p.getStage().name(), p.getStage().label(), p.getCopiedFromId(), p.getSampleNote(),
                    p.getObservationStartedAt() == null ? null : p.getObservationStartedAt().toString(),
                    p.getDecisionNote(),
                    new EvidenceDto(v.evidence().hits(), v.evidence().falsePositives(), v.evidence().auditsSinceStart(),
                            v.evidence().precision()),
                    v.minPrecision(), v.minHits(), v.sharedEligible(), p.getStage().runs());
        }).toList();
        long open = suggestions.stream().filter(s -> "OPEN".equals(s.status())).count();
        return new ReviewResponse((int) open, suggestions, proposals);
    }

    private void requireSystem(Long id) {
        systemCatalog.requireExists(id);
    }
}
