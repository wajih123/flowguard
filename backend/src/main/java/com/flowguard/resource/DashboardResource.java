package com.flowguard.resource;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.AlertEntity;
import com.flowguard.dto.AlertDto;
import com.flowguard.dto.TreasuryForecastDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.TransactionRepository;
import com.flowguard.service.AlertService;
import com.flowguard.service.CreditScoringService;
import com.flowguard.service.SpendingAnalysisService;
import com.flowguard.service.TreasuryService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.*;

/**
 * Aggregated dashboard endpoints consumed by the frontend.
 *
 * <ul>
 *   <li>{@code GET /api/dashboard/summary}          — health score, balance, alert summary
 *   <li>{@code GET /api/dashboard/transactions}      — latest N transactions across all accounts
 * </ul>
 */
@Path("/dashboard")
@RolesAllowed("user")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DashboardResource {

    private static final Logger LOG = Logger.getLogger(DashboardResource.class);

    @Inject AccountRepository accountRepository;
    @Inject TransactionRepository transactionRepository;
    @Inject AlertService alertService;
    @Inject CreditScoringService creditScoringService;
    @Inject TreasuryService treasuryService;
    @Inject SpendingAnalysisService spendingAnalysisService;
    @Inject JsonWebToken jwt;

    // ── GET /api/dashboard/summary ───────────────────────────────────────────

    @GET
    @Path("/summary")
    @Transactional
    public Response getSummary() {
        UUID userId = UUID.fromString(jwt.getSubject());

        // 1. Aggregate balance across ALL active accounts — aligned with DecisionEngineService
        //    so both pages show the same number for the same user.
        List<AccountEntity> activeAccounts = accountRepository.findActiveByUserId(userId);
        if (activeAccounts.isEmpty()) {
            // Fallback: user just connected, accounts may still be PENDING/SYNCING
            List<AccountEntity> allAccounts = accountRepository.findByUserId(userId);
            if (allAccounts.isEmpty()) {
                return Response.ok(emptyDashboard()).build();
            }
            activeAccounts = allAccounts;
        }

        // Primary account is used only for display (bank name, masked IBAN)
        AccountEntity primary = activeAccounts.stream()
                .filter(a -> a.getSyncStatus() == AccountEntity.SyncStatus.OK)
                .findFirst()
                .orElse(activeAccounts.get(0));

        // Aggregate balance — same formula as DecisionEngineService.computeSnapshot()
        BigDecimal currentBalance = activeAccounts.stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 2. Forecast (best-effort — ML service may not have enough data yet)
        BigDecimal predictedBalance30d = currentBalance;
        double forecastHealthScore = -1;
        try {
            TreasuryForecastDto forecast = treasuryService.getForecast(userId, 30);
            if (forecast != null && forecast.predictions() != null && !forecast.predictions().isEmpty()) {
                TreasuryForecastDto.ForecastPoint last =
                        forecast.predictions().get(forecast.predictions().size() - 1);
                if (last.predictedBalance() != null) {
                    predictedBalance30d = last.predictedBalance();
                }
            }
            if (forecast != null) {
                // healthScore from ML service is already 0-100
                forecastHealthScore = forecast.healthScore();
            }
        } catch (Exception e) {
            LOG.debugf("Forecast unavailable for user %s: %s", userId, e.getMessage());
        }

        // 4. Health score — prefer ML forecast, fall back to credit scoring
        int healthScore;
        String healthLabel;
        try {
            healthScore = forecastHealthScore >= 0
                    ? (int) Math.round(forecastHealthScore)
                    : creditScoringService.computeScore(userId);
        } catch (Exception e) {
            healthScore = 50;
        }

        // Safety cap on the fallback credit score path: if the ML service is
        // unavailable AND the current balance is negative, the credit-history-based
        // score (which only looks at past income) must be capped.
        // The ML path already incorporates current_balance as a first-class component.
        if (forecastHealthScore < 0 && currentBalance.compareTo(BigDecimal.ZERO) < 0) {
            double bal = currentBalance.doubleValue();
            int balanceCap;
            if (bal <= -5000)      balanceCap = 20;
            else if (bal <= -1000) balanceCap = 30;
            else if (bal <= -200)  balanceCap = 40;
            else                   balanceCap = 45;
            healthScore = Math.min(healthScore, balanceCap);
        }

        if (healthScore >= 80) healthLabel = "Excellent";
        else if (healthScore >= 60) healthLabel = "Bon";
        else if (healthScore >= 40) healthLabel = "Moyen";
        else healthLabel = "Fragile";

        // 5. Flash-credit reserve eligibility
        boolean reserveAvailable = healthScore >= CreditScoringService.MIN_SCORE;
        BigDecimal reserveMaxAmount = reserveAvailable
                ? new BigDecimal("5000") : BigDecimal.ZERO;

        // 6. High-priority unread alerts
        boolean hasHighAlert = false;
        String highAlertMessage = null;
        BigDecimal highAlertAmount = null;
        String highAlertDate = null;
        try {
            List<AlertDto> unread = alertService.getUnreadAlerts(userId);
            Optional<AlertDto> topAlert = unread.stream()
                    .filter(a -> a.severity() == AlertEntity.Severity.HIGH
                              || a.severity() == AlertEntity.Severity.CRITICAL)
                    .findFirst();
            if (topAlert.isPresent()) {
                hasHighAlert = true;
                AlertDto a = topAlert.get();
                highAlertMessage = a.message();
                highAlertAmount = a.projectedDeficit();
                highAlertDate = a.triggerDate() != null ? a.triggerDate().toString() : null;
            }
        } catch (Exception e) {
            LOG.debugf("Alert lookup failed for user %s: %s", userId, e.getMessage());
        }

        // 7. Build account sub-object (matches frontend BankAccount interface)
        //    currentBalance here reflects the AGGREGATED balance (same as top-level)
        //    so that any frontend code reading account.currentBalance stays consistent.
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", primary.getId().toString());
        account.put("bankName", primary.getBankName() != null ? primary.getBankName() : "");
        account.put("ibanMasked", maskIban(primary.getIban()));
        account.put("currentBalance", currentBalance); // aggregated, not primary-only
        account.put("currency", primary.getCurrency() != null ? primary.getCurrency() : "EUR");
        account.put("lastSyncAt", primary.getLastSyncAt() != null
                ? primary.getLastSyncAt().toString() : null);
        account.put("syncStatus", primary.getSyncStatus().name());

        // Calculate last month's income, spending, and savings.
        // IMPORTANT: filter by tx.getDate() (the actual transaction date on the bank statement),
        // NOT tx.getCreatedAt() (the import timestamp). Using createdAt would count all
        // historically-imported transactions as "this month" since they were all imported today.
        java.time.LocalDate thirtyDaysAgo = java.time.LocalDate.now().minusDays(30);
        BigDecimal lastMonthIncome = BigDecimal.ZERO;
        BigDecimal lastMonthSpend = BigDecimal.ZERO;

        for (AccountEntity acc : activeAccounts) {
            List<com.flowguard.domain.TransactionEntity> txs = transactionRepository.findByAccountId(acc.getId());
            for (com.flowguard.domain.TransactionEntity tx : txs) {
                if (tx.getDate() != null && !tx.getDate().isBefore(thirtyDaysAgo)
                        && !creditScoringService.isInternalTransfer(tx.getLabel())) {
                    if (tx.getType() == com.flowguard.domain.TransactionEntity.TransactionType.CREDIT) {
                        // CREDIT amounts are positive — add directly
                        lastMonthIncome = lastMonthIncome.add(tx.getAmount().abs());
                    } else if (tx.getType() == com.flowguard.domain.TransactionEntity.TransactionType.DEBIT) {
                        // DEBIT amounts are negative since V26 migration — use abs() so that
                        // lastMonthSpend is a positive total and savings = income - spend is correct.
                        lastMonthSpend = lastMonthSpend.add(tx.getAmount().abs());
                    }
                }
            }
        }

        BigDecimal lastMonthSavings = lastMonthIncome.subtract(lastMonthSpend);
        BigDecimal monthlySubscriptionsCost = BigDecimal.ZERO; // Will be calculated from SubscriptionEntity if available

        // ── Local J+30 projection ─────────────────────────────────────────────
        // Computed from actual income/spend data of the last 30 days.
        // This is the authoritative single source of truth:
        //   predictedBalance30d  = what the ML model predicted (may be stale/wrong)
        //   localPredictedBalance30d = simple math: currentBalance + income − spend
        // Rule: trust ML only when it returned a real balance (predictions list was
        // non-empty); otherwise fall back to the local calculation.
        // Extra guard: if the ML prediction deviates by more than 10x the local delta
        // (e.g. ML says -16k while math says -49), the ML data is likely stale from
        // before the V26 sign-fix migration and we override it.
        BigDecimal localPredictedBalance30d = currentBalance
                .add(lastMonthIncome)
                .subtract(lastMonthSpend);

        if (predictedBalance30d.compareTo(currentBalance) == 0) {
            // ML returned no balance predictions → use local calculation
            predictedBalance30d = localPredictedBalance30d;
        } else {
            // Sanity check: ML delta vs local delta
            BigDecimal localDelta = localPredictedBalance30d.subtract(currentBalance).abs();
            BigDecimal mlDelta    = predictedBalance30d.subtract(currentBalance).abs();
            boolean mlSane = localDelta.compareTo(BigDecimal.ZERO) == 0
                    || mlDelta.divide(localDelta.max(BigDecimal.ONE), 2, java.math.RoundingMode.HALF_UP)
                              .compareTo(BigDecimal.TEN) <= 0;
            if (!mlSane) {
                LOG.warnf("ML balance prediction (%s) deviates >10x from local estimate (%s) — overriding with local",
                        predictedBalance30d, localPredictedBalance30d);
                predictedBalance30d = localPredictedBalance30d;
            }
        }

        // Health score cap: if J+30 balance is negative the user is heading for
        // overdraft regardless of historical credit quality → enforce hard ceiling.
        if (predictedBalance30d.compareTo(BigDecimal.ZERO) < 0) {
            double proj = predictedBalance30d.doubleValue();
            int projectionCap = proj <= -5000 ? 25 : proj <= -2000 ? 35 : proj <= -500 ? 45 : 55;
            healthScore = Math.min(healthScore, projectionCap);
            // Recalculate label after cap
            if (healthScore >= 80)      healthLabel = "Excellent";
            else if (healthScore >= 60) healthLabel = "Bon";
            else if (healthScore >= 40) healthLabel = "Moyen";
            else                        healthLabel = "Fragile";
        }

        // Build accounts list breakdown
        List<Map<String, Object>> accountsList = new ArrayList<>();
        for (AccountEntity acc : activeAccounts) {
            Map<String, Object> accBreakdown = new LinkedHashMap<>();
            accBreakdown.put("id", acc.getId().toString());
            accBreakdown.put("bankName", acc.getBankName() != null ? acc.getBankName() : "");
            accBreakdown.put("ibanMasked", maskIban(acc.getIban()));
            accBreakdown.put("balance", acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO);
            accBreakdown.put("syncStatus", acc.getSyncStatus().name());
            accountsList.add(accBreakdown);
        }

        // Build upcoming debits list (empty for now, can be enhanced)
        List<Map<String, Object>> upcomingDebits = new ArrayList<>();

        // Build overdraft risk summary.
        // The risk level MUST be based on the PROJECTED balance (J+30), not the current
        // balance. A user with 10k today but -2k in 30 days is HIGH risk, not NONE.
        Map<String, Object> overdraftRisk = new LinkedHashMap<>();
        String overdraftLevel;
        if (predictedBalance30d.compareTo(BigDecimal.ZERO) < 0) {
            overdraftLevel = "HIGH";
        } else if (predictedBalance30d.compareTo(BigDecimal.valueOf(500)) < 0) {
            overdraftLevel = "HIGH";
        } else if (predictedBalance30d.compareTo(BigDecimal.valueOf(2000)) < 0) {
            overdraftLevel = "MEDIUM";
        } else {
            overdraftLevel = "NONE";
        }
        overdraftRisk.put("level", overdraftLevel);
        overdraftRisk.put("projectedBalance", predictedBalance30d);
        overdraftRisk.put("horizonDate", java.time.LocalDate.now().plusDays(30).toString());

        // Build 30-day predictions (fallback forecast chart data).
        // Uses net daily flow = (income − spend) / 30 so the projection is realistic.
        // A user earning 2 000 € and spending 1 800 € will see +200 € at day 30, not -1 800 €.
        List<Map<String, Object>> predictions = generatePredictions(currentBalance, lastMonthIncome, lastMonthSpend);

        // Count unread alerts
        int unreadAlerts = 0;
        try {
            unreadAlerts = (int) alertService.getUnreadAlerts(userId).size();
        } catch (Exception e) {
            unreadAlerts = 0;
        }

        // Build the enriched summary response
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentBalance", currentBalance);
        body.put("totalBalance", currentBalance);       // alias kept for compatibility
        body.put("account", account);                   // primary account sub-object
        body.put("accountCount", activeAccounts.size());
        body.put("accounts", accountsList);
        body.put("predictedBalance30d", predictedBalance30d);
        body.put("balanceTrend", predictedBalance30d.subtract(currentBalance));
        body.put("healthScore", healthScore);
        body.put("healthLabel", healthLabel);
        body.put("reserveAvailable", reserveAvailable);
        body.put("reserveMaxAmount", reserveMaxAmount);
        body.put("hasHighAlert", hasHighAlert);
        body.put("highAlertMessage", highAlertMessage);
        body.put("highAlertAmount", highAlertAmount);
        body.put("highAlertDate", highAlertDate);
        body.put("unreadAlerts", unreadAlerts);
        body.put("lastMonthIncome", lastMonthIncome);
        body.put("lastMonthSpend", lastMonthSpend);
        body.put("lastMonthSavings", lastMonthSavings);
        body.put("monthlySubscriptionsCost", monthlySubscriptionsCost);
        body.put("upcomingDebits", upcomingDebits);
        body.put("overdraftRisk", overdraftRisk);
        body.put("predictions", predictions);

        return Response.ok(body).build();
    }

    // ── GET /api/dashboard/spending-by-category ──────────────────────────────

    @GET
    @Path("/spending-by-category")
    @Transactional
    public Response getSpendingByCategory(
            @QueryParam("months") @DefaultValue("1") int months) {
        UUID userId = UUID.fromString(jwt.getSubject());
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.LocalDate from = now.minusMonths(Math.max(months, 1)).withDayOfMonth(1);

        // Aggregate spending across all active accounts for the requested period
        java.util.Map<String, java.math.BigDecimal> totals = new java.util.LinkedHashMap<>();
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        for (AccountEntity acc : accounts) {
            List<com.flowguard.domain.TransactionEntity> txs =
                    transactionRepository.findByAccountIdAndDateBetween(acc.getId(), from, now);
            for (com.flowguard.domain.TransactionEntity tx : txs) {
                if (tx.getType() != com.flowguard.domain.TransactionEntity.TransactionType.DEBIT) continue;
                if (tx.isInternal()) continue;
                String cat = tx.getCategory() != null ? tx.getCategory().name() : "AUTRE";
                totals.merge(cat, tx.getAmount().abs(), java.math.BigDecimal::add);
            }
        }

        List<Map<String, Object>> result = totals.entrySet().stream()
                .sorted(Map.Entry.<String, java.math.BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("category", e.getKey());
                    row.put("amount", e.getValue());
                    return row;
                })
                .toList();

        return Response.ok(result).build();
    }

    // ── GET /api/dashboard/transactions ─────────────────────────────────────

    @GET
    @Path("/transactions")
    @Transactional
    public Response getTransactions(@QueryParam("limit") @DefaultValue("5") int limit) {
        UUID userId = UUID.fromString(jwt.getSubject());

        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) {
            return Response.ok(List.of()).build();
        }

        // Gather recent transactions from all accounts, sort, and truncate
        List<Map<String, Object>> result = accounts.stream()
                .flatMap(acc -> transactionRepository.findByAccountId(acc.getId()).stream()
                        .map(tx -> {
                            Map<String, Object> t = new LinkedHashMap<>();
                            t.put("id", tx.getId().toString());
                            t.put("label", tx.getLabel());
                            t.put("amount", tx.getAmount());
                            t.put("currency", acc.getCurrency() != null ? acc.getCurrency() : "EUR");
                            t.put("transactionDate", tx.getDate().toString());
                            t.put("category", tx.getCategory().name());
                            t.put("isRecurring", tx.isRecurring());
                            return t;
                        }))
                .sorted(Comparator.comparing(t -> (String) t.get("transactionDate"),
                        Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        return Response.ok(result).build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String maskIban(String iban) {
        if (iban == null || iban.startsWith("BRIDGE-")) return null;
        if (iban.length() <= 8) return iban;
        return iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
    }

    /**
     * Generates a 30-day balance projection using the actual net daily cash flow.
     *
     * <p>Formula: {@code dailyNet = (monthlyIncome − monthlySpend) / 30}
     * Each day the running balance is adjusted by this net amount.
     *
     * <p>Using spend-only was a critical bug: a user who earns 2 000 € and spends
     * 1 800 € would show a -1 800 € collapse instead of the real +200 € gain.
     */
    private static List<Map<String, Object>> generatePredictions(
            BigDecimal currentBalance, BigDecimal monthlyIncome, BigDecimal monthlySpend) {

        // Net monthly cash flow: positive = growing balance, negative = shrinking
        BigDecimal monthlyNet = monthlyIncome.subtract(monthlySpend);
        BigDecimal dailyNet   = monthlyNet.divide(BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP);

        List<Map<String, Object>> predictions = new ArrayList<>();
        BigDecimal runningBalance = currentBalance;
        java.time.LocalDate today = java.time.LocalDate.now();

        for (int day = 0; day <= 30; day++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", today.plusDays(day).toString());
            point.put("predictedBalance", runningBalance.setScale(2, java.math.RoundingMode.HALF_UP));
            predictions.add(point);
            runningBalance = runningBalance.add(dailyNet);
        }

        return predictions;
    }

    /** Returns a safe empty dashboard when the user has no accounts yet. */
    private static Map<String, Object> emptyDashboard() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account", null);
        body.put("currentBalance", BigDecimal.ZERO);
        body.put("predictedBalance30d", BigDecimal.ZERO);
        body.put("balanceTrend", 0.0);
        body.put("healthScore", 0);
        body.put("healthLabel", "Fragile");
        body.put("reserveAvailable", false);
        body.put("reserveMaxAmount", BigDecimal.ZERO);
        body.put("hasHighAlert", false);
        body.put("highAlertMessage", null);
        body.put("highAlertAmount", null);
        body.put("highAlertDate", null);
        return body;
    }
}
