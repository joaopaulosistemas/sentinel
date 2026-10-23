package com.sentinel.audit.review;

import com.sentinel.audit.repository.AuditedSystemRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * Revisão do Claude: sugestões e regras propostas de um sistema e as decisões das pessoas sobre elas.
 * Fica sob a API key global. Nada aqui altera achado ou nota.
 */
@Path("/api/v1/claude-review/{id}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Revisão do Claude", description = "Sugestões do Claude e funil de promoção de regras")
public class ClaudeReviewResource {

    public record SuggestionDto(Long id, String title, String detail, String ruleRef, String file, Integer line,
                                String status, String createdAt) {}

    public record EvidenceDto(int hits, int falsePositives, int auditsSinceStart, double precision) {}

    public record ProposalDto(Long id, String slug, String title, String rationale, String semgrepYaml, String stage,
                              String stageLabel, Long copiedFromId, String sampleNote, String observationStartedAt,
                              String decisionNote, EvidenceDto evidence, double minPrecision, int minHits,
                              boolean sharedEligible, boolean runsInAudits) {}

    public record ReviewResponse(int openSuggestions, List<SuggestionDto> suggestions, List<ProposalDto> proposals) {}

    public record StatusRequest(String status) {}

    public record SampleRequest(Boolean passed, String note) {}

    public record NoteRequest(String note) {}

    public record CopyRequest(Long targetSystemId) {}

    @Inject
    AuditedSystemRepository systemRepository;
    @Inject
    ReviewService service;

    @GET
    @Operation(summary = "Sugestões e regras propostas do sistema")
    public ReviewResponse get(@PathParam("id") Long id) {
        requireSystem(id);
        return response(id);
    }

    @POST
    @Path("/suggestions/{sid}/status")
    @Operation(summary = "Marcar uma sugestão como tratada, dispensada ou aberta")
    public ReviewResponse suggestionStatus(@PathParam("id") Long id, @PathParam("sid") Long sid, StatusRequest body) {
        requireSystem(id);
        SuggestionStatus status = parseStatus(body);
        return act(id, () -> service.setSuggestionStatus(id, sid, status));
    }

    @POST
    @Path("/rules/{rid}/review")
    @Operation(summary = "Uma pessoa revisou a regra proposta")
    public ReviewResponse review(@PathParam("id") Long id, @PathParam("rid") Long rid) {
        requireSystem(id);
        return act(id, () -> service.review(id, rid));
    }

    @POST
    @Path("/rules/{rid}/sample")
    @Operation(summary = "Registrar o resultado do teste de amostra feito fora do Sentinel")
    public ReviewResponse sample(@PathParam("id") Long id, @PathParam("rid") Long rid, SampleRequest body) {
        requireSystem(id);
        if (body == null || body.passed() == null) {
            throw new BadRequestException("Informe se passou.");
        }
        return act(id, () -> service.sampleTest(id, rid, body.passed(), body.note()));
    }

    @POST
    @Path("/rules/{rid}/observe")
    @Operation(summary = "Começar a observação: a regra passa a rodar fora da nota")
    public ReviewResponse observe(@PathParam("id") Long id, @PathParam("rid") Long rid) {
        requireSystem(id);
        return act(id, () -> service.startObservation(id, rid));
    }

    @POST
    @Path("/rules/{rid}/promote")
    @Operation(summary = "Promover a regra: passa a pesar na nota")
    public ReviewResponse promote(@PathParam("id") Long id, @PathParam("rid") Long rid, NoteRequest body) {
        requireSystem(id);
        return act(id, () -> service.promote(id, rid, body == null ? null : body.note()));
    }

    @POST
    @Path("/rules/{rid}/discard")
    @Operation(summary = "Descartar a regra")
    public ReviewResponse discard(@PathParam("id") Long id, @PathParam("rid") Long rid, NoteRequest body) {
        requireSystem(id);
        return act(id, () -> service.discard(id, rid, body == null ? null : body.note()));
    }

    @POST
    @Path("/rules/{rid}/copy")
    @Operation(summary = "Copiar uma regra promovida para outro sistema, em observação")
    public ReviewResponse copy(@PathParam("id") Long id, @PathParam("rid") Long rid, CopyRequest body) {
        requireSystem(id);
        return act(id, () -> service.copyTo(id, rid, body == null ? null : body.targetSystemId()));
    }

    private static SuggestionStatus parseStatus(StatusRequest body) {
        try {
            return SuggestionStatus.valueOf(body == null || body.status() == null ? "" : body.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Situação inválida.");
        }
    }

    private ReviewResponse act(Long id, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
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
        if (systemRepository.findByIdOptional(id).isEmpty()) {
            throw new NotFoundException("Sistema não encontrado: " + id);
        }
    }
}
