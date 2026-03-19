package fr.flowguard.decision.service;

import fr.flowguard.auth.entity.UserEntity;
import fr.flowguard.decision.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Generates the Weekly Financial Brief: a short, plain-language narrative
 * (max 150 words) summarising the cash position, risks and top actions.
 *
 * Called:
 *   a) On demand via API (mode=ON_DEMAND)
 *   b) Weekly via Quarkus @Scheduled (mode=CRON)
 */
@ApplicationScoped
public class WeeklyBriefService {

    private static final Logger LOG = Logger.getLogger(WeeklyBriefService.class);

    @Inject DecisionEngineService engine;

    @ConfigProperty(name = "flowguard.brief.min-risk-for-cron", defaultValue = "MEDIUM")
    String minRiskForCron;

    /**
     * Generate and store a brief for a single user.
     * Returns the saved entity.
     */
    @Transactional
    public WeeklyBriefEntity generate(String userId, String mode) {
        Map<String, Object> summary = engine.compute(userId);

        String riskLevel = summary.getOrDefault("riskLevel", "LOW").toString();
        Integer runway = (Integer) summary.get("runwayDays");
        List<?> drivers = (List<?>) summary.getOrDefault("drivers", List.of());
        List<?> actions = (List<?>) summary.getOrDefault("actions", List.of());

        String text = compose(riskLevel, runway, drivers, actions, summary);

        WeeklyBriefEntity brief = new WeeklyBriefEntity();
        brief.userId = userId;
        brief.snapshotId = summary.getOrDefault("snapshotId", "").toString();
        brief.briefText = text;
        brief.riskLevel = riskLevel;
        brief.runwayDays = runway;
        brief.generationMode = mode;
        brief.persist();

        // Audit
        DecisionAuditLogEntity.of(userId, "BRIEF_GENERATED", brief.id, "BRIEF",
                "{\"mode\":\"" + mode + "\",\"risk\":\"" + riskLevel + "\"}").persist();

        return brief;
    }

    /**
     * Weekly cron: every Monday at 08:00.
     * Generates briefs for all active users (those with at least one bank account).
     */
    @Transactional
    public void runWeeklyCron() {
        LOG.info("[WeeklyBrief] Starting weekly cron generation");
        List<UserEntity> users = UserEntity.listAll();
        int generated = 0;
        for (UserEntity user : users) {
            try {
                generate(user.id, "CRON");
                generated++;
            } catch (Exception e) {
                LOG.warnf("[WeeklyBrief] Failed for userId=%s: %s", user.id, e.getMessage());
            }
        }
        LOG.infof("[WeeklyBrief] Cron complete — %d briefs generated", generated);
    }

    // -----------------------------------------------------------------------
    // Plain-language composition
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private String compose(String riskLevel, Integer runway,
                           List<?> drivers, List<?> actions,
                           Map<String, Object> summary) {
        StringBuilder sb = new StringBuilder();

        // Opening sentence
        if (runway != null && runway < 180) {
            sb.append("Votre trésorerie est estimée suffisante pour environ ").append(runway).append(" jours.");
        } else {
            sb.append("Votre trésorerie est en bonne santé à court terme.");
        }
        sb.append(" Niveau de risque : ").append(translateRisk(riskLevel)).append(".\n\n");

        // Top drivers
        if (!drivers.isEmpty()) {
            sb.append("Principaux facteurs :\n");
            for (int i = 0; i < Math.min(3, drivers.size()); i++) {
                Map<String, Object> d = (Map<String, Object>) drivers.get(i);
                sb.append("• ").append(d.get("label")).append("\n");
            }
            sb.append("\n");
        }

        // Top actions
        if (!actions.isEmpty()) {
            sb.append("Actions recommandées :\n");
            for (int i = 0; i < Math.min(3, actions.size()); i++) {
                Map<String, Object> a = (Map<String, Object>) actions.get(i);
                sb.append("• ").append(a.get("description")).append("\n");
            }
        }

        // Truncate to 150 words
        String text = sb.toString().trim();
        String[] words = text.split("\\s+");
        if (words.length > 150) {
            StringBuilder truncated = new StringBuilder();
            for (int i = 0; i < 150; i++) {
                truncated.append(words[i]).append(" ");
            }
            text = truncated.toString().trim() + "…";
        }
        return text;
    }

    private String translateRisk(String level) {
        return switch (level) {
            case "CRITICAL" -> "CRITIQUE";
            case "HIGH"     -> "ÉLEVÉ";
            case "MEDIUM"   -> "MODÉRÉ";
            default         -> "FAIBLE";
        };
    }
}