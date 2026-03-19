package fr.flowguard.alert.resource;

import fr.flowguard.alert.entity.AlertEntity;
import fr.flowguard.alert.entity.AlertThresholdEntity;
import fr.flowguard.decision.entity.CashRiskSnapshotEntity;
import fr.flowguard.decision.entity.CashRecommendationEntity;
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
 * Smart alert API.
 *
 * Alerts are enriched with:
 *   - A contextual explanation (WHY is this happening)
 *   - A suggested_action from the recommendation engine (WHAT to do)
 *
 * Example upgraded alert:
 *   title:   "Risque de trésorerie"
 *   message: "Votre solde tombera sous €5,000 dans 6 jours à cause de la TVA (€18,000)."
 *   suggestedAction: "Reportez €7,000 fournisseurs → +€7,000 de trésorerie à court terme."
 *
 * GET  /api/alerts
 * GET  /api/alerts/unread-count
 * PUT  /api/alerts/{id}/read
 * PUT  /api/alerts/read-all
 * GET  /api/alert-thresholds
 * PUT  /api/alert-thresholds
 * POST /api/alerts/generate   – force alert generation from latest snapshot
 */
@Tag(name = "Alerts", description = "Alertes financieres intelligentes")
public class AlertResource {

    @Inject JsonWebToken jwt;

    @GET
    @Path("/api/alerts")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lister les alertes")
    public Response list(@QueryParam("unreadOnly") @DefaultValue("false") boolean unreadOnly) {
        String userId = jwt.getSubject();
        List<AlertEntity> alerts = AlertEntity.findByUser(userId, unreadOnly);
        return Response.ok(alerts.stream().map(this::toDto).toList()).build();
    }

    @GET
    @Path("/api/alerts/unread-count")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Nombre d alertes non lues")
    public Response unreadCount() {
        String userId = jwt.getSubject();
        return Response.ok(Map.of("count", AlertEntity.countUnread(userId))).build();
    }

    @PUT
    @Path("/api/alerts/{id}/read")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Marquer une alerte comme lue")
    public Response markRead(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        AlertEntity a = AlertEntity.find("id = ?1 AND userId = ?2", id, userId).firstResult();
        if (a == null) return Response.status(404).build();
        a.isRead = true;
        return Response.ok(Map.of("id", id, "isRead", true)).build();
    }

    @PUT
    @Path("/api/alerts/read-all")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Marquer toutes les alertes comme lues")
    public Response markAllRead() {
        String userId = jwt.getSubject();
        AlertEntity.update("isRead = true WHERE userId = ?1 AND isRead = false", userId);
        return Response.ok(Map.of("marked", true)).build();
    }

    @GET
    @Path("/api/alert-thresholds")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Seuils d alertes configures")
    public Response getThresholds() {
        String userId = jwt.getSubject();
        List<AlertThresholdEntity> thresholds = AlertThresholdEntity.findByUser(userId);
        return Response.ok(thresholds.stream().map(t -> Map.of(
                "id", t.id, "alertType", t.alertType,
                "minAmount", t.minAmount != null ? t.minAmount : 0,
                "enabled", t.enabled, "minSeverity", t.minSeverity
        )).toList()).build();
    }

    @PUT
    @Path("/api/alert-thresholds")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Creer ou mettre a jour un seuil")
    public Response upsertThreshold(ThresholdRequest req) {
        String userId = jwt.getSubject();
        AlertThresholdEntity t = AlertThresholdEntity.findByUserAndType(userId, req.alertType())
                .orElseGet(AlertThresholdEntity::new);
        boolean isNew = t.id == null;
        t.userId = userId;
        t.alertType = req.alertType();
        t.minAmount = req.minAmount();
        t.enabled = req.enabled();
        t.minSeverity = req.minSeverity() != null ? req.minSeverity() : "LOW";
        if (isNew) t.persist();
        return Response.ok(Map.of(
                "id", t.id, "alertType", t.alertType,
                "minAmount", t.minAmount != null ? t.minAmount : 0,
                "enabled", t.enabled, "minSeverity", t.minSeverity
        )).build();
    }

    /**
     * Force alert generation from the latest risk snapshot.
     * Creates smart contextual alerts enriched with suggested actions.
     */
    @POST
    @Path("/api/alerts/generate")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Generer les alertes depuis le dernier snapshot")
    public Response generateAlerts() {
        String userId = jwt.getSubject();
        var snapOpt = CashRiskSnapshotEntity.findLatestByUser(userId);
        if (snapOpt.isEmpty()) {
            return Response.status(404).entity(Map.of("error", "No snapshot — run /decision-engine/refresh first")).build();
        }
        var snap = snapOpt.get();
        List<CashRecommendationEntity> recos = CashRecommendationEntity.findBySnapshot(snap.id);
        String topActionDesc = recos.isEmpty() ? null : recos.get(0).description;

        int created = 0;

        // Alert 1: overall risk level (always)
        if (!"LOW".equals(snap.riskLevel)) {
            AlertEntity a = new AlertEntity();
            a.userId = userId;
            a.snapshotId = snap.id;
            a.type = "CASH_SHORTAGE";
            a.severity = snap.riskLevel.equals("CRITICAL") ? "CRITICAL"
                    : snap.riskLevel.equals("HIGH") ? "HIGH" : "MEDIUM";
            a.title = buildRiskTitle(snap.riskLevel);
            a.message = buildRiskMessage(snap);
            a.suggestedAction = topActionDesc;
            a.predictedDeficitAmount = snap.minBalance != null && snap.minBalance.compareTo(BigDecimal.ZERO) < 0
                    ? snap.minBalance.abs() : null;
            a.predictedDeficitDate = snap.minBalanceDate;
            a.persist();
            created++;
        }

        return Response.ok(Map.of("created", created, "snapshotId", snap.id)).build();
    }

    private String buildRiskTitle(String level) {
        return switch (level) {
            case "CRITICAL" -> "⚠ Risque critique de trésorerie";
            case "HIGH"     -> "Risque élevé de trésorerie";
            default         -> "Trésorerie sous surveillance";
        };
    }

    private String buildRiskMessage(CashRiskSnapshotEntity snap) {
        StringBuilder sb = new StringBuilder();
        if (snap.runwayDays != null && snap.runwayDays < 180) {
            sb.append("Votre trésorerie couvre environ ").append(snap.runwayDays).append(" jours de charges.");
        }
        if (snap.deficitPredicted) {
            sb.append(" Un déficit est prévu");
            if (snap.minBalanceDate != null) sb.append(" le ").append(snap.minBalanceDate);
            sb.append(".");
        }
        if (snap.minBalance != null && snap.minBalance.compareTo(BigDecimal.ZERO) < 0) {
            sb.append(String.format(" Solde minimum projeté : %,.0f€.", snap.minBalance.doubleValue()));
        }
        return sb.toString().isBlank() ? "Surveillance de trésorerie activée." : sb.toString().trim();
    }

    private Map<String, Object> toDto(AlertEntity a) {
        return Map.of(
                "id", a.id,
                "type", a.type,
                "severity", a.severity,
                "title", a.title,
                "message", a.message,
                "suggestedAction", a.suggestedAction != null ? a.suggestedAction : "",
                "projectedDeficit", a.predictedDeficitAmount != null ? a.predictedDeficitAmount : 0,
                "triggerDate", a.predictedDeficitDate != null ? a.predictedDeficitDate.toString() : "",
                "isRead", a.isRead,
                "createdAt", a.createdAt.toString()
        );
    }

    public record ThresholdRequest(
        String alertType, BigDecimal minAmount, boolean enabled, String minSeverity
    ) {}
}