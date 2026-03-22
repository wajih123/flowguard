package com.flowguard.resource;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.SubscriptionSummaryDto;
import com.flowguard.repository.AccountRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Path("/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class SubscriptionResource {

    @Inject TransactionRepository transactionRepository;
    @Inject AccountRepository accountRepository;
    @Inject JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public List<SubscriptionSummaryDto> list() {
        UUID userId = UUID.fromString(jwt.getSubject());
        LocalDate today = LocalDate.now();
        LocalDate twelveMonthsAgo = today.minusMonths(12);

        List<TransactionEntity> recurringTxs = transactionRepository.findRecurringByUserId(userId);

        // Group by normalized label (lowercase, trimmed)
        Map<String, List<TransactionEntity>> grouped = recurringTxs.stream()
                .collect(Collectors.groupingBy(tx -> tx.getLabel() != null
                        ? tx.getLabel().toLowerCase().trim()
                        : "inconnu"));

        List<SubscriptionSummaryDto> result = new ArrayList<>();
        for (Map.Entry<String, List<TransactionEntity>> entry : grouped.entrySet()) {
            List<TransactionEntity> txs = entry.getValue();

            // Use original label from most recent transaction
            TransactionEntity mostRecent = txs.stream()
                    .max(Comparator.comparing(TransactionEntity::getDate))
                    .orElseThrow();

            LocalDate lastUsedDate = mostRecent.getDate();
            long monthsSinceLastUse = ChronoUnit.MONTHS.between(lastUsedDate, today);

            // Count occurrences in last 12 months to estimate monthly cost
            long occurrences = txs.stream()
                    .filter(tx -> !tx.getDate().isBefore(twelveMonthsAgo))
                    .count();

            // Monthly amount = average debit amount (debits are negative, use abs)
            BigDecimal avgAmount = txs.stream()
                    .filter(tx -> !tx.getDate().isBefore(twelveMonthsAgo))
                    .map(tx -> tx.getAmount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal monthlyAmount = occurrences > 0
                    ? avgAmount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                    : mostRecent.getAmount().abs();

            String category = mostRecent.getCategory() != null
                    ? mostRecent.getCategory().name()
                    : "AUTRE";

            result.add(new SubscriptionSummaryDto(
                    mostRecent.getLabel() != null ? mostRecent.getLabel() : entry.getKey(),
                    category,
                    monthlyAmount,
                    lastUsedDate,
                    (int) monthsSinceLastUse,
                    monthsSinceLastUse >= 3,
                    (int) occurrences
            ));
        }

        // Sort: stale first, then by monthly amount descending
        result.sort(Comparator.<SubscriptionSummaryDto, Boolean>comparing(s -> !s.isStale())
                .thenComparing(Comparator.<SubscriptionSummaryDto, BigDecimal>comparing(SubscriptionSummaryDto::monthlyAmount).reversed()));

        return result;
    }
}
