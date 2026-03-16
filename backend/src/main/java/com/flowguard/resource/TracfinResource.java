package com.flowguard.resource;

import com.flowguard.domain.TracfinReport;
import com.flowguard.domain.TracfinReport.SuspicionType;
import com.flowguard.security.Roles;
import com.flowguard.service.TracfinService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for TRACFIN compliance workflow — admin/compliance only.
 * These endpoints are intentionally NOT documented in public API spec.
 *
 * WARNING: Art. L574-1 CMF (tipping-off) — responses from these endpoints must
 * NEVER be surfaced to the end user whose account is under investigation.
 */
@Path("/admin/tracfin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.ADMIN, Roles.SUPER_ADMIN})
public class TracfinResource {

    @Inject
    TracfinService tracfinService;

    @Inject
    JsonWebToken jwt;

    // ── Query ──────────────────────────────────────────────────────────────────

    /** GET /api/admin/tracfin/pending — list reports awaiting compliance review */
    @GET
    @Path("/pending")
    @RunOnVirtualThread
    public Response getPendingReports() {
        List<TracfinReport> reports = tracfinService.getPendingReports();
        return Response.ok(reports).build();
    }

    /** GET /api/admin/tracfin — list all reports */
    @GET
    @RunOnVirtualThread
    public Response getAllReports() {
        List<TracfinReport> reports = tracfinService.getAllReports();
        return Response.ok(reports).build();
    }

    // ── Flag ──────────────────────────────────────────────────────────────────

    /**
     * POST /api/admin/tracfin/flag — manually flag suspicious activity.
     * Used when an analyst manually identifies suspicious behaviour.
     */
    @POST
    @Path("/flag")
    @RunOnVirtualThread
    public Response flagSuspiciousActivity(FlagRequest request) {
        UUID reportId = tracfinService.flagSuspiciousActivity(
                request.userId(), request.suspicionType(), request.narrative(), request.triggerAmount());
        if (reportId == null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("{\"message\":\"An open report already exists for this user and suspicion type\"}")
                    .build();
        }
        return Response.status(Response.Status.CREATED)
                .entity("{\"reportId\":\"" + reportId + "\"}")
                .build();
    }

    // ── Review ────────────────────────────────────────────────────────────────

    /**
     * POST /api/admin/tracfin/{id}/review — record compliance officer decision.
     * approved=true → CONFIRMED_SUSPICION, approved=false → FALSE_POSITIVE
     */
    @POST
    @Path("/{id}/review")
    @RunOnVirtualThread
    public Response reviewReport(
            @PathParam("id") UUID reportId,
            ReviewRequest request
    ) {
        UUID reviewerUserId = UUID.fromString(jwt.getSubject());
        TracfinReport report = tracfinService.reviewReport(
                reportId, request.approved(), reviewerUserId, request.notes());
        return Response.ok(report).build();
    }

    // ── Submit to TRACFIN ─────────────────────────────────────────────────────

    /**
     * POST /api/admin/tracfin/{id}/submit — mark report as submitted to TRACFIN ERMES.
     * Call this AFTER manually submitting via https://ermes.tracfin.mineco.gouv.fr
     * and recording the ERMES reference number.
     */
    @POST
    @Path("/{id}/submit")
    @RunOnVirtualThread
    public Response submitToTracfin(
            @PathParam("id") UUID reportId,
            SubmitRequest request
    ) {
        TracfinReport report = tracfinService.submitToTracfin(reportId, request.ermesDeclRef());
        return Response.ok(report).build();
    }

    // ── Account freeze ────────────────────────────────────────────────────────

    /**
     * POST /api/admin/tracfin/{id}/freeze — freeze the user's account.
     * Art. L561-24 CMF — may oppose transactions for max 3 business days.
     */
    @POST
    @Path("/{id}/freeze")
    @RunOnVirtualThread
    public Response freezeAccount(
            @PathParam("id") UUID reportId,
            FreezeRequest request
    ) {
        tracfinService.freezeUserAccount(request.userId(), reportId, request.reason());
        return Response.ok("{\"status\":\"frozen\"}").build();
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record FlagRequest(
            @NotNull UUID userId,
            @NotNull SuspicionType suspicionType,
            @NotBlank String narrative,
            BigDecimal triggerAmount
    ) {}

    public record ReviewRequest(
            boolean approved,
            @NotBlank String notes
    ) {}

    public record SubmitRequest(
            @NotBlank String ermesDeclRef
    ) {}

    public record FreezeRequest(
            @NotNull UUID userId,
            @NotBlank String reason
    ) {}
}
