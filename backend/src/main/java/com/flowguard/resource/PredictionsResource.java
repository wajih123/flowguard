package com.flowguard.resource;

import com.flowguard.dto.TreasuryForecastDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.service.TreasuryService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * GET /api/predictions/latest?accountId={uuid}
 *
 * Adapts the internal TreasuryForecastDto into the Prediction shape
 * consumed by the frontend ForecastPage / usePredictions hook.
 *
 * Shape returned:
 * {
 *   id, status, horizonDays, confidenceScore, confidenceLabel,
 *   estimatedErrorEur, minPredictedBalance, minPredictedDate,
 *   deficitPredicted, deficitAmount, deficitDate,
 *   dailyData: [{date, balance, p25, p75}],
 *   criticalPoints: [{date, amount, type, label}]
 * }
 */
@Path("/predictions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class PredictionsResource {

    @Inject TreasuryService treasuryService;
    @Inject AccountRepository accountRepository;
    @Inject JsonWebToken jwt;

    @GET
    @Path("/latest")
    @RunOnVirtualThread
    public Response getLatest(
            @QueryParam("accountId") UUID accountId,
            @QueryParam("horizonDays") @DefaultValue("90") int horizonDays) {

        UUID userId = UUID.fromString(jwt.getSubject());

        TreasuryForecastDto forecast = treasuryService.getCachedForecast(userId, horizonDays);

        if (forecast == null || forecast.predictions() == null || forecast.predictions().isEmpty()) {
            return Response.ok(emptyPrediction(accountId, horizonDays)).build();
        }

        // ── Daily data ──────────────────────────────────────────────────────
        List<Map<String, Object>> dailyData = forecast.predictions().stream().map(p -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", p.date().toString());
            point.put("balance", p.predictedBalance());
            // Derive p25/p75 from bounds; fall back to ±5% of predicted if not available
            BigDecimal lower = p.lowerBound() != null ? p.lowerBound()
                    : p.predictedBalance().multiply(new BigDecimal("0.95"));
            BigDecimal upper = p.upperBound() != null ? p.upperBound()
                    : p.predictedBalance().multiply(new BigDecimal("1.05"));
            point.put("p25", lower.setScale(2, RoundingMode.HALF_UP));
            point.put("p75", upper.setScale(2, RoundingMode.HALF_UP));
            return point;
        }).toList();

        // ── Min predicted balance ────────────────────────────────────────────
        Optional<TreasuryForecastDto.ForecastPoint> minPoint = forecast.predictions().stream()
                .min(Comparator.comparing(TreasuryForecastDto.ForecastPoint::predictedBalance));
        BigDecimal minBalance = minPoint.map(TreasuryForecastDto.ForecastPoint::predictedBalance)
                .orElse(BigDecimal.ZERO);
        String minDate = minPoint.map(p -> p.date().toString()).orElse(LocalDate.now().toString());

        boolean deficitPredicted = minBalance.compareTo(BigDecimal.ZERO) < 0;

        // ── Critical points ──────────────────────────────────────────────────
        List<Map<String, Object>> criticalPoints = new ArrayList<>();
        if (forecast.criticalPoints() != null) {
            for (TreasuryForecastDto.CriticalPoint cp : forecast.criticalPoints()) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", cp.date().toString());
                point.put("amount", cp.predictedBalance());
                point.put("type", cp.predictedBalance().compareTo(BigDecimal.ZERO) < 0 ? "DEFICIT" : "CRITICAL");
                point.put("label", cp.reason() != null ? cp.reason() : "Point critique");
                criticalPoints.add(point);
            }
        }
        if (criticalPoints.isEmpty() && deficitPredicted) {
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("date", minDate);
            pt.put("amount", minBalance);
            pt.put("type", "DEFICIT");
            pt.put("label", "Solde prévu négatif");
            criticalPoints.add(pt);
        }

        // ── Confidence label ─────────────────────────────────────────────────
        double cs = forecast.confidenceScore();
        String confidenceLabel = cs >= 0.75 ? "Fiable" : cs >= 0.50 ? "Indicatif" : "Estimation";

        // ── Estimated error ──────────────────────────────────────────────────
        BigDecimal estimatedError = forecast.predictions().stream()
                .filter(p -> p.upperBound() != null && p.lowerBound() != null)
                .findFirst()
                .map(p -> p.upperBound().subtract(p.lowerBound())
                        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);

        // ── Build response ───────────────────────────────────────────────────
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", accountId != null ? accountId.toString() : userId.toString());
        body.put("status", "READY");
        body.put("horizonDays", horizonDays);
        body.put("confidenceScore", cs);
        body.put("confidenceLabel", confidenceLabel);
        body.put("estimatedErrorEur", estimatedError);
        body.put("minPredictedBalance", minBalance);
        body.put("minPredictedDate", minDate);
        body.put("deficitPredicted", deficitPredicted);
        body.put("deficitAmount", deficitPredicted ? minBalance : null);
        body.put("deficitDate", deficitPredicted ? minDate : null);
        body.put("dailyData", dailyData);
        body.put("criticalPoints", criticalPoints);

        return Response.ok(body).build();
    }

    private static Map<String, Object> emptyPrediction(UUID accountId, int horizonDays) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", accountId != null ? accountId.toString() : "");
        body.put("status", "PENDING");
        body.put("horizonDays", horizonDays);
        body.put("confidenceScore", 0.0);
        body.put("confidenceLabel", "Estimation");
        body.put("estimatedErrorEur", 0);
        body.put("minPredictedBalance", 0);
        body.put("minPredictedDate", LocalDate.now().toString());
        body.put("deficitPredicted", false);
        body.put("deficitAmount", null);
        body.put("deficitDate", null);
        body.put("dailyData", List.of());
        body.put("criticalPoints", List.of());
        return body;
    }
}
