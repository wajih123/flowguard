package fr.flowguard.decision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.flowguard.banking.entity.BankAccountEntity;
import fr.flowguard.banking.entity.TransactionEntity;
import fr.flowguard.banking.ml.MlServiceClient;
import fr.flowguard.decision.entity.*;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Orchestrates the full decision engine pipeline:
 *   1. Fetch ML forecast (or use cached)
 *   2. Detect cash drivers
 *   3. Score risk
 *   4. Generate recommendations
 *   5. Audit log
 *   6. Cache result for <1s subsequent reads
 */
@ApplicationScoped
public class DecisionEngineService {

    private static final Logger LOG = Logger.getLogger(DecisionEngineService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_KEY_PREFIX = "decision:summary:";

    @Inject MlServiceClient mlClient;
    @Inject DriverDetectionService driverDetection;
    @Inject RiskScoringService riskScoring;
    @Inject RecommendationEngineService recommendations;
    @Inject RedisDataSource redis;

    private final ObjectMapper om = new ObjectMapper();

    /**
     * Compute (or return cached) full decision summary for a user.
     * Cache TTL = 10 minutes.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSummary(String userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;
        ValueCommands<String, String> vals = redis.value(String.class);
        String cached = vals.get(cacheKey);
        if (cached != null) {
            try {
                return om.readValue(cached, Map.class);
            } catch (Exception e) {
                LOG.warnf("[DE] Cache parse error for %s", userId);
            }
        }
        Map<String, Object> result = compute(userId);
        try {
            vals.set(cacheKey, om.writeValueAsString(result));
            redis.key(String.class).expire(cacheKey, CACHE_TTL);
        } catch (Exception e) {
            LOG.warnf("[DE] Cache write error: %s", e.getMessage());
        }
        return result;
    }

    /** Invalidate cache — call whenever underlying data changes. */
    public void invalidate(String userId) {
        try {
            redis.key(String.class).del(CACHE_KEY_PREFIX + userId);
        } catch (Exception e) {
            LOG.warnf("[DE] Cache invalidate error: %s", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> compute(String userId) {
        // --- 1. Fetch ML forecast ---
        BigDecimal mlMinBalance = null;
        LocalDate mlMinDate = null;
        boolean mlDeficit = false;
        Map<String, Object> mlResult = null;

        List<BankAccountEntity> accounts = BankAccountEntity.findActiveByUserId(userId);
        if (!accounts.isEmpty()) {
            BankAccountEntity account = accounts.stream()
                .filter(a -> "CHECKING".equalsIgnoreCase(a.accountType))
                .findFirst().orElse(accounts.get(0));
            List<TransactionEntity> txs = TransactionEntity.findByAccountIdOrderedAsc(account.id);
            if (txs.size() >= 5) {
                BigDecimal[] balances = new BigDecimal[txs.size()];
                BigDecimal running = account.currentBalance != null ? account.currentBalance : BigDecimal.ZERO;
                for (int i = txs.size() - 1; i >= 0; i--) {
                    balances[i] = running;
                    running = running.subtract(txs.get(i).amount != null ? txs.get(i).amount : BigDecimal.ZERO);
                }
                List<Map<String, Object>> mlTxs = new ArrayList<>();
                for (int i = 0; i < txs.size(); i++) {
                    TransactionEntity tx = txs.get(i);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", tx.transactionDate.toString());
                    item.put("amount", tx.amount != null ? tx.amount.doubleValue() : 0.0);
                    item.put("balance", balances[i].doubleValue());
                    item.put("description", tx.label != null ? tx.label : "");
                    item.put("category", tx.category);
                    mlTxs.add(item);
                }
                mlResult = mlClient.predict(account.id, mlTxs, 30);
                if (mlResult != null) {
                    Object minBal = mlResult.get("min_balance");
                    if (minBal instanceof Number n) mlMinBalance = BigDecimal.valueOf(n.doubleValue());
                    Object minDate = mlResult.get("min_balance_date");
                    if (minDate instanceof String s && !s.isBlank()) {
                        try { mlMinDate = LocalDate.parse(s); } catch (Exception ignored) {}
                    }
                    mlDeficit = Boolean.TRUE.equals(mlResult.get("predicted_deficit"));
                }
            }
        }

        // --- 2. Detect drivers ---
        // We need a snapshot ID before persisting drivers, so create a placeholder first
        String tempSnapshotId = UUID.randomUUID().toString();
        // Temporarily set snapshotId on driver records via detect()
        List<CashDriverEntity> drivers = driverDetection.detect(userId, tempSnapshotId);

        // --- 3. Score risk ---
        CashRiskSnapshotEntity snapshot = riskScoring.computeAndPersist(
                userId, mlMinBalance, mlMinDate, mlDeficit, drivers);

        // Re-link drivers to actual snapshot id (drivers were persisted with tempSnapshotId)
        if (!snapshot.id.equals(tempSnapshotId)) {
            // Update snapshotId in driver rows
            for (CashDriverEntity d : drivers) {
                d.snapshotId = snapshot.id;
            }
        }

        // --- 4. Recommendations ---
        List<CashRecommendationEntity> recos = recommendations.generate(userId, snapshot, drivers);

        // --- 5. Audit log ---
        auditLog(userId, "SNAPSHOT_CREATED", snapshot.id, "SNAPSHOT",
                "{\"riskLevel\":\"" + snapshot.riskLevel + "\",\"runway\":" + snapshot.runwayDays + "}");

        // --- 6. Build response ---
        return buildSummaryMap(snapshot, drivers, recos, mlResult);
    }

    @Transactional
    void auditLog(String userId, String eventType, String entityId, String entityType, String payload) {
        DecisionAuditLogEntity.of(userId, eventType, entityId, entityType, payload).persist();
    }

    private Map<String, Object> buildSummaryMap(
            CashRiskSnapshotEntity snap,
            List<CashDriverEntity> drivers,
            List<CashRecommendationEntity> recos,
            Map<String, Object> mlResult) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("snapshotId", snap.id);
        m.put("computedAt", snap.computedAt.toString());
        m.put("riskLevel", snap.riskLevel);
        m.put("runwayDays", snap.runwayDays);
        m.put("currentBalance", snap.currentBalance);
        m.put("minProjectedBalance", snap.minBalance);
        m.put("minProjectedDate", snap.minBalanceDate != null ? snap.minBalanceDate.toString() : null);
        m.put("deficitPredicted", snap.deficitPredicted);
        m.put("volatilityScore", snap.volatilityScore);
        m.put("drivers", drivers.stream().map(d -> {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.id);
            dm.put("type", d.driverType);
            dm.put("label", d.label);
            dm.put("amount", d.amount);
            dm.put("impactDays", d.impactDays);
            dm.put("dueDate", d.dueDate != null ? d.dueDate.toString() : null);
            dm.put("referenceId", d.referenceId);
            dm.put("referenceType", d.referenceType);
            dm.put("rank", d.rank);
            return dm;
        }).toList());
        m.put("actions", recos.stream().map(r -> {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.id);
            rm.put("actionType", r.actionType);
            rm.put("description", r.description);
            rm.put("estimatedImpact", r.estimatedImpact);
            rm.put("horizonDays", r.horizonDays);
            rm.put("confidence", r.confidence);
            rm.put("status", r.status);
            return rm;
        }).toList());
        if (mlResult != null) {
            m.put("forecast", Map.of(
                "confidenceScore", mlResult.getOrDefault("confidence_score", 0),
                "horizonDays", mlResult.getOrDefault("horizon_days", 30),
                "dailyData", mlResult.getOrDefault("daily_balance", List.of())
            ));
        }
        return m;
    }
}