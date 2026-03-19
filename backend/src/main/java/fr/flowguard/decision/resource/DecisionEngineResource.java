package fr.flowguard.decision.resource;

import fr.flowguard.decision.entity.*;
import fr.flowguard.decision.service.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Financial Decision Engine API
 *
 * GET  /decision-engine/summary        – full snapshot + drivers + actions (cached)
 * GET  /decision-engine/drivers        – top cash flow drivers only
 * GET  /decision-engine/actions        – pending recommendations only
 * POST /decision-engine/simulate       – what-if scenario simulation
 * POST /decision-engine/refresh        – force recompute, invalidates cache
 * POST /decision-engine/actions/{id}/apply    – mark recommendation as applied
 * POST /decision-engine/actions/{id}/dismiss  – dismiss recommendation
 * GET  /decision-engine/brief          – latest weekly brief
 * POST /decision-engine/brief/generate – on-demand brief generation
 * GET  /decision-engine/audit          – audit log (paginated)
 */
@Path("/decision-engine")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "DecisionEngine", description = "Moteur de decision financiere")
public class DecisionEngineResource {

    @Inject JsonWebToken jwt;
    @Inject DecisionEngineService engine;
    @Inject WeeklyBriefService briefService;

    @GET
    @Path("/summary")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Synthese complete : risque, drivers, actions (cache 10min)")
    public Response summary() {
        String userId = jwt.getSubject();
        Map<String, Object> result = engine.getSummary(userId);
        return Response.ok(result).build();
    }

    @GET
    @Path("/drivers")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Facteurs explicatifs du cash flow")
    public Response drivers() {
        String userId = jwt.getSubject();
        CashRiskSnapshotEntity.findLatestByUser(userId).ifPresent(snap -> {});
        var latest = CashRiskSnapshotEntity.findLatestByUser(userId);
        if (latest.isEmpty()) {
            return Response.ok(List.of()).build();
        }
        List<CashDriverEntity> drivers = CashDriverEntity.findBySnapshot(latest.get().id);
        return Response.ok(drivers.stream().map(d -> Map.of(
                "id", d.id, "type", d.driverType, "label", d.label,
                "amount", d.amount != null ? d.amount : 0,
                "impactDays", d.impactDays != null ? d.impactDays : 0,
                "dueDate", d.dueDate != null ? d.dueDate.toString() : "",
                "rank", d.rank
        )).toList()).build();
    }

    @GET
    @Path("/actions")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Actions recommandees en attente")
    public Response actions() {
        String userId = jwt.getSubject();
        List<CashRecommendationEntity> pending = CashRecommendationEntity.findPendingByUser(userId);
        return Response.ok(pending.stream().map(r -> Map.of(
                "id", r.id, "actionType", r.actionType,
                "description", r.description,
                "estimatedImpact", r.estimatedImpact != null ? r.estimatedImpact : 0,
                "horizonDays", r.horizonDays != null ? r.horizonDays : 0,
                "confidence", r.confidence != null ? r.confidence : 0,
                "status", r.status
        )).toList()).build();
    }

    @POST
    @Path("/simulate")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Simuler un scenario what-if sur la tresorerie")
    public Response simulate(SimulateRequest req) {
        String userId = jwt.getSubject();
        // Get current snapshot base
        var snap = CashRiskSnapshotEntity.findLatestByUser(userId);
        BigDecimal currentBalance = snap.map(s -> s.currentBalance).orElse(BigDecimal.ZERO);
        Integer baseRunway = snap.map(s -> s.runwayDays).orElse(0);

        // Apply scenario adjustments
        BigDecimal balanceDelta = BigDecimal.ZERO;
        int runwayDelta = 0;
        String explanation;

        switch (req.scenarioType()) {
            case "HIRE_EMPLOYEE" -> {
                BigDecimal monthlyCost = req.amount() != null ? req.amount() : new BigDecimal("3500");
                balanceDelta = monthlyCost.negate().multiply(new BigDecimal("12"));
                runwayDelta = -(monthlyCost.multiply(new BigDecimal("365")).divide(monthlyCost.add(new BigDecimal("1")), 0, java.math.RoundingMode.FLOOR).intValue());
                explanation = String.format("Embaucher un employé à %,.0f€/mois réduit la trésorerie de %,.0f€ sur 12 mois.",
                        monthlyCost.doubleValue(), balanceDelta.abs().doubleValue());
            }
            case "REVENUE_DROP" -> {
                double pct = req.percentage() != null ? req.percentage() / 100.0 : 0.20;
                BigDecimal impact = currentBalance.multiply(BigDecimal.valueOf(pct));
                balanceDelta = impact.negate();
                runwayDelta = -(int) (baseRunway * pct);
                explanation = String.format("Une baisse de %.0f%% du CA réduirait la trésorerie de %,.0f€.",
                        pct * 100, impact.doubleValue());
            }
            case "PAYMENT_DELAY" -> {
                BigDecimal amount = req.amount() != null ? req.amount() : BigDecimal.ZERO;
                int days = req.daysDelay() != null ? req.daysDelay() : 30;
                balanceDelta = amount; // positive impact on short-term
                runwayDelta = days;
                explanation = String.format("Reporter %,.0f€ de paiements de %d jours améliore la trésorerie à court terme.",
                        amount.doubleValue(), days);
            }
            default -> {
                explanation = "Scénario inconnu.";
            }
        }

        BigDecimal projectedBalance = currentBalance.add(balanceDelta).setScale(2, java.math.RoundingMode.HALF_UP);
        int projectedRunway = Math.max(0, baseRunway + runwayDelta);

        return Response.ok(Map.of(
                "scenarioType", req.scenarioType(),
                "baseBalance", currentBalance,
                "projectedBalance", projectedBalance,
                "balanceDelta", balanceDelta,
                "baseRunwayDays", baseRunway,
                "projectedRunwayDays", projectedRunway,
                "explanation", explanation
        )).build();
    }

    @POST
    @Path("/refresh")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Forcer un recalcul (invalide le cache)")
    public Response refresh() {
        String userId = jwt.getSubject();
        engine.invalidate(userId);
        Map<String, Object> fresh = engine.compute(userId);
        return Response.ok(fresh).build();
    }

    @POST
    @Path("/actions/{id}/apply")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Marquer une action comme appliquee")
    public Response applyAction(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        return CashRecommendationEntity.findByIdAndUser(id, userId)
                .map(r -> {
                    r.status = "APPLIED";
                    r.appliedAt = java.time.Instant.now();
                    DecisionAuditLogEntity.of(userId, "RECOMMENDATION_APPLIED", id, "RECOMMENDATION",
                            "{\"actionType\":\"" + r.actionType + "\"}").persist();
                    engine.invalidate(userId);
                    return Response.ok(Map.of("id", id, "status", "APPLIED")).build();
                })
                .orElseGet(() -> Response.status(404).entity(Map.of("error", "Not found")).build());
    }

    @POST
    @Path("/actions/{id}/dismiss")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Ignorer une recommandation")
    public Response dismissAction(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        return CashRecommendationEntity.findByIdAndUser(id, userId)
                .map(r -> {
                    r.status = "DISMISSED";
                    DecisionAuditLogEntity.of(userId, "RECOMMENDATION_DISMISSED", id, "RECOMMENDATION",
                            "{\"actionType\":\"" + r.actionType + "\"}").persist();
                    return Response.ok(Map.of("id", id, "status", "DISMISSED")).build();
                })
                .orElseGet(() -> Response.status(404).entity(Map.of("error", "Not found")).build());
    }

    @GET
    @Path("/brief")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Dernier bulletin financier hebdomadaire")
    public Response latestBrief() {
        String userId = jwt.getSubject();
        return WeeklyBriefEntity.findLatestByUser(userId)
                .map(b -> Response.ok(Map.of(
                        "id", b.id,
                        "briefText", b.briefText,
                        "riskLevel", b.riskLevel,
                        "runwayDays", b.runwayDays != null ? b.runwayDays : 0,
                        "generatedAt", b.generatedAt.toString(),
                        "generationMode", b.generationMode
                )).build())
                .orElseGet(() -> Response.status(404).entity(Map.of("error", "No brief yet")).build());
    }

    @POST
    @Path("/brief/generate")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Generer un bulletin a la demande")
    public Response generateBrief() {
        String userId = jwt.getSubject();
        WeeklyBriefEntity brief = briefService.generate(userId, "ON_DEMAND");
        return Response.ok(Map.of(
                "id", brief.id,
                "briefText", brief.briefText,
                "riskLevel", brief.riskLevel,
                "runwayDays", brief.runwayDays != null ? brief.runwayDays : 0,
                "generatedAt", brief.generatedAt.toString()
        )).build();
    }

    @GET
    @Path("/audit")
    @RolesAllowed({"ROLE_ADMIN"})
    @Operation(summary = "Journal d audit (admin uniquement)")
    public Response auditLog(@QueryParam("page") @DefaultValue("0") int page,
                              @QueryParam("size") @DefaultValue("20") int size) {
        String userId = jwt.getSubject();
        List<DecisionAuditLogEntity> logs = DecisionAuditLogEntity
                .find("userId = ?1 ORDER BY createdAt DESC", userId)
                .page(page, size).list();
        return Response.ok(logs.stream().map(l -> Map.of(
                "id", l.id, "eventType", l.eventType,
                "entityId", l.entityId != null ? l.entityId : "",
                "entityType", l.entityType != null ? l.entityType : "",
                "payload", l.payload != null ? l.payload : "{}",
                "createdAt", l.createdAt.toString()
        )).toList()).build();
    }

    // ---- DTOs ----

    public record SimulateRequest(
        String scenarioType,   // HIRE_EMPLOYEE | REVENUE_DROP | PAYMENT_DELAY
        BigDecimal amount,
        Double percentage,
        Integer daysDelay
    ) {}
}