package com.flowguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.TreasuryForecastDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Calls the FlowGuard ML Service v2 endpoint (Model Race — always returns
 * the most accurate model based on rolling 30-day MAE).
 *
 * Request:  POST {mlServiceUrl}/v2/predict
 * Payload:  { account_id, transactions: [{date, amount, balance, ...}], horizon }
 * Response: { daily_balance, critical_points, confidence_score, model_used, ... }
 */
@ApplicationScoped
public class TreasuryService {

    private static final Logger LOG = Logger.getLogger(TreasuryService.class);

    @ConfigProperty(name = "flowguard.ml-service.url")
    String mlServiceUrl;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Fetches a cash-flow forecast for the user's primary account by:
     * 1. Loading the last 6 months of transactions from the DB
     * 2. POSTing them to ML /v2/predict (Model Race)
     * 3. Mapping the response to TreasuryForecastDto
     */
    public TreasuryForecastDto getForecast(UUID userId, int horizonDays) {
        // ── 1. Find primary account ───────────────────────────────────────────
        var accounts = accountRepository.findActiveByUserId(userId);
        if (accounts.isEmpty()) {
            return emptyForecast();
        }
        var primary = accounts.stream()
                .filter(a -> a.getSyncStatus() != null
                        && a.getSyncStatus().name().equals("OK"))
                .findFirst()
                .orElse(accounts.get(0));

        // ── 2. Load last 6 months of transactions ─────────────────────────────
        LocalDate since = LocalDate.now().minusMonths(6);
        List<TransactionEntity> txs = transactionRepository
                .findByAccountIdAndDateBetween(primary.getId(), since, LocalDate.now());

        if (txs.size() < 5) {
            LOG.infof("Not enough transactions for ML forecast (userId=%s, count=%d)", userId, txs.size());
            return emptyForecast();
        }

        // ── 3. Build JSON payload ─────────────────────────────────────────────
        try {
            // Ensure ascending sort by date before computing running balance
            txs.sort(java.util.Comparator.comparing(TransactionEntity::getDate));

            // Compute running balance per transaction (no balance column in DB).
            // Strategy: walk backwards from current account balance.
            double currentBal = primary.getBalance() != null
                    ? primary.getBalance().doubleValue() : 0.0;
            double[] runningBalances = new double[txs.size()];
            runningBalances[txs.size() - 1] = currentBal;
            for (int i = txs.size() - 2; i >= 0; i--) {
                runningBalances[i] = runningBalances[i + 1]
                        - txs.get(i + 1).getAmount().doubleValue();
            }

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("account_id", primary.getId().toString());
            payload.put("horizon", horizonDays);

            ArrayNode txArray = payload.putArray("transactions");
            for (int i = 0; i < txs.size(); i++) {
                TransactionEntity tx = txs.get(i);
                ObjectNode item = txArray.addObject();
                item.put("date", tx.getDate().toString());
                item.put("amount", tx.getAmount().doubleValue());
                item.put("balance", runningBalances[i]);
                item.put("description", tx.getLabel());
                item.put("category", tx.getCategory() != null
                        ? tx.getCategory().name() : null);
            }

            String body = objectMapper.writeValueAsString(payload);

            // ── 4. POST to ML /v2/predict ─────────────────────────────────────
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServiceUrl + "/v2/predict"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 422) {
                LOG.infof("ML service: insufficient data for userId=%s", userId);
                return emptyForecast();
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("ML service returned status " + response.statusCode());
            }

            // ── 5. Map response → TreasuryForecastDto ───────────────────────
            return mapResponse(objectMapper.readTree(response.body()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Forecast request interrupted", e);
        } catch (Exception e) {
            LOG.warnf("ML forecast failed for userId=%s: %s", userId, e.getMessage());
            throw new RuntimeException("Erreur lors de la prévision de trésorerie", e);
        }
    }

    public TreasuryForecastDto getCachedForecast(UUID userId, int horizonDays) {
        return getForecast(userId, horizonDays);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private TreasuryForecastDto mapResponse(JsonNode root) {
        List<TreasuryForecastDto.ForecastPoint> points = new ArrayList<>();
        JsonNode daily = root.path("daily_balance");
        if (daily.isArray()) {
            for (JsonNode d : daily) {
                points.add(new TreasuryForecastDto.ForecastPoint(
                        LocalDate.parse(d.path("date").asText()),
                        BigDecimal.valueOf(d.path("balance").asDouble()),
                        BigDecimal.valueOf(d.path("balance_p25").asDouble()),
                        BigDecimal.valueOf(d.path("balance_p75").asDouble())
                ));
            }
        }

        List<TreasuryForecastDto.CriticalPoint> criticals = new ArrayList<>();
        JsonNode cps = root.path("critical_points");
        if (cps.isArray()) {
            for (JsonNode cp : cps) {
                criticals.add(new TreasuryForecastDto.CriticalPoint(
                        LocalDate.parse(cp.path("date").asText()),
                        BigDecimal.valueOf(cp.path("predicted_balance").asDouble()),
                        cp.path("cause").asText()
                ));
            }
        }

        double confidence = root.path("confidence_score").asDouble(0.5);
        double healthScore = root.path("data_quality_score").asDouble(0.5);
        String modelUsed = root.path("model_used").asText("unknown");
        String raceWinner = root.path("model_race_winner").asText(null);
        if (raceWinner != null && raceWinner.equals("null")) raceWinner = null;

        return new TreasuryForecastDto(
                points,
                criticals,
                confidence,
                healthScore,
                LocalDate.now(),
                modelUsed,
                raceWinner
        );
    }

    private TreasuryForecastDto emptyForecast() {
        return new TreasuryForecastDto(
                List.of(), List.of(), 0.0, 0.0, LocalDate.now(), "none", null);
    }
}
