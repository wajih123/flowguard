package com.flowguard.service;

import com.flowguard.domain.UserEntity;
import com.flowguard.dto.DecisionSummaryDto;
import com.flowguard.repository.UserRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Scheduled job that sends a weekly financial brief to all users every Monday at 08:00.
 *
 * <p>Uses the Decision Engine to compute each user's current cash risk summary and
 * formats it into a concise HTML email.
 */
@ApplicationScoped
public class WeeklyBriefScheduler {

    private static final Logger LOG = Logger.getLogger(WeeklyBriefScheduler.class);

    @Inject UserRepository userRepository;
    @Inject DecisionEngineService decisionEngineService;
    @Inject Mailer mailer;

    @Scheduled(cron = "0 0 8 ? * MON")
    @Transactional
    void sendWeeklyBriefs() {
        LOG.info("Starting weekly financial brief job");
        List<UserEntity> users = userRepository.listAll();
        int sent = 0;
        int failed = 0;

        for (UserEntity user : users) {
            try {
                DecisionSummaryDto summary = decisionEngineService.getSummary(user.getId());
                sendBriefEmail(user, summary);
                sent++;
            } catch (Exception e) {
                LOG.errorf("Failed to send weekly brief to user %s: %s", user.getId(), e.getMessage());
                failed++;
            }
        }

        LOG.infof("Weekly brief job complete — sent=%d, failed=%d", sent, failed);
    }

    private void sendBriefEmail(UserEntity user, DecisionSummaryDto summary) {
        String displayName = user.getFirstName() != null ? user.getFirstName() : "Utilisateur";
        String riskColor = switch (summary.riskLevel()) {
            case "HIGH" -> "#e74c3c";
            case "MEDIUM" -> "#f39c12";
            default -> "#27ae60";
        };

        String deficitBadge = summary.deficitPredicted()
                ? "<span style=\"color:#e74c3c;\">⚠ Déficit prévu prochainement</span>"
                : "<span style=\"color:#27ae60;\">✓ Trésorerie saine</span>";

        String html = """
                <html><body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: auto;">
                  <div style="background: linear-gradient(135deg, #1a1a2e 0%%, #16213e 100%%); padding: 24px; border-radius: 12px; margin-bottom: 20px;">
                    <h1 style="color: #fff; margin: 0; font-size: 22px;">📊 Votre bilan financier de la semaine</h1>
                    <p style="color: #aaa; margin: 8px 0 0;">Bonjour %s — voici votre résumé FlowGuard</p>
                  </div>

                  <div style="display: grid; gap: 12px; margin-bottom: 20px;">
                    <div style="background: #f8f9fa; border-radius: 8px; padding: 16px;">
                      <h3 style="margin: 0 0 12px; color: #555;">Situation actuelle</h3>
                      <table style="width: 100%%;">
                        <tr>
                          <td>Solde agrégé</td>
                          <td style="text-align:right; font-weight:bold;">%.2f €</td>
                        </tr>
                        <tr>
                          <td>Runway estimé</td>
                          <td style="text-align:right; font-weight:bold;">%d jours</td>
                        </tr>
                        <tr>
                          <td>Niveau de risque</td>
                          <td style="text-align:right; font-weight:bold; color:%s;">%s</td>
                        </tr>
                        <tr>
                          <td>Statut</td>
                          <td style="text-align:right;">%s</td>
                        </tr>
                      </table>
                    </div>
                  </div>

                  %s

                  <div style="border-top: 1px solid #eee; padding-top: 16px; margin-top: 20px;">
                    <p style="color: #aaa; font-size: 12px; text-align: center;">
                      FlowGuard — Tableau de bord financier intelligent<br>
                      Pour désactiver ces emails, rendez-vous dans vos paramètres de profil.
                    </p>
                  </div>
                </body></html>
                """.formatted(
                displayName,
                summary.currentBalance().doubleValue(),
                summary.runwayDays(),
                riskColor, summary.riskLevel(),
                deficitBadge,
                buildActionsHtml(summary)
        );

        mailer.send(Mail.withHtml(
                user.getEmail(),
                "📊 Votre bilan FlowGuard — " + java.time.LocalDate.now(),
                html
        ));
    }

    private String buildActionsHtml(DecisionSummaryDto summary) {
        if (summary.actions() == null || summary.actions().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"background: #fff3cd; border: 1px solid #ffc107; border-radius: 8px; padding: 16px;\">");
        sb.append("<h3 style=\"margin: 0 0 8px; color: #856404;\">Actions recommandées</h3><ul style=\"margin: 0; padding-left: 20px;\">");
        // Show top 3 actions
        summary.actions().stream().limit(3).forEach(action ->
                sb.append("<li style=\"margin-bottom: 6px;\">").append(action.description()).append("</li>")
        );
        sb.append("</ul></div>");
        return sb.toString();
    }
}
