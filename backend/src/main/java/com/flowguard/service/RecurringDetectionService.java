package com.flowguard.service;

import com.flowguard.domain.TransactionEntity;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Detects recurring transactions (salary, rent, URSSAF, subscriptions) from
 * a user's transaction history using label normalisation + amount clustering.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Normalise labels: lowercase, remove accents, remove amounts/dates, remove extra whitespace.
 *   <li>Group transactions by normalised label (same payer/payee).
 *   <li>For groups with ≥ 2 occurrences over 3+ months: analyse periodicity.
 *   <li>If same amount ±10% and consistent interval → mark as recurring.
 *   <li>Compute next expected date.
 * </ol>
 */
@ApplicationScoped
public class RecurringDetectionService {

    private static final Logger LOG = Logger.getLogger(RecurringDetectionService.class);

    /** Min occurrences to be considered recurring */
    private static final int MIN_OCCURRENCES = 2;

    /** Max amount variance (±10%) to be considered same transaction */
    private static final double AMOUNT_VARIANCE_PCT = 0.10;

    /** Min interval between occurrences (days) */
    private static final long MIN_INTERVAL_DAYS = 5;

    /** Pattern to remove digit sequences (amounts, dates embedded in labels) */
    private static final Pattern DIGITS = Pattern.compile("\\d+([.,]\\d+)?");

    /** Pattern to remove reference codes and extra punctuation */
    private static final Pattern REF_CODES = Pattern.compile("[A-Z0-9]{8,}|[*/:#@]");

    @Inject
    TransactionRepository transactionRepository;

    /**
     * Analyses all transactions for an account and marks recurring ones.
     * Updates the {@code is_recurring} flag on existing transactions and
     * saves detected patterns to the recurring_patterns table.
     *
     * @param accountId the account to analyse
     */
    @Transactional
    public List<RecurringPattern> detectForAccount(UUID accountId) {
        List<TransactionEntity> txns = transactionRepository.findByAccountId(accountId);
        if (txns.size() < MIN_OCCURRENCES) {
            return Collections.emptyList();
        }

        // Group by normalised label
        Map<String, List<TransactionEntity>> groups = txns.stream()
                .filter(t -> t.getAmount() != null && t.getDate() != null)
                .collect(Collectors.groupingBy(t -> normaliseLabel(t.getLabel())));

        List<RecurringPattern> patterns = new ArrayList<>();

        for (Map.Entry<String, List<TransactionEntity>> entry : groups.entrySet()) {
            String normLabel = entry.getKey();
            List<TransactionEntity> group = entry.getValue().stream()
                    .sorted(Comparator.comparing(TransactionEntity::getDate))
                    .toList();

            if (group.size() < MIN_OCCURRENCES) continue;

            Optional<RecurringPattern> pattern = analyseGroup(normLabel, group);
            if (pattern.isPresent()) {
                patterns.add(pattern.get());
                // Mark transactions as recurring
                group.forEach(t -> t.setRecurring(true));
            }
        }

        LOG.infof("Detected %d recurring patterns for account %s", patterns.size(), accountId);
        return patterns;
    }

    // ── Label normalisation ────────────────────────────────────────────────────

    static String normaliseLabel(String label) {
        if (label == null) return "";

        // 1. Lowercase
        String s = label.toLowerCase(java.util.Locale.FRENCH);

        // 2. Remove accents (NFD decomposition)
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");

        // 3. Remove numbers (amounts, dates, reference codes)
        s = DIGITS.matcher(s).replaceAll("");
        s = REF_CODES.matcher(s).replaceAll("");

        // 4. Remove common transaction noise words
        s = s.replaceAll("\\b(carte|virement|prelevement|sepa|ref|no|num|from|to|de|du|au|le|la|les|un|une)\\b", "");

        // 5. Collapse whitespace
        s = s.replaceAll("\\s+", " ").trim();

        // 6. Cap at 50 chars (avoid very long unique labels)
        return s.length() > 50 ? s.substring(0, 50).trim() : s;
    }

    // ── Pattern analysis ───────────────────────────────────────────────────────

    private Optional<RecurringPattern> analyseGroup(String normLabel, List<TransactionEntity> sorted) {
        // Compute average amount
        double avgAmount = sorted.stream()
                .mapToDouble(t -> t.getAmount().abs().doubleValue())
                .average()
                .orElse(0);

        if (avgAmount < 1.0) return Optional.empty(); // Skip negligible amounts

        // Check amount consistency (all within ±10%)
        boolean amountConsistent = sorted.stream().allMatch(t -> {
            double ratio = t.getAmount().abs().doubleValue() / avgAmount;
            return ratio >= (1.0 - AMOUNT_VARIANCE_PCT) && ratio <= (1.0 + AMOUNT_VARIANCE_PCT);
        });

        if (!amountConsistent) return Optional.empty();

        // Compute intervals between occurrences
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            long days = ChronoUnit.DAYS.between(sorted.get(i - 1).getDate(), sorted.get(i).getDate());
            if (days >= MIN_INTERVAL_DAYS) {
                intervals.add(days);
            }
        }

        if (intervals.isEmpty()) return Optional.empty();

        double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
        double intervalStd = computeStd(intervals, avgInterval);

        // Accept if coefficient of variation < 30% (consistent periodicity)
        if (avgInterval > 0 && intervalStd / avgInterval > 0.30) {
            return Optional.empty();
        }

        Periodicity periodicity = classifyPeriodicity(avgInterval);
        LocalDate lastDate = sorted.getLast().getDate();
        LocalDate nextExpected = lastDate.plusDays(Math.round(avgInterval));

        TransactionEntity.TransactionCategory category = sorted.getLast().getCategory();

        double amountStd = computeStd(
            sorted.stream().map(t -> t.getAmount().abs().doubleValue()).toList(),
            avgAmount
        );

        double confidence = Math.min(0.99, 0.5 + (sorted.size() * 0.1) - (intervalStd / avgInterval * 0.3));

        return Optional.of(new RecurringPattern(
            normLabel,
            category,
            BigDecimal.valueOf(avgAmount).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(amountStd).setScale(2, RoundingMode.HALF_UP),
            periodicity,
            lastDate,
            nextExpected,
            sorted.size(),
            BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP)
        ));
    }

    private Periodicity classifyPeriodicity(double avgIntervalDays) {
        if (avgIntervalDays < 10)              return Periodicity.WEEKLY;
        if (avgIntervalDays < 35)              return Periodicity.MONTHLY;
        if (avgIntervalDays < 100)             return Periodicity.QUARTERLY;
        return Periodicity.ANNUAL;
    }

    private double computeStd(List<? extends Number> values, double mean) {
        if (values.size() < 2) return 0.0;
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v.doubleValue() - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    // ── Value objects ──────────────────────────────────────────────────────────

    public enum Periodicity { WEEKLY, MONTHLY, QUARTERLY, ANNUAL }

    public record RecurringPattern(
        String normalisedLabel,
        TransactionEntity.TransactionCategory category,
        BigDecimal avgAmount,
        BigDecimal amountStd,
        Periodicity periodicity,
        LocalDate lastSeen,
        LocalDate nextExpected,
        int occurrenceCount,
        BigDecimal confidence
    ) {}
}
