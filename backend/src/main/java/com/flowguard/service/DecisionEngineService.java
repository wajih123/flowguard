package com.flowguard.service;

import com.flowguard.domain.*;
import com.flowguard.dto.*;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.InvoiceRepository;
import com.flowguard.repository.TaxEstimateRepository;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DecisionEngineService {

    private static final Logger LOG = Logger.getLogger(DecisionEngineService.class);
    private static final int CACHE_MINUTES = 10;
    private static final int RUNWAY_CAP = 180;
    private static final BigDecimal MONTHLY_THRESHOLD = new BigDecimal("500");

    @Inject AccountRepository accountRepository;
    @Inject InvoiceRepository invoiceRepository;
    @Inject TaxEstimateRepository taxEstimateRepository;
    @Inject TransactionRepository transactionRepository;
    @Inject EntityManager em;

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional
    public DecisionSummaryDto getSummary(UUID userId) {
        // Check cache: most recent snapshot younger than CACHE_MINUTES
        CashRiskSnapshotEntity cached = latestSnapshot(userId);
        if (cached != null) {
            long ageMinutes = (Instant.now().toEpochMilli() - cached.getComputedAt().toEpochMilli()) / 60_000;
            if (ageMinutes < CACHE_MINUTES) {
                return toSummaryDto(cached, userId);
            }
        }
        return computeAndPersist(userId);
    }

    @Transactional
    public DecisionSummaryDto refresh(UUID userId) {
        return computeAndPersist(userId);
    }

    // ── Drivers / Actions ─────────────────────────────────────────────────────

    public List<CashDriverDto> getDrivers(UUID userId) {
        CashRiskSnapshotEntity snap = latestSnapshot(userId);
        if (snap == null) return List.of();
        return em.createQuery(
                        "FROM CashDriverEntity d WHERE d.snapshotId = :sid ORDER BY d.rank ASC",
                        CashDriverEntity.class)
                .setParameter("sid", snap.getId())
                .getResultList()
                .stream().map(this::toDriverDto).toList();
    }

    public List<CashActionDto> getActions(UUID userId) {
        CashRiskSnapshotEntity snap = latestSnapshot(userId);
        if (snap == null) return List.of();
        return em.createQuery(
                        "FROM CashRecommendationEntity r WHERE r.snapshotId = :sid ORDER BY r.confidence DESC",
                        CashRecommendationEntity.class)
                .setParameter("sid", snap.getId())
                .getResultList()
                .stream().map(this::toActionDto).toList();
    }

    // ── Simulate ──────────────────────────────────────────────────────────────

    public SimulateResultDto simulate(UUID userId, SimulateRequestDto req) {
        BigDecimal currentBalance = sumActiveBalances(userId);
        int baseRunway = computeRunway(userId, currentBalance);

        BigDecimal projected;
        int projectedRunway;
        String explanation;

        switch (req.scenarioType() == null ? "" : req.scenarioType()) {
            case "HIRE_EMPLOYEE" -> {
                double monthlyCost = req.amount() != null ? req.amount() : 3500.0;
                double annualCost = monthlyCost * 13; // includes charges ~45%
                projected = currentBalance.subtract(BigDecimal.valueOf(annualCost / 12));
                projectedRunway = computeRunwayFromBalance(userId, projected);
                explanation = String.format(
                        "Embaucher un salarié à %.0f€/mois représente ~%.0f€/mois de charges totales. " +
                        "Votre trésorerie passerait de %.0f€ à %.0f€ dès le 1er mois.",
                        monthlyCost, annualCost / 12, currentBalance, projected);
            }
            case "REVENUE_DROP" -> {
                double pct = req.percentage() != null ? req.percentage() : 20.0;
                BigDecimal avgMonthlyIncome = avgMonthlyIncome(userId);
                BigDecimal lost = avgMonthlyIncome.multiply(BigDecimal.valueOf(pct / 100));
                projected = currentBalance.subtract(lost);
                projectedRunway = computeRunwayFromBalance(userId, projected);
                explanation = String.format(
                        "Une baisse de CA de %.0f%% entraîne une perte estimée à %.0f€/mois. " +
                        "Votre trésorerie passerait de %.0f€ à %.0f€.",
                        pct, lost, currentBalance, projected);
            }
            case "PAYMENT_DELAY" -> {
                double amount = req.amount() != null ? req.amount() : 1000.0;
                int days = req.daysDelay() != null ? req.daysDelay() : 30;
                // Delaying a supplier payment frees up cash now
                projected = currentBalance.add(BigDecimal.valueOf(amount));
                projectedRunway = Math.min(RUNWAY_CAP, baseRunway + (int)(days * 0.8));
                explanation = String.format(
                        "Reporter le paiement de %.0f€ de %d jours vous donne un peu d'air. " +
                        "Votre solde serait temporairement de %.0f€ au lieu de %.0f€.",
                        amount, days, projected, currentBalance);
            }
            default -> {
                projected = currentBalance;
                projectedRunway = baseRunway;
                explanation = "Scénario non reconnu — aucun impact calculé.";
            }
        }

        BigDecimal delta = projected.subtract(currentBalance);
        return new SimulateResultDto(
                req.scenarioType(),
                currentBalance,
                projected,
                delta,
                baseRunway,
                projectedRunway,
                explanation
        );
    }

    // ── Action lifecycle ──────────────────────────────────────────────────────

    @Transactional
    public CashActionDto applyAction(String actionId, UUID userId) {
        CashRecommendationEntity rec = findRecommendation(actionId, userId);
        rec.setStatus("APPLIED");
        rec.setAppliedAt(Instant.now());
        em.merge(rec);
        return toActionDto(rec);
    }

    @Transactional
    public CashActionDto dismissAction(String actionId, UUID userId) {
        CashRecommendationEntity rec = findRecommendation(actionId, userId);
        rec.setStatus("DISMISSED");
        em.merge(rec);
        return toActionDto(rec);
    }

    // ── Weekly Brief ──────────────────────────────────────────────────────────

    public WeeklyBriefDto getLatestBrief(UUID userId) {
        List<WeeklyBriefEntity> briefs = em.createQuery(
                        "FROM WeeklyBriefEntity b WHERE b.userId = :uid ORDER BY b.generatedAt DESC",
                        WeeklyBriefEntity.class)
                .setParameter("uid", userId.toString())
                .setMaxResults(1)
                .getResultList();
        return briefs.isEmpty() ? null : toBriefDto(briefs.get(0));
    }

    @Transactional
    public WeeklyBriefDto generateBrief(UUID userId) {
        CashRiskSnapshotEntity snap = latestSnapshot(userId);
        if (snap == null) {
            computeAndPersist(userId);
            snap = latestSnapshot(userId);
        }

        String riskLevel = snap != null ? snap.getRiskLevel() : "LOW";
        int runway = snap != null && snap.getRunwayDays() != null ? snap.getRunwayDays() : RUNWAY_CAP;
        BigDecimal balance = snap != null && snap.getCurrentBalance() != null
                ? snap.getCurrentBalance() : BigDecimal.ZERO;

        String text = buildBriefText(riskLevel, runway, balance, userId);

        WeeklyBriefEntity brief = WeeklyBriefEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId.toString())
                .snapshotId(snap != null ? snap.getId() : null)
                .briefText(text)
                .riskLevel(riskLevel)
                .runwayDays(runway)
                .generatedAt(Instant.now())
                .generationMode("ON_DEMAND")
                .build();
        em.persist(brief);
        return toBriefDto(brief);
    }

    // ── Core compute ──────────────────────────────────────────────────────────

    private DecisionSummaryDto computeAndPersist(UUID userId) {
        BigDecimal currentBalance = sumActiveBalances(userId);
        int runwayDays = computeRunway(userId, currentBalance);
        String riskLevel = toRiskLevel(runwayDays, currentBalance);

        // Worst projected balance over 30-day horizon
        BigDecimal monthlyBurn = avgMonthlyBurn(userId);
        BigDecimal minBalance = currentBalance.add(monthlyBurn); // burn is negative
        LocalDate minBalanceDate = LocalDate.now().plusDays(30);
        boolean deficit = minBalance.compareTo(BigDecimal.ZERO) < 0;

        // Volatility: stddev of last 3 months income, normalised
        double volatility = computeVolatility(userId);

        String snapshotId = UUID.randomUUID().toString();
        CashRiskSnapshotEntity snap = CashRiskSnapshotEntity.builder()
                .id(snapshotId)
                .userId(userId.toString())
                .computedAt(Instant.now())
                .riskLevel(riskLevel)
                .runwayDays(runwayDays)
                .currentBalance(currentBalance)
                .minBalance(minBalance)
                .minBalanceDate(deficit ? minBalanceDate : null)
                .volatilityScore(BigDecimal.valueOf(volatility))
                .deficitPredicted(deficit)
                .build();
        em.persist(snap);

        List<CashDriverDto> drivers = buildAndPersistDrivers(userId, snapshotId);
        List<CashActionDto> actions = buildAndPersistActions(userId, snapshotId, riskLevel, drivers);

        return new DecisionSummaryDto(
                snapshotId,
                snap.getComputedAt().toString(),
                riskLevel,
                runwayDays,
                currentBalance,
                minBalance,
                deficit ? minBalanceDate : null,
                deficit,
                volatility,
                drivers,
                actions
        );
    }

    private List<CashDriverDto> buildAndPersistDrivers(UUID userId, String snapshotId) {
        List<CashDriverEntity> entities = new ArrayList<>();
        short rank = 1;

        // 1. Upcoming tax deadlines (next 60 days)
        List<TaxEstimateEntity> taxes = taxEstimateRepository.findUpcoming(
                userId, LocalDate.now(), LocalDate.now().plusDays(60));
        for (TaxEstimateEntity t : taxes) {
            CashDriverEntity d = CashDriverEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .snapshotId(snapshotId)
                    .userId(userId.toString())
                    .driverType("TAX_PAYMENT")
                    .label(t.getTaxType().name() + " — " + t.getPeriodLabel())
                    .amount(t.getEstimatedAmount())
                    .impactDays((int) (t.getDueDate().toEpochDay() - LocalDate.now().toEpochDay()))
                    .dueDate(t.getDueDate())
                    .referenceId(t.getId().toString())
                    .referenceType("TAX")
                    .rank(rank++)
                    .build();
            em.persist(d);
            entities.add(d);
        }

        // 2. Overdue / late invoices (money not yet received)
        List<InvoiceEntity> overdueInvoices = invoiceRepository.findByUserIdAndStatus(
                userId, InvoiceEntity.InvoiceStatus.OVERDUE);
        for (InvoiceEntity inv : overdueInvoices) {
            CashDriverEntity d = CashDriverEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .snapshotId(snapshotId)
                    .userId(userId.toString())
                    .driverType("LATE_INVOICE")
                    .label("Facture impayée — " + inv.getClientName())
                    .amount(inv.getTotalTtc())
                    .impactDays(0)
                    .dueDate(inv.getDueDate())
                    .referenceId(inv.getId().toString())
                    .referenceType("INVOICE")
                    .rank(rank++)
                    .build();
            em.persist(d);
            entities.add(d);
        }

        return entities.stream().map(this::toDriverDto).toList();
    }

    private List<CashActionDto> buildAndPersistActions(
            UUID userId, String snapshotId, String riskLevel, List<CashDriverDto> drivers) {

        List<CashRecommendationEntity> entities = new ArrayList<>();

        boolean hasLateInvoices = drivers.stream().anyMatch(d -> "LATE_INVOICE".equals(d.type()));
        boolean hasTaxDeadline = drivers.stream().anyMatch(d -> "TAX_PAYMENT".equals(d.type()));
        boolean isCritical = "CRITICAL".equals(riskLevel) || "HIGH".equals(riskLevel);
        BigDecimal avgBurn = avgMonthlyBurn(userId).abs();

        if (hasLateInvoices) {
            BigDecimal lateTotal = drivers.stream()
                    .filter(d -> "LATE_INVOICE".equals(d.type()))
                    .map(d -> d.amount() != null ? d.amount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            entities.add(buildRecommendation(snapshotId, userId, "SEND_REMINDERS",
                    "Relancez vos clients : " + invoiceRepository.findByUserIdAndStatus(
                            userId, InvoiceEntity.InvoiceStatus.OVERDUE).size()
                            + " facture(s) en retard pour " + fmt(lateTotal) + "€ impayé(s).",
                    lateTotal, 14, 0.88));
        }

        if (isCritical) {
            entities.add(buildRecommendation(snapshotId, userId, "REQUEST_CREDIT",
                    "Votre trésorerie est sous pression. Activez une réserve FlowGuard pour sécuriser votre activité.",
                    avgBurn.multiply(BigDecimal.valueOf(2)), 7, 0.82));
        }

        if (hasTaxDeadline && isCritical) {
            entities.add(buildRecommendation(snapshotId, userId, "DELAY_SUPPLIER",
                    "Décalez certains paiements fournisseurs non urgents pour faire face aux échéances fiscales.",
                    avgBurn.multiply(BigDecimal.valueOf(0.3)), 30, 0.71));
        }

        if (isCritical || "MEDIUM".equals(riskLevel)) {
            entities.add(buildRecommendation(snapshotId, userId, "REDUCE_SPEND",
                    "Passez en revue vos charges récurrentes et identifiez 10-20% d'économies potentielles.",
                    avgBurn.multiply(BigDecimal.valueOf(0.15)), 60, 0.65));
        }

        if (hasLateInvoices) {
            entities.add(buildRecommendation(snapshotId, userId, "ACCELERATE_RECEIVABLES",
                    "Proposez un escompte de 2% pour paiement immédiat sur les factures en retard.",
                    drivers.stream().filter(d -> "LATE_INVOICE".equals(d.type()))
                            .map(d -> d.amount() != null ? d.amount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .multiply(BigDecimal.valueOf(0.95)),
                    7, 0.60));
        }

        entities.forEach(em::persist);
        return entities.stream().map(this::toActionDto).toList();
    }

    private CashRecommendationEntity buildRecommendation(
            String snapshotId, UUID userId, String type, String description,
            BigDecimal impact, int horizonDays, double confidence) {
        return CashRecommendationEntity.builder()
                .id(UUID.randomUUID().toString())
                .snapshotId(snapshotId)
                .userId(userId.toString())
                .actionType(type)
                .description(description)
                .estimatedImpact(impact.setScale(2, RoundingMode.HALF_UP))
                .horizonDays(horizonDays)
                .confidence(BigDecimal.valueOf(confidence))
                .build();
    }

    // ── Financial helpers ─────────────────────────────────────────────────────

    private BigDecimal sumActiveBalances(UUID userId) {
        try {
            return accountRepository.findActiveByUserId(userId).stream()
                    .map(AccountEntity::getBalance)
                    .filter(b -> b != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            LOG.warnf("Could not sum balances for user %s: %s", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal avgMonthlyBurn(UUID userId) {
        // Negative = net outflow per month
        BigDecimal monthly = BigDecimal.ZERO;
        try {
            var accounts = accountRepository.findActiveByUserId(userId);
            for (var acc : accounts) {
                LocalDate since = LocalDate.now().minusMonths(3);
                var txs = transactionRepository.findByAccountIdAndDateBetween(
                        acc.getId(), since, LocalDate.now());
                BigDecimal sum = txs.stream()
                        .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                // divide by 3 months
                monthly = monthly.add(sum.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP));
            }
        } catch (Exception e) {
            LOG.warnf("Could not compute burn for user %s: %s", userId, e.getMessage());
        }
        return monthly;
    }

    private BigDecimal avgMonthlyIncome(UUID userId) {
        BigDecimal monthly = BigDecimal.ZERO;
        try {
            var accounts = accountRepository.findActiveByUserId(userId);
            for (var acc : accounts) {
                LocalDate since = LocalDate.now().minusMonths(3);
                var txs = transactionRepository.findByAccountIdAndDateBetween(
                        acc.getId(), since, LocalDate.now());
                BigDecimal income = txs.stream()
                        .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                        .filter(a -> a.compareTo(BigDecimal.ZERO) > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                monthly = monthly.add(income.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP));
            }
        } catch (Exception e) {
            LOG.warnf("Could not compute income for user %s: %s", userId, e.getMessage());
        }
        return monthly;
    }

    private int computeRunway(UUID userId, BigDecimal balance) {
        return computeRunwayFromBalance(userId, balance);
    }

    private int computeRunwayFromBalance(UUID userId, BigDecimal balance) {
        BigDecimal burn = avgMonthlyBurn(userId);
        if (burn.compareTo(BigDecimal.ZERO) >= 0) return RUNWAY_CAP; // net positive
        if (balance.compareTo(BigDecimal.ZERO) <= 0) return 0;
        // months of runway
        double months = balance.divide(burn.abs(), 4, RoundingMode.HALF_UP).doubleValue();
        int days = (int) Math.round(months * 30);
        return Math.min(RUNWAY_CAP, Math.max(0, days));
    }

    private double computeVolatility(UUID userId) {
        try {
            var accounts = accountRepository.findActiveByUserId(userId);
            if (accounts.isEmpty()) return 0.0;
            var acc = accounts.get(0);
            LocalDate since = LocalDate.now().minusMonths(3);
            var txs = transactionRepository.findByAccountIdAndDateBetween(
                    acc.getId(), since, LocalDate.now());
            if (txs.size() < 2) return 0.0;
            double mean = txs.stream()
                    .mapToDouble(t -> t.getAmount() != null ? t.getAmount().doubleValue() : 0)
                    .average().orElse(0);
            if (mean == 0) return 0.0;
            double variance = txs.stream()
                    .mapToDouble(t -> {
                        double a = t.getAmount() != null ? t.getAmount().doubleValue() : 0;
                        return Math.pow(a - mean, 2);
                    })
                    .average().orElse(0);
            return Math.min(1.0, Math.sqrt(variance) / Math.abs(mean));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String toRiskLevel(int runwayDays, BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) < 0 || runwayDays < 15) return "CRITICAL";
        if (runwayDays < 30) return "HIGH";
        if (runwayDays < 60) return "MEDIUM";
        return "LOW";
    }

    // ── Brief text generation ──────────────────────────────────────────────────

    private String buildBriefText(String riskLevel, int runway, BigDecimal balance, UUID userId) {
        String balanceFmt = String.format("%.0f€", balance);
        String runwayFmt = runway >= RUNWAY_CAP ? "plus de 6 mois" : runway + " jours";

        String intro = switch (riskLevel) {
            case "CRITICAL" -> "⚠️ Situation critique. ";
            case "HIGH"     -> "📉 Situation tendue. ";
            case "MEDIUM"   -> "📊 Situation à surveiller. ";
            default         -> "✅ Situation saine. ";
        };

        String lines = intro
                + "Votre solde actuel est de " + balanceFmt + ". "
                + "Avec le rythme de dépenses actuel, votre trésorerie vous permet de tenir " + runwayFmt + ".\n\n";

        List<TaxEstimateEntity> taxes = taxEstimateRepository.findUpcoming(
                userId, LocalDate.now(), LocalDate.now().plusDays(30));
        if (!taxes.isEmpty()) {
            lines += "📅 Échéances fiscales dans les 30 prochains jours : "
                    + taxes.size() + " paiement(s) à prévoir.\n";
        }

        List<InvoiceEntity> overdue = invoiceRepository.findByUserIdAndStatus(
                userId, InvoiceEntity.InvoiceStatus.OVERDUE);
        if (!overdue.isEmpty()) {
            lines += "📨 " + overdue.size() + " facture(s) impayée(s) en retard — pensez à relancer vos clients.\n";
        }

        lines += "\nConsultez les recommandations ci-dessous pour des actions concrètes.";
        return lines;
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private CashRiskSnapshotEntity latestSnapshot(UUID userId) {
        List<CashRiskSnapshotEntity> list = em.createQuery(
                        "FROM CashRiskSnapshotEntity s WHERE s.userId = :uid ORDER BY s.computedAt DESC",
                        CashRiskSnapshotEntity.class)
                .setParameter("uid", userId.toString())
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    private CashRecommendationEntity findRecommendation(String actionId, UUID userId) {
        return em.createQuery(
                        "FROM CashRecommendationEntity r WHERE r.id = :id AND r.userId = :uid",
                        CashRecommendationEntity.class)
                .setParameter("id", actionId)
                .setParameter("uid", userId.toString())
                .getSingleResult();
    }

    // ── DTO mappers ───────────────────────────────────────────────────────────

    private DecisionSummaryDto toSummaryDto(CashRiskSnapshotEntity snap, UUID userId) {
        List<CashDriverDto> drivers = em.createQuery(
                        "FROM CashDriverEntity d WHERE d.snapshotId = :sid ORDER BY d.rank ASC",
                        CashDriverEntity.class)
                .setParameter("sid", snap.getId())
                .getResultList()
                .stream().map(this::toDriverDto).toList();

        List<CashActionDto> actions = em.createQuery(
                        "FROM CashRecommendationEntity r WHERE r.snapshotId = :sid AND r.status = 'PENDING' ORDER BY r.confidence DESC",
                        CashRecommendationEntity.class)
                .setParameter("sid", snap.getId())
                .getResultList()
                .stream().map(this::toActionDto).toList();

        return new DecisionSummaryDto(
                snap.getId(),
                snap.getComputedAt().toString(),
                snap.getRiskLevel(),
                snap.getRunwayDays() != null ? snap.getRunwayDays() : RUNWAY_CAP,
                snap.getCurrentBalance() != null ? snap.getCurrentBalance() : BigDecimal.ZERO,
                snap.getMinBalance() != null ? snap.getMinBalance() : BigDecimal.ZERO,
                snap.getMinBalanceDate(),
                snap.isDeficitPredicted(),
                snap.getVolatilityScore() != null ? snap.getVolatilityScore().doubleValue() : 0.0,
                drivers,
                actions
        );
    }

    private CashDriverDto toDriverDto(CashDriverEntity e) {
        return new CashDriverDto(
                e.getId(), e.getDriverType(), e.getLabel(),
                e.getAmount(), e.getImpactDays() != null ? e.getImpactDays() : 0,
                e.getDueDate(), e.getReferenceId(), e.getReferenceType(),
                (int) e.getRank());
    }

    private CashActionDto toActionDto(CashRecommendationEntity e) {
        return new CashActionDto(
                e.getId(), e.getActionType(), e.getDescription(),
                e.getEstimatedImpact() != null ? e.getEstimatedImpact() : BigDecimal.ZERO,
                e.getHorizonDays() != null ? e.getHorizonDays() : 30,
                e.getConfidence() != null ? e.getConfidence().doubleValue() : 0.0,
                e.getStatus());
    }

    private WeeklyBriefDto toBriefDto(WeeklyBriefEntity e) {
        return new WeeklyBriefDto(
                e.getId(), e.getBriefText(), e.getRiskLevel(),
                e.getRunwayDays() != null ? e.getRunwayDays() : 0,
                e.getGeneratedAt().toString(), e.getGenerationMode());
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
