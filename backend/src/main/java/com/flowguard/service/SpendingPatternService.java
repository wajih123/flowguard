package com.flowguard.service;

import com.flowguard.domain.AlertEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.SpendingPatternDto;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyses user spending patterns to detect:
 * 1. Daily spending spikes vs the 90-day rolling average
 * 2. Weekend overspending vs historic weekend average
 * 3. Hidden subscriptions — recurring charges not tagged as ABONNEMENT or isRecurring
 * 4. Subscription price increases — same merchant, amount crept up ≥10%
 * 5. Duplicate subscriptions — two distinct merchants with near-identical recurring amounts
 *
 * Does NOT inject AlertService — returns AlertCandidate records to avoid circular dependency.
 */
@ApplicationScoped
public class SpendingPatternService {

    @Inject
    TransactionRepository transactionRepository;

    @ConfigProperty(name = "flowguard.spending.daily-multiplier", defaultValue = "2.0")
    double dailyMultiplier;

    @ConfigProperty(name = "flowguard.spending.weekend-multiplier", defaultValue = "1.5")
    double weekendMultiplier;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Called by the AlertService scheduler — returns candidates without persisting.
     * AlertService is responsible for dedup + creation.
     */
    public List<AlertCandidate> generateAlerts(UserEntity user) {
        PatternAnalysis pa = analyze(user.getId());
        List<AlertCandidate> candidates = new ArrayList<>();

        // 1. Daily spending spike
        if (pa.todayTotal().compareTo(BigDecimal.ZERO) > 0
                && pa.dailyAverage().compareTo(BigDecimal.ZERO) > 0) {
            double ratio = pa.todayVsAvgRatio();
            if (ratio >= 3.0) {
                candidates.add(new AlertCandidate(
                        AlertEntity.AlertType.EXCESSIVE_SPEND,
                        AlertEntity.Severity.HIGH,
                        String.format(
                                "Dépenses excessives : %s dépensés aujourd'hui, soit %.1f× votre moyenne quotidienne (%s/j). Vérifiez vos achats.",
                                fmt(pa.todayTotal()), ratio, fmt(pa.dailyAverage()))));
            } else if (ratio >= dailyMultiplier) {
                candidates.add(new AlertCandidate(
                        AlertEntity.AlertType.EXCESSIVE_SPEND,
                        AlertEntity.Severity.MEDIUM,
                        String.format(
                                "Dépenses élevées aujourd'hui : %s, soit %.1f× votre habitude quotidienne (%s/j). Pensez à freiner.",
                                fmt(pa.todayTotal()), ratio, fmt(pa.dailyAverage()))));
            }
        }

        // 2. Weekend spike
        if (pa.weekendIsAnomaly()) {
            candidates.add(new AlertCandidate(
                    AlertEntity.AlertType.EXCESSIVE_SPEND,
                    AlertEntity.Severity.MEDIUM,
                    String.format(
                            "Weekend dépensier : %s ce weekend, soit +%.0f%% par rapport à vos weekends habituels. Les petites dépenses s'accumulent vite.",
                            fmt(pa.thisWeekendTotal()),
                            (pa.thisWeekendRatio() - 1) * 100)));
        }

        // 3. Hidden subscriptions
        for (SpendingPatternDto.HiddenSubscriptionDto sub : pa.hiddenSubscriptions()) {
            candidates.add(new AlertCandidate(
                    AlertEntity.AlertType.HIDDEN_SUBSCRIPTION,
                    AlertEntity.Severity.LOW,
                    String.format(
                            "Abonnement potentiellement caché : \"%s\" (~%s/mois depuis %d mois, non catégorisé dans Abonnements). Vérifiez si vous êtes toujours abonné.",
                            sub.label(), fmt(sub.monthlyAmount()), sub.monthsDetected())));
        }

        // 4. Subscription price increases
        List<TransactionEntity> allTx180 = transactionRepository.findByUserIdAndDateBetween(
                user.getId(), LocalDate.now().minusDays(180), LocalDate.now());
        List<TransactionEntity> debits180 = allTx180.stream()
                .filter(t -> t.getType() == TransactionEntity.TransactionType.DEBIT && !t.isInternal())
                .toList();
        candidates.addAll(detectPriceIncreases(debits180));

        // 5. Duplicate subscriptions
        candidates.addAll(detectDuplicateSubscriptions(debits180));

        return candidates;
    }

    /** Returns pattern data for the REST API endpoint. */
    public SpendingPatternDto computePatterns(UUID userId) {
        PatternAnalysis pa = analyze(userId);
        return new SpendingPatternDto(
                pa.dailyAverage(),
                pa.todayTotal(),
                pa.todayVsAvgRatio(),
                pa.weekdayDailyAverage(),
                pa.weekendDailyAverage(),
                pa.weekendVsWeekdayRatio(),
                pa.todayIsAnomaly(),
                pa.weekendIsAnomaly(),
                pa.hiddenSubscriptions());
    }

    // ── Core analysis ──────────────────────────────────────────────────────────

    private PatternAnalysis analyze(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(90);

        List<TransactionEntity> allTx = transactionRepository.findByUserIdAndDateBetween(userId, from, today);
        List<TransactionEntity> debits = allTx.stream()
                .filter(t -> t.getType() == TransactionEntity.TransactionType.DEBIT && !t.isInternal())
                .toList();

        BigDecimal dailyAvg = computeDailyAverage(debits, 90);
        BigDecimal todayTotal = sumForDate(debits, today);
        double todayRatio = dailyAvg.compareTo(BigDecimal.ZERO) > 0
                ? todayTotal.divide(dailyAvg, 4, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        boolean todayIsAnomaly = todayRatio >= dailyMultiplier && todayTotal.compareTo(BigDecimal.ZERO) > 0;

        // Aggregate by date for weekend/weekday comparison
        Map<LocalDate, BigDecimal> byDate = debits.stream()
                .collect(Collectors.groupingBy(
                        TransactionEntity::getDate,
                        Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)));

        DoubleSummaryStatistics weekendStats = byDate.entrySet().stream()
                .filter(e -> isWeekend(e.getKey()))
                .mapToDouble(e -> e.getValue().doubleValue())
                .summaryStatistics();

        DoubleSummaryStatistics weekdayStats = byDate.entrySet().stream()
                .filter(e -> !isWeekend(e.getKey()))
                .mapToDouble(e -> e.getValue().doubleValue())
                .summaryStatistics();

        BigDecimal weekdayDailyAvg = weekdayStats.getCount() > 0
                ? BigDecimal.valueOf(weekdayStats.getAverage()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal weekendDailyAvg = weekendStats.getCount() > 0
                ? BigDecimal.valueOf(weekendStats.getAverage()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        double weekendVsWeekdayRatio = weekdayDailyAvg.compareTo(BigDecimal.ZERO) > 0
                ? weekendDailyAvg.divide(weekdayDailyAvg, 4, RoundingMode.HALF_UP).doubleValue()
                : 1.0;

        // Current weekend anomaly check
        DayOfWeek dow = today.getDayOfWeek();
        BigDecimal thisWeekendTotal = BigDecimal.ZERO;
        double thisWeekendRatio = 0.0;
        boolean weekendIsAnomaly = false;

        if (isWeekend(today) && weekendDailyAvg.compareTo(BigDecimal.ZERO) > 0) {
            LocalDate saturday = (dow == DayOfWeek.SATURDAY) ? today : today.minusDays(1);
            BigDecimal thisSat = sumForDate(debits, saturday);
            BigDecimal thisSun = (dow == DayOfWeek.SUNDAY)
                    ? todayTotal
                    : sumForDate(debits, saturday.plusDays(1));
            thisWeekendTotal = thisSat.add(thisSun);
            int daysElapsed = (dow == DayOfWeek.SATURDAY) ? 1 : 2;
            BigDecimal dailyRate = thisWeekendTotal.divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
            thisWeekendRatio = dailyRate.divide(weekendDailyAvg, 4, RoundingMode.HALF_UP).doubleValue();
            weekendIsAnomaly = thisWeekendRatio >= weekendMultiplier
                    && thisWeekendTotal.compareTo(BigDecimal.ZERO) > 0;
        }

        List<SpendingPatternDto.HiddenSubscriptionDto> hidden = detectHiddenSubscriptions(debits);

        return new PatternAnalysis(
                dailyAvg, todayTotal, todayRatio, weekdayDailyAvg, weekendDailyAvg,
                weekendVsWeekdayRatio, todayIsAnomaly, weekendIsAnomaly,
                thisWeekendTotal, thisWeekendRatio, hidden);
    }

    private List<SpendingPatternDto.HiddenSubscriptionDto> detectHiddenSubscriptions(
            List<TransactionEntity> debits) {
        // Group by normalized label (first 20 chars), skip already-tagged recurring/abonnements
        Map<String, List<TransactionEntity>> byLabel = debits.stream()
                .filter(t -> !t.isRecurring())
                .filter(t -> t.getCategory() != TransactionEntity.TransactionCategory.ABONNEMENT)
                .collect(Collectors.groupingBy(t -> {
                    if (t.getLabel() == null) return "?";
                    String lbl = t.getLabel().toLowerCase().trim();
                    return lbl.substring(0, Math.min(20, lbl.length()));
                }));

        List<SpendingPatternDto.HiddenSubscriptionDto> result = new ArrayList<>();
        for (Map.Entry<String, List<TransactionEntity>> entry : byLabel.entrySet()) {
            List<TransactionEntity> group = entry.getValue();
            if (group.size() < 3) continue;

            // Must span at least 3 distinct calendar months
            long distinctMonths = group.stream()
                    .map(t -> YearMonth.from(t.getDate()))
                    .distinct().count();
            if (distinctMonths < 3) continue;

            // Amount must be consistent — coefficient of variation < 25%
            double[] amounts = group.stream()
                    .mapToDouble(t -> t.getAmount().abs().doubleValue())
                    .toArray();
            double mean = Arrays.stream(amounts).average().orElse(0.0);
            if (mean < 1.0) continue;
            double variance = Arrays.stream(amounts).map(a -> (a - mean) * (a - mean)).average().orElse(0.0);
            double cv = Math.sqrt(variance) / mean;
            if (cv > 0.25) continue;

            result.add(new SpendingPatternDto.HiddenSubscriptionDto(
                    capitalize(entry.getKey()),
                    BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP),
                    (int) distinctMonths));
        }
        return result;
    }

    // ── Hidden cost detectors ──────────────────────────────────────────────────

    /**
     * Detects subscription price increases: same merchant key appearing in both
     * the "old" period (months 4-6 ago) and the "recent" period (last 3 months)
     * with an average amount increase ≥ 10%.
     */
    private List<AlertCandidate> detectPriceIncreases(List<TransactionEntity> debits) {
        LocalDate cutoff = LocalDate.now().minusMonths(3);
        List<AlertCandidate> result = new ArrayList<>();

        Map<String, List<TransactionEntity>> byLabel = groupByMerchantKey(debits);
        for (Map.Entry<String, List<TransactionEntity>> entry : byLabel.entrySet()) {
            List<TransactionEntity> group = entry.getValue();
            if (group.size() < 4) continue;

            List<TransactionEntity> old = group.stream()
                    .filter(t -> t.getDate().isBefore(cutoff)).toList();
            List<TransactionEntity> recent = group.stream()
                    .filter(t -> !t.getDate().isBefore(cutoff)).toList();

            if (old.isEmpty() || recent.isEmpty()) continue;

            double oldAvg = old.stream()
                    .mapToDouble(t -> t.getAmount().abs().doubleValue()).average().orElse(0);
            double recentAvg = recent.stream()
                    .mapToDouble(t -> t.getAmount().abs().doubleValue()).average().orElse(0);

            if (oldAvg < 1.0) continue;
            double increaseRatio = (recentAvg - oldAvg) / oldAvg;
            if (increaseRatio < 0.10) continue;

            BigDecimal oldAmt = BigDecimal.valueOf(oldAvg).setScale(2, RoundingMode.HALF_UP);
            BigDecimal newAmt = BigDecimal.valueOf(recentAvg).setScale(2, RoundingMode.HALF_UP);
            result.add(new AlertCandidate(
                    AlertEntity.AlertType.SUBSCRIPTION_PRICE_INCREASE,
                    AlertEntity.Severity.MEDIUM,
                    String.format(
                            "Hausse de prix détectée : \"%s\" est passé de %s à %s/mois (+%.0f%%). Vérifiez votre contrat.",
                            capitalize(entry.getKey()), fmt(oldAmt), fmt(newAmt), increaseRatio * 100)));
        }
        return result;
    }

    /**
     * Detects duplicate subscriptions: two distinct merchant keys both recurring
     * monthly with amounts within 15% of each other (likely same service subscribed twice).
     */
    private List<AlertCandidate> detectDuplicateSubscriptions(List<TransactionEntity> debits) {
        // Only look at likely-recurring transactions (tagged recurring OR category ABONNEMENT)
        Map<String, Double> recurringAvgByKey = new LinkedHashMap<>();
        Map<String, List<TransactionEntity>> byLabel = groupByMerchantKey(debits);
        for (Map.Entry<String, List<TransactionEntity>> entry : byLabel.entrySet()) {
            List<TransactionEntity> group = entry.getValue();
            long distinctMonths = group.stream()
                    .map(t -> YearMonth.from(t.getDate())).distinct().count();
            if (distinctMonths < 2) continue;
            double avg = group.stream()
                    .mapToDouble(t -> t.getAmount().abs().doubleValue()).average().orElse(0);
            if (avg < 2.0) continue;
            recurringAvgByKey.put(entry.getKey(), avg);
        }

        List<AlertCandidate> result = new ArrayList<>();
        List<Map.Entry<String, Double>> recurring = new ArrayList<>(recurringAvgByKey.entrySet());
        Set<String> reported = new HashSet<>();

        for (int i = 0; i < recurring.size(); i++) {
            for (int j = i + 1; j < recurring.size(); j++) {
                String keyA = recurring.get(i).getKey();
                String keyB = recurring.get(j).getKey();
                if (reported.contains(keyA) || reported.contains(keyB)) continue;

                double avgA = recurring.get(i).getValue();
                double avgB = recurring.get(j).getValue();
                double diff = Math.abs(avgA - avgB) / Math.max(avgA, avgB);
                if (diff > 0.15) continue;
                // Must be different merchants (don't flag same merchant detected twice)
                if (keyA.startsWith(keyB.substring(0, Math.min(6, keyB.length())))
                        || keyB.startsWith(keyA.substring(0, Math.min(6, keyA.length())))) continue;

                reported.add(keyA);
                reported.add(keyB);
                result.add(new AlertCandidate(
                        AlertEntity.AlertType.DUPLICATE_SUBSCRIPTION,
                        AlertEntity.Severity.MEDIUM,
                        String.format(
                                "Doublon possible : \"%s\" (~%s/mois) et \"%s\" (~%s/mois) semblent être le même service. Vérifiez si les deux sont nécessaires.",
                                capitalize(keyA), fmt(BigDecimal.valueOf(avgA).setScale(2, RoundingMode.HALF_UP)),
                                capitalize(keyB), fmt(BigDecimal.valueOf(avgB).setScale(2, RoundingMode.HALF_UP)))));
            }
        }
        return result;
    }

    private Map<String, List<TransactionEntity>> groupByMerchantKey(List<TransactionEntity> debits) {
        return debits.stream()
                .filter(t -> t.getLabel() != null)
                .collect(Collectors.groupingBy(t -> {
                    String lbl = t.getLabel().toLowerCase().trim();
                    return lbl.substring(0, Math.min(20, lbl.length()));
                }));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private BigDecimal computeDailyAverage(List<TransactionEntity> debits, int days) {
        BigDecimal total = debits.stream()
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumForDate(List<TransactionEntity> debits, LocalDate date) {
        return debits.stream()
                .filter(t -> t.getDate().equals(date))
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString() + " €";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Internal types ─────────────────────────────────────────────────────────

    private record PatternAnalysis(
            BigDecimal dailyAverage,
            BigDecimal todayTotal,
            double todayVsAvgRatio,
            BigDecimal weekdayDailyAverage,
            BigDecimal weekendDailyAverage,
            double weekendVsWeekdayRatio,
            boolean todayIsAnomaly,
            boolean weekendIsAnomaly,
            BigDecimal thisWeekendTotal,
            double thisWeekendRatio,
            List<SpendingPatternDto.HiddenSubscriptionDto> hiddenSubscriptions) {}

    /** Returned to AlertService so it can apply dedup + create alerts. */
    public record AlertCandidate(
            AlertEntity.AlertType type,
            AlertEntity.Severity severity,
            String message) {}
}
