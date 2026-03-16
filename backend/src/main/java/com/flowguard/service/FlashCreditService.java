package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.FlashCreditEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.FlashCreditDto;
import com.flowguard.dto.FlashCreditRequest;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.FlashCreditRepository;
import com.flowguard.repository.TransactionRepository;
import com.flowguard.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FlashCreditService {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.015");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("500");
    private static final int MAX_ACTIVE_CREDITS = 3;
    private static final int REPAYMENT_DAYS = 30;
    private static final int RETRACTION_DAYS = 14;
    /** Max debt-to-income ratio (33% per French banking standards) */
    private static final BigDecimal MAX_DEBT_RATIO = new BigDecimal("0.33");

    @Inject
    FlashCreditRepository flashCreditRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    CreditScoringService creditScoringService;

    public List<FlashCreditDto> getCreditsByUserId(UUID userId) {
        return flashCreditRepository.findByUserId(userId).stream()
                .map(FlashCreditDto::from)
                .toList();
    }

    @Transactional
    public FlashCreditDto requestCredit(UUID userId, FlashCreditRequest request) {
        return requestCredit(userId, request, null);
    }

    @Transactional
    public FlashCreditDto requestCredit(UUID userId, FlashCreditRequest request, String idempotencyKey) {
        // Idempotency check — return existing result if key was already processed
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = flashCreditRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return FlashCreditDto.from(existing.get());
            }
        }
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("Utilisateur introuvable");
        }

        if (user.getKycStatus() != UserEntity.KycStatus.APPROVED) {
            throw new IllegalStateException("KYC non validé. Veuillez compléter la vérification d'identité.");
        }

        if (request.amount().compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("Montant minimum : " + MIN_AMOUNT + " €");
        }

        if (request.amount().compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Montant maximum : " + MAX_AMOUNT + " €");
        }

        long activeCount = flashCreditRepository.countActiveByUserId(userId);
        if (activeCount >= MAX_ACTIVE_CREDITS) {
            throw new IllegalStateException("Limite de crédits actifs atteinte (" + MAX_ACTIVE_CREDITS + ")");
        }

        // --- Solvability check (art. L312-16 Code de la consommation) ---
        verifySolvability(userId, request.amount());

        // --- Credit scoring ---
        creditScoringService.assertEligible(userId);

        BigDecimal fee = request.amount().multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRepayment = request.amount().add(fee);

        // --- TAEG calculation (art. L314-1 Code de la consommation) ---
        // TAEG = ((totalRepayment / amount) ^ (365/days) - 1) * 100
        BigDecimal taegPercent = calculateTAEG(request.amount(), totalRepayment, REPAYMENT_DAYS);

        Instant now = Instant.now();

        FlashCreditEntity credit = FlashCreditEntity.builder()
                .user(user)
                .amount(request.amount())
                .fee(fee)
                .totalRepayment(totalRepayment)
                .taegPercent(taegPercent)
                .purpose(request.purpose())
                .status(FlashCreditEntity.CreditStatus.APPROVED)
                .disbursedAt(now)
                .dueDate(now.plus(REPAYMENT_DAYS, ChronoUnit.DAYS))
                .retractionDeadline(now.plus(RETRACTION_DAYS, ChronoUnit.DAYS))
                .idempotencyKey(idempotencyKey)
                .build();

        flashCreditRepository.persist(credit);

        credit.setStatus(FlashCreditEntity.CreditStatus.DISBURSED);

        return FlashCreditDto.from(credit);
    }

    /**
     * Exercice du droit de rétractation (directive 2008/48/CE — 14 jours calendaires).
     */
    @Transactional
    public FlashCreditDto exerciseRetraction(UUID userId, UUID creditId) {
        FlashCreditEntity credit = flashCreditRepository.findById(creditId);
        if (credit == null || !credit.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Crédit introuvable");
        }

        if (credit.isRetractionExercised()) {
            throw new IllegalStateException("Le droit de rétractation a déjà été exercé");
        }

        if (credit.getRetractionDeadline() != null && Instant.now().isAfter(credit.getRetractionDeadline())) {
            throw new IllegalStateException("Le délai de rétractation de 14 jours est expiré");
        }

        credit.setRetractionExercised(true);
        credit.setStatus(FlashCreditEntity.CreditStatus.RETRACTED);
        credit.setRepaidAt(Instant.now());

        return FlashCreditDto.from(credit);
    }

    /**
     * Repayment of a flash credit.
     */
    @Transactional
    public FlashCreditDto repayCredit(UUID userId, UUID creditId) {
        FlashCreditEntity credit = flashCreditRepository.findById(creditId);
        if (credit == null || !credit.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Crédit introuvable");
        }

        if (credit.getStatus() != FlashCreditEntity.CreditStatus.DISBURSED
                && credit.getStatus() != FlashCreditEntity.CreditStatus.OVERDUE) {
            throw new IllegalStateException("Ce crédit ne peut pas être remboursé (statut : " + credit.getStatus() + ")");
        }

        credit.setStatus(FlashCreditEntity.CreditStatus.REPAID);
        credit.setRepaidAt(Instant.now());

        return FlashCreditDto.from(credit);
    }

    /**
     * Calcul du TAEG — Taux Annuel Effectif Global (art. L314-1).
     * Formule actuarielle simplifiée pour un prêt à remboursement unique.
     */
    BigDecimal calculateTAEG(BigDecimal principal, BigDecimal totalRepayment, int durationDays) {
        // TAEG = ((totalRepayment / principal) ^ (365 / durationDays) - 1) * 100
        double ratio = totalRepayment.doubleValue() / principal.doubleValue();
        double annualized = Math.pow(ratio, 365.0 / durationDays) - 1.0;
        return BigDecimal.valueOf(annualized * 100).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Vérification de solvabilité (art. L312-16 Code de la consommation).
     * Calcule le ratio d'endettement : total des mensualités / revenus mensuels.
     * Le ratio ne doit pas dépasser 33%.
     */
    void verifySolvability(UUID userId, BigDecimal requestedAmount) {
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) {
            throw new IllegalStateException("Aucun compte bancaire connecté. Impossible de vérifier la solvabilité.");
        }

        // Calculate monthly income from last 90 days of CREDIT transactions
        LocalDate ninetyDaysAgo = LocalDate.now().minusDays(90);
        BigDecimal totalIncome = BigDecimal.ZERO;
        for (AccountEntity account : accounts) {
            List<TransactionEntity> credits = transactionRepository
                    .findByAccountIdAndDateBetween(account.getId(), ninetyDaysAgo, LocalDate.now())
                    .stream()
                    .filter(t -> t.getType() == TransactionEntity.TransactionType.CREDIT)
                    .toList();
            for (TransactionEntity t : credits) {
                totalIncome = totalIncome.add(t.getAmount());
            }
        }

        BigDecimal monthlyIncome = totalIncome.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);

        // If no income detected, block
        if (monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Revenus insuffisants pour accorder un crédit.");
        }

        // Existing monthly debt = sum of active credits / repayment period
        BigDecimal existingMonthlyDebt = BigDecimal.ZERO;
        List<FlashCreditEntity> activeCredits = flashCreditRepository.findActiveByUserId(userId);
        for (FlashCreditEntity c : activeCredits) {
            existingMonthlyDebt = existingMonthlyDebt.add(c.getTotalRepayment());
        }

        // New monthly charge
        BigDecimal newFee = requestedAmount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newTotalRepayment = requestedAmount.add(newFee);
        BigDecimal totalMonthlyDebt = existingMonthlyDebt.add(newTotalRepayment);

        // Debt ratio check
        BigDecimal debtRatio = totalMonthlyDebt.divide(monthlyIncome, 4, RoundingMode.HALF_UP);

        if (debtRatio.compareTo(MAX_DEBT_RATIO) > 0) {
            BigDecimal ratioPercent = debtRatio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
            throw new IllegalStateException(
                    "Taux d'endettement trop élevé (" + ratioPercent + "%). "
                    + "Le maximum autorisé est de 33%. "
                    + "Réduisez le montant demandé ou remboursez un crédit existant."
            );
        }
    }
}
