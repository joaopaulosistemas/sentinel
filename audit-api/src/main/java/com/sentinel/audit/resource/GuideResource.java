package com.sentinel.audit.resource;

import com.sentinel.audit.pack.GuideBook;
import com.sentinel.audit.pack.GuideCatalog;
import com.sentinel.audit.pack.PackRegistry;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/** Guia de código: boas práticas por tecnologia, as regras do Sentinel que valem para cada uma e o CLAUDE.md para a IA. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/guide")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Guide", description = "Guia de código por tecnologia")
public class GuideResource {

    public record RuleView(String id, String pack, String title, String action, String severity, String cwe,
                           String before, String after) {}

    public record RuleRef(String id, boolean own) {}

    public record StackView(String id, String name, String kind, String summary, List<String> tools, List<String> gaps,
                            List<String> commands, boolean commandsVerified, GuideBook.Review review,
                            List<GuideBook.Practice> practices, boolean baseLanguage, int ownRules, int inheritedRules,
                            List<RuleRef> rules) {}

    public record GuideResponse(List<StackView> stacks, List<RuleView> rules, List<GuideBook.Practice> process) {}

    @Inject
    PackRegistry packRegistry;

    @GET
    @Operation(summary = "Guia de código",
            description = "Tecnologias com boas práticas e as regras de cada uma, lidas dos pacotes carregados")
    public GuideResponse guide() {
        List<GuideCatalog.GuideRule> rules = GuideCatalog.of(packRegistry.snapshot().packs());
        List<GuideBook.Stack> stacks = GuideBook.stacks();
        List<StackView> views = stacks.stream().map(s -> view(s, stacks, rules)).toList();
        List<RuleView> ruleViews = rules.stream()
                .map(r -> new RuleView(r.id(), r.pack(), r.title(), r.action(), r.severity(), r.cwe(), r.before(), r.after()))
                .toList();
        return new GuideResponse(views, ruleViews, GuideBook.process());
    }

    @GET
    @Path("/{stack}/claude-md")
    @Produces("text/markdown;charset=UTF-8")
    @Operation(summary = "CLAUDE.md da tecnologia",
            description = "Arquivo de instruções enxuto para a IA que escreve código nessa tecnologia")
    public Response claudeMd(@PathParam("stack") String stackId) {
        List<GuideBook.Stack> stacks = GuideBook.stacks();
        GuideBook.Stack stack = stacks.stream().filter(s -> s.id().equals(stackId)).findFirst().orElse(null);
        if (stack == null) {
            return Response.status(Response.Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "Tecnologia desconhecida: " + stackId)).build();
        }
        List<GuideCatalog.GuideRule> rules = GuideCatalog.of(packRegistry.snapshot().packs());
        return Response.ok(GuideBook.claudeMd(stack, GuideBook.rulesOf(stack, stacks, rules))).build();
    }

    private static StackView view(GuideBook.Stack stack, List<GuideBook.Stack> all, List<GuideCatalog.GuideRule> rules) {
        List<GuideBook.StackRule> mine = GuideBook.rulesOf(stack, all, rules);
        long own = mine.stream().filter(GuideBook.StackRule::own).count();
        return new StackView(stack.id(), stack.name(), stack.kind(), stack.summary(), stack.tools(), stack.gaps(), stack.commands(),
                stack.commandsVerified(), stack.review(), stack.practices(), stack.own().isEmpty(), (int) own, mine.size() - (int) own,
                mine.stream().map(s -> new RuleRef(s.rule().id(), s.own())).toList());
    }
}
