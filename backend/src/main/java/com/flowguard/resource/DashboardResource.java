package com.flowguard.resource;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.AlertEntity;
import com.flowguard.dto.AlertDto;
import com.flowguard.dto.TreasuryForecastDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.TransactionRepository;
import com.flowguard.service.AlertService;
import com.flowguard.service.CreditScoringService;
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
import java.math.RoundingMode;
import java.time.Instant;
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
    @Inject JsonWebToken jwt;

    // ── GET /api/dashboard/summary ───────────────────────────────────────────

    @GET
    @Path("/summary")
    @Transactional
    public Response getSummary() {
        UUID userId = UUID.fromString(jwt.getSubject());

        // 1. Pick the primary account (first OK, or first available)
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) {
            return Response.ok(emptyDashboard()).build();
        }
        AccountEntity primary = accounts.stream()
                .filter(a -> a.getSyncStatus() == AccountEntity.SyncStatus.OK)
                .findFirst()
                .orElse(accounts.get(0));

        BigDecimal currentBalance = primary.getBalance() != null
                ? primary.getBalance() : BigDecimal.ZERO;

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

        // 3. Balance trend (percentage change over 30 days)
        double balanceTrend = 0;
        if (currentBalance.compareTo(BigDecimal.ZERO) != 0) {
            balanceTrend = predictedBalance30d.subtract(currentBalance)
                    .divide(currentBalance.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
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
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", primary.getId().toString());
        account.put("bankName", primary.getBankName() != null ? primary.getBankName() : "");
        account.put("ibanMasked", maskIban(primary.getIban()));
        account.put("currentBalance", currentBalance);
        account.put("currency", primary.getCurrency() != null ? primary.getCurrency() : "EUR");
        account.put("lastSyncAt", primary.getLastSyncAt() != null
                ? primary.getLastSyncAt().toString() : null);
        account.put("syncStatus", primary.getSyncStatus().name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account", account);
        body.put("currentBalance", currentBalance);
        body.put("predictedBalance30d", predictedBalance30d);
        body.put("balanceTrend", balanceTrend);
        body.put("healthScore", healthScore);
        body.put("healthLabel", healthLabel);
        body.put("reserveAvailable", reserveAvailable);
        body.put("reserveMaxAmount", reserveMaxAmount);
        body.put("hasHighAlert", hasHighAlert);
        body.put("highAlertMessage", highAlertMessage);
        body.put("highAlertAmount", highAlertAmount);
        body.put("highAlertDate", highAlertDate);

        return Response.ok(body).build();
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
