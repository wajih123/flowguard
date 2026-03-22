package com.flowguard.resource;

import com.flowguard.domain.InvoiceEntity;
import com.flowguard.dto.ClientStatsDto;
import com.flowguard.repository.InvoiceRepository;
import com.flowguard.security.Roles;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Path("/client-stats")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.BUSINESS, Roles.ADMIN, Roles.SUPER_ADMIN})
public class ClientStatsResource {

    private static final double CONCENTRATION_THRESHOLD = 40.0;

    @Inject InvoiceRepository invoiceRepository;
    @Inject JsonWebToken      jwt;

    @GET
    @RunOnVirtualThread
    public List<ClientStatsDto> list() {
        UUID userId = UUID.fromString(jwt.getSubject());

        List<InvoiceEntity> all = invoiceRepository.findByUserId(userId);
        if (all.isEmpty()) return List.of();

        // Total revenue across all paid invoices for share calculation
        BigDecimal totalRevenue = all.stream()
                .filter(i -> i.getStatus() == InvoiceEntity.InvoiceStatus.PAID)
                .map(InvoiceEntity::getTotalTtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group invoices by client name (case-insensitive)
        Map<String, List<InvoiceEntity>> byClient = all.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getClientName() != null
                                ? i.getClientName().trim().toLowerCase()
                                : "inconnu"));

        List<ClientStatsDto> result = new ArrayList<>();
        for (Map.Entry<String, List<InvoiceEntity>> entry : byClient.entrySet()) {
            List<InvoiceEntity> clientInvoices = entry.getValue();
            InvoiceEntity sample = clientInvoices.get(0);

            // Use original (non-lowercased) client name from newest invoice
            String displayName = clientInvoices.stream()
                    .max(Comparator.comparing(InvoiceEntity::getIssueDate))
                    .map(InvoiceEntity::getClientName)
                    .orElse(entry.getKey());

            String clientEmail = sample.getClientEmail();

            int invoiceCount = clientInvoices.size();

            // Paid invoices
            List<InvoiceEntity> paid = clientInvoices.stream()
                    .filter(i -> i.getStatus() == InvoiceEntity.InvoiceStatus.PAID)
                    .toList();

            BigDecimal clientRevenue = paid.stream()
                    .map(InvoiceEntity::getTotalTtc)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Outstanding (SENT + OVERDUE)
            BigDecimal outstanding = clientInvoices.stream()
                    .filter(i -> i.getStatus() == InvoiceEntity.InvoiceStatus.SENT
                            || i.getStatus() == InvoiceEntity.InvoiceStatus.OVERDUE)
                    .map(InvoiceEntity::getTotalTtc)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Average payment delay in days (issueDate → paidAt)
            int avgPaymentDays = 0;
            if (!paid.isEmpty()) {
                long totalDays = 0;
                int counted = 0;
                for (InvoiceEntity inv : paid) {
                    if (inv.getPaidAt() != null) {
                        long days = ChronoUnit.DAYS.between(
                                inv.getIssueDate(),
                                inv.getPaidAt().atZone(ZoneOffset.UTC).toLocalDate());
                        if (days >= 0) {
                            totalDays += days;
                            counted++;
                        }
                    }
                }
                if (counted > 0) avgPaymentDays = (int) (totalDays / counted);
            }

            // Predicted payment days for outstanding = average + small buffer
            int predictedPaymentDays = avgPaymentDays > 0 ? avgPaymentDays : 30;

            // Revenue share
            double revenueShare = 0.0;
            if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
                revenueShare = clientRevenue
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenue, 1, RoundingMode.HALF_UP)
                        .doubleValue();
            }

            result.add(new ClientStatsDto(
                    displayName,
                    clientEmail,
                    invoiceCount,
                    paid.size(),
                    clientRevenue,
                    outstanding,
                    revenueShare,
                    avgPaymentDays,
                    predictedPaymentDays,
                    revenueShare >= CONCENTRATION_THRESHOLD
            ));
        }

        // Sort by revenue descending
        result.sort(Comparator.comparing(ClientStatsDto::totalRevenue).reversed());
        return result;
    }
}
