package com.flowguard.service;

import com.flowguard.domain.InvoiceEntity;
import com.flowguard.repository.InvoiceRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job that sends automatic payment reminders for overdue invoices.
 *
 * <p>Runs daily at 09:00 and sends a reminder email on day +7, +15, and +30 past due date,
 * but only for invoices where {@code reminderEnabled = true}.
 */
@ApplicationScoped
public class InvoiceReminderScheduler {

    private static final Logger LOG = Logger.getLogger(InvoiceReminderScheduler.class);
    private static final int[] REMINDER_DAYS = {7, 15, 30};

    @Inject InvoiceRepository invoiceRepository;
    @Inject Mailer mailer;

    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    void sendReminders() {
        LocalDate today = LocalDate.now();
        List<InvoiceEntity> pendingInvoices = invoiceRepository.findPendingReminders();

        for (InvoiceEntity inv : pendingInvoices) {
            if (inv.getUser().getEmail() == null || inv.getUser().getEmail().isBlank()) continue;

            int daysOverdue = (int) ChronoUnit.DAYS.between(inv.getDueDate(), today);
            if (daysOverdue <= 0) continue;

            boolean shouldSend = false;
            for (int threshold : REMINDER_DAYS) {
                if (daysOverdue == threshold) {
                    shouldSend = true;
                    break;
                }
            }
            if (!shouldSend) continue;

            try {
                sendReminderEmail(inv, daysOverdue);
                LOG.infof("Reminder sent for invoice %s (%s, %d days overdue)",
                        inv.getNumber(), inv.getUser().getEmail(), daysOverdue);
            } catch (Exception e) {
                LOG.errorf("Failed to send reminder for invoice %s: %s", inv.getId(), e.getMessage());
            }
        }
    }

    private void sendReminderEmail(InvoiceEntity inv, int daysOverdue) {
        String clientEmail = inv.getClientEmail();
        if (clientEmail == null || clientEmail.isBlank()) return;

        String subject = String.format("Rappel de paiement — Facture N° %s (%d jours de retard)",
                inv.getNumber(), daysOverdue);

        String html = """
                <html><body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: auto;">
                  <h2 style="color: #e74c3c;">Rappel de paiement</h2>
                  <p>Bonjour,</p>
                  <p>La facture <strong>N° %s</strong> d'un montant de <strong>%.2f € TTC</strong>
                     est en attente de règlement depuis <strong>%d jours</strong>.</p>
                  <table style="width:100%%; border-collapse:collapse; margin: 16px 0;">
                    <tr style="background:#f5f5f5;">
                      <td style="padding:8px; border:1px solid #ddd;"><strong>Numéro</strong></td>
                      <td style="padding:8px; border:1px solid #ddd;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:8px; border:1px solid #ddd;"><strong>Montant TTC</strong></td>
                      <td style="padding:8px; border:1px solid #ddd;">%.2f €</td>
                    </tr>
                    <tr style="background:#f5f5f5;">
                      <td style="padding:8px; border:1px solid #ddd;"><strong>Date d'échéance</strong></td>
                      <td style="padding:8px; border:1px solid #ddd;">%s</td>
                    </tr>
                  </table>
                  <p>Merci de procéder au règlement dans les meilleurs délais.</p>
                  <p style="color:#999; font-size:12px;">Ce message est envoyé automatiquement par FlowGuard.</p>
                </body></html>
                """.formatted(
                inv.getNumber(), inv.getTotalTtc().doubleValue(), daysOverdue,
                inv.getNumber(), inv.getTotalTtc().doubleValue(), inv.getDueDate().toString()
        );

        mailer.send(Mail.withHtml(clientEmail, subject, html));
    }
}
