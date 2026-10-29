package com.sentinel.audit.resource;

import com.sentinel.audit.model.AuditRun;
import com.sentinel.audit.service.RunCatalog;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Exportações de uma auditoria: relatórios HTML e PDF, achados em CSV e SBOM. */
// Acesso: ApiKeyFilter (API_KEY) ou http.auth.permission do perfil oidc protegem /api/*; sem papéis adicionais por endpoint.
@PermitAll
@Path("/api/v1/audits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Audits", description = "Relatórios e exportações de auditorias")
public class AuditExportResource {

    @Inject
    AuditReports reports;

    @Inject
    RunCatalog runCatalog;


    /**
     * Gera e retorna um relatório HTML da auditoria. O tipo escolhe o conteúdo: técnico (padrão, com todos os
     * achados), executivo (uma página para quem decide) ou comparativo (evolução entre auditorias do sistema).
     */
    @GET
    @Path("/{id}/report")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Relatório HTML", description = "Relatório da auditoria: type=tecnico (padrão), executivo ou comparativo; days limita o período do comparativo")
    public Response getReport(@PathParam("id") Long id,
                              @QueryParam("type") String type,
                              @QueryParam("days") Integer days) {
        return Response.ok(reports.html(id, type, days), MediaType.TEXT_HTML).build();
    }

    /** Mesmos relatórios em PDF, gerado no servidor. */
    @GET
    @Path("/{id}/report.pdf")
    @Produces("application/pdf")
    @Operation(summary = "Relatório PDF", description = "Relatório da auditoria em PDF: type=tecnico (padrão), executivo ou comparativo; days limita o período do comparativo")
    public Response getReportPdf(@PathParam("id") Long id,
                                 @QueryParam("type") String type,
                                 @QueryParam("days") Integer days) {
        AuditReports.PdfFile pdf = reports.pdf(id, type, days);
        return Response.ok(pdf.bytes(), "application/pdf")
                .header("Content-Disposition", "inline; filename=\"" + pdf.filename() + "\"")
                .build();
    }

    /**
     * Achados ativos da auditoria em CSV, para abrir em planilha.
     */
    @GET
    @Path("/{id}/findings.csv")
    @Produces("text/csv")
    @Operation(summary = "Achados em CSV", description = "Achados ativos da auditoria, prontos para planilha")
    public Response getFindingsCsv(@PathParam("id") Long id) {
        String csv = reports.csv(id);
        return Response.ok("\uFEFF" + csv, "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"achados-auditoria-" + id + ".csv\"")
                .build();
    }

    /**
     * SBOM (CycloneDX 1.5, JSON) das dependências encontradas na auditoria.
     */
    @GET
    @Path("/{id}/sbom")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "SBOM CycloneDX", description = "Lista de materiais de software da auditoria (CycloneDX 1.5)")
    public Response getSbom(@PathParam("id") Long id) {
        AuditRun run = runCatalog.require(id);
        if (run.getSbomJson() == null) {
            throw new NotFoundException("Esta auditoria não tem SBOM (auditorias antigas ou falha na geração)");
        }
        return Response.ok(run.getSbomJson(), MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=\"sbom-auditoria-" + id + ".cdx.json\"")
                .build();
    }
}
