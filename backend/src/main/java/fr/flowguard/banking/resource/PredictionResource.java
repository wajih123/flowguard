package fr.flowguard.banking.resource;

import fr.flowguard.banking.entity.BankAccountEntity;
import fr.flowguard.banking.entity.TransactionEntity;
import fr.flowguard.banking.ml.MlServiceClient;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Path("/api/predictions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PredictionResource {

    private static final Logger LOG = Logger.getLogger(PredictionResource.class);

    @Inject JsonWebToken jwt;
    @Inject MlServiceClient mlClient;

    @POST
    @Path("/generate")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public Response generate() {
        String userId = jwt.getSubject();
        List<BankAccountEntity> accounts = BankAccountEntity.findActiveByUserId(userId);

        if (accounts.isEmpty()) {
            return Response.status(404).entity(Map.of("error",
                "Aucun compte bancaire. Connectez votre banque via la page Banque.")).build();
        }

        // Prefer checking account, fallback to first active
        BankAccountEntity account = accounts.stream()
            .filter(a -> "checking".equalsIgnoreCase(a.accountType)
                      || "CHECKING".equalsIgnoreCase(a.accountType))
            .findFirst().orElse(accounts.get(0));

        List<TransactionEntity> txsAsc = TransactionEntity.findByAccountIdOrderedAsc(account.id);

        if (txsAsc.size() < 5) {
            return Response.status(422).entity(Map.of("error",
                "Pas assez de transactions (" + txsAsc.size() + " trouvees, minimum 5 requises). "
                + "Synchronisez votre compte depuis la page Banque.")).build();
        }

        // Reconstruct running balance backwards from current balance.
        // balance[i] = account balance just AFTER transaction i (ascending order).
        // Going backwards: balance_after[n-1] = current_balance;
        //   balance_after[i] = balance_after[i+1] - tx[i+1].amount
        BigDecimal[] balances = new BigDecimal[txsAsc.size()];
        BigDecimal running = account.currentBalance != null ? account.currentBalance : BigDecimal.ZERO;
        for (int i = txsAsc.size() - 1; i >= 0; i--) {
            balances[i] = running;
            running = running.subtract(txsAsc.get(i).amount != null ? txsAsc.get(i).amount : BigDecimal.ZERO);
        }

        // Build ML transaction list in Bridge v2 TransactionItem format
        List<Map<String, Object>> mlTxs = new ArrayList<>();
        for (int i = 0; i < txsAsc.size(); i++) {
            TransactionEntity tx = txsAsc.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", tx.transactionDate.toString());
            item.put("amount", tx.amount != null ? tx.amount.doubleValue() : 0.0);
            item.put("balance", balances[i].doubleValue());
            item.put("description", tx.label != null ? tx.label : "");
            item.put("creditor_name", tx.creditorDebtor);
            item.put("category", tx.category);
            mlTxs.add(item);
        }

        LOG.infof("[Predict] userId=%s accountId=%s txCount=%d currentBalance=%.2f",
            userId, account.id, mlTxs.size(),
            account.currentBalance != null ? account.currentBalance.doubleValue() : 0.0);

        Map<String, Object> mlResult = mlClient.predict(account.id, mlTxs, 90);
        if (mlResult == null) {
            return Response.status(503).entity(Map.of("error",
                "Service IA indisponible. Demarrez le ML service et reessayez.")).build();
        }

        return Response.ok(toFrontendFormat(account.id, mlResult)).build();
    }

    @GET
    @Path("/latest")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public Response latest(@QueryParam("accountId") String accountId) {
        return generate();
    }

    @GET
    @Path("/history")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public Response history() {
        return Response.ok(List.of()).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toFrontendFormat(String accountId, Map<String, Object> ml) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", UUID.randomUUID().toString());
        r.put("status", "READY");
        r.put("accountId", accountId);
        r.put("horizonDays", ml.getOrDefault("horizon_days", 90));

        double score = toDouble(ml.get("confidence_score"), 0.6);
        r.put("confidenceScore", score);
        r.put("confidenceLabel", score >= 0.75 ? "Fiable" : score >= 0.45 ? "Indicatif" : "Estimation");
        r.put("estimatedErrorEur", toDouble(ml.get("mae_estimate"), 0.0));
        r.put("minPredictedBalance", toDouble(ml.get("min_balance"), 0.0));
        r.put("minPredictedDate", Objects.toString(ml.get("min_balance_date"), ""));
        r.put("deficitPredicted", Boolean.TRUE.equals(ml.get("predicted_deficit")));
        r.put("deficitAmount", ml.get("deficit_amount"));
        r.put("deficitDate", ml.get("deficit_date"));

        // daily_balance → [{date, balance, p25, p75}]
        List<Map<String, Object>> dailyData = new ArrayList<>();
        Object daily = ml.get("daily_balance");
        if (daily instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> day = new LinkedHashMap<>();
                    day.put("date", m.get("date"));
                    day.put("balance", toDouble(m.get("balance"), 0.0));
                    day.put("p25", toDouble(m.containsKey("balance_p25") ? m.get("balance_p25") : m.get("balance"), 0.0));
                    day.put("p75", toDouble(m.containsKey("balance_p75") ? m.get("balance_p75") : m.get("balance"), 0.0));
                    dailyData.add(day);
                }
            }
        }
        r.put("dailyData", dailyData);

        // critical_points → [{date, amount, type, label}]
        List<Map<String, Object>> critPoints = new ArrayList<>();
        Object critical = ml.get("critical_points");
        if (critical instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> pt = new LinkedHashMap<>();
                    pt.put("date", m.get("date"));
                    pt.put("amount", toDouble(m.get("predicted_balance"), 0.0));
                    pt.put("type", Objects.toString(m.get("severity"), "INFO"));
                    pt.put("label", Objects.toString(m.get("cause"), "Point critique"));
                    critPoints.add(pt);
                }
            }
        }
        r.put("criticalPoints", critPoints);
        r.put("createdAt", Instant.now().toString());
        return r;
    }

    private double toDouble(Object val, double fallback) {
        if (val == null) return fallback;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return fallback; }
    }
}
