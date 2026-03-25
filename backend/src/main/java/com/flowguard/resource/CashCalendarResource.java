package com.flowguard.resource;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.InvoiceEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.CashCalendarEventDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.InvoiceRepository;
import com.flowguard.repository.TransactionRepository;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Path("/cash-calendar")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class CashCalendarResource {

    @Inject InvoiceRepository     invoiceRepository;
    @Inject TransactionRepository transactionRepository;
    @Inject AccountRepository     accountRepository;
    @Inject JsonWebToken          jwt;

    @GET
    @RunOnVirtualThread
    public List<CashCalendarEventDto> list() {
        UUID userId = UUID.fromString(jwt.getSubject());
        LocalDate today  = LocalDate.now();
        LocalDate future = today.plusDays(60);

        List<CashCalendarEventDto> events = new ArrayList<>();

        // ── 1. Invoices: DRAFT (scheduled), SENT + OVERDUE ──
        List<InvoiceEntity> invoices = invoiceRepository.findByUserId(userId);
        for (InvoiceEntity inv : invoices) {
            LocalDate due = inv.getDueDate();
            boolean inWindow = !due.isBefore(today.minusDays(90)) && !due.isAfter(future);

            if (inv.getStatus() == InvoiceEntity.InvoiceStatus.DRAFT && !due.isBefore(today) && !due.isAfter(future)) {
                // Planned but not yet sent — show as upcoming expected inflow
                events.add(new CashCalendarEventDto(
                        due,
                        "INVOICE_SCHEDULED",
                        "Facture prévue — " + inv.getNumber(),
                        inv.getTotalTtc(),
                        "PENDING",
                        inv.getClientName()
                ));
            } else if ((inv.getStatus() == InvoiceEntity.InvoiceStatus.SENT
                    || inv.getStatus() == InvoiceEntity.InvoiceStatus.OVERDUE) && inWindow) {
                boolean isOverdue = due.isBefore(today);
                events.add(new CashCalendarEventDto(
                        due,
                        isOverdue ? "INVOICE_OVERDUE" : "INVOICE_DUE",
                        "Facture — " + inv.getNumber(),
                        inv.getTotalTtc(),
                        isOverdue ? "OVERDUE" : "PENDING",
                        inv.getClientName()
                ));
            }
        }

        // ── 2. Recurring transactions: project next occurrence ──
        List<AccountEntity> accounts = accountRepository.findActiveByUserId(userId);
        for (AccountEntity acc : accounts) {
            List<TransactionEntity> recurring = transactionRepository.findRecurringByAccountId(acc.getId());

            // Group by label to find most-recent per label, then project next date
            Map<String, TransactionEntity> latest = new LinkedHashMap<>();
            for (TransactionEntity tx : recurring) {
                latest.merge(
                        tx.getLabel() != null ? tx.getLabel().toLowerCase().trim() : "?",
                        tx,
                        (a, b) -> a.getDate().isAfter(b.getDate()) ? a : b
                );
            }

            for (TransactionEntity tx : latest.values()) {
                // Estimate recurrence period from all transactions with same label
                LocalDate nextDate = tx.getDate().plusMonths(1); // default: monthly
                if (nextDate.isAfter(today) && !nextDate.isAfter(future)) {
                    String type = tx.getType() == TransactionEntity.TransactionType.DEBIT
                            ? "RECURRING_CHARGE" : "RECURRING_INCOME";
                    // Debits are negative amounts, credits positive
                    BigDecimal amount = tx.getType() == TransactionEntity.TransactionType.DEBIT
                            ? tx.getAmount().abs().negate()
                            : tx.getAmount().abs();
                    events.add(new CashCalendarEventDto(
                            nextDate,
                            type,
                            tx.getLabel() != null ? tx.getLabel() : "Récurrent",
                            amount,
                            "PREDICTED",
                            null
                    ));
                }
            }
        }

        // Sort by date ascending
        events.sort(Comparator.comparing(CashCalendarEventDto::date));
        return events;
    }
}
