package com.flowguard.service;

import com.flowguard.domain.BnplInstallmentEntity;
import com.flowguard.domain.TransactionEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.UUID;

@ApplicationScoped
public class BnplDetectionService {

    private static final List<String> BNPL_KEYWORDS = Arrays.asList(
        "KLARNA", "SCALAPAY", "AFFIRM", "AFTERPAY", "CLEARPAY", "SEZZLE", "LAYBUY",
        "BNPL", "PAY IN 4", "PAY IN 3", "INSTALLMENT", "LAYAWAY"
    );

    /**
     * Detects BNPL transactions from user's account
     */
    public List<BnplInstallmentEntity> detectBnplTransactions(String userId) {
        List<BnplInstallmentEntity> detected = new ArrayList<>();

        PanacheQuery<TransactionEntity> query = TransactionEntity.find(
            "account.user.id = ?1 ORDER BY createdAt DESC",
            UUID.fromString(userId)
        );

        Map<String, BnplInstallmentEntity> bnplMap = new HashMap<>();

        for (TransactionEntity tx : query.list()) {
            String merchantName = tx.getLabel();
            String description = tx.getLabel();

            String key = detectBnplProvider(merchantName, description);
            if (key != null) {
                bnplMap.computeIfAbsent(key, k -> {
                    BnplInstallmentEntity entity = new BnplInstallmentEntity();
                    entity.setUser(tx.getAccount().getUser());
                    entity.setMerchantName(merchantName);
                    entity.setProvider(extractProvider(merchantName, description));
                    entity.setStatus("active");
                    return entity;
                });

                BnplInstallmentEntity entity = bnplMap.get(key);
                entity.setPaidAmount(entity.getPaidAmount() != null ? 
                    entity.getPaidAmount().add(tx.getAmount()) : tx.getAmount());
            }
        }

        detected.addAll(bnplMap.values());
        return detected;
    }

    /**
     * Detects if a transaction is BNPL-related
     */
    private String detectBnplProvider(String merchant, String description) {
        String combined = (merchant + " " + description).toUpperCase();

        for (String keyword : BNPL_KEYWORDS) {
            if (combined.contains(keyword)) {
                return merchant + "|" + keyword;
            }
        }

        // Pattern matching for "Pay in X" format
        if (Pattern.compile("PAY\\s+IN\\s+\\d").matcher(combined).find()) {
            return merchant + "|PAY_IN_X";
        }

        return null;
    }

    private String extractProvider(String merchant, String description) {
        String combined = (merchant + " " + description).toUpperCase();

        for (String keyword : BNPL_KEYWORDS) {
            if (combined.contains(keyword)) {
                return keyword.toLowerCase();
            }
        }

        return "unknown";
    }

    /**
     * Analyzes BNPL payment obligations
     */
    public static class BnplAnalysis {
        public Integer activeInstallmentPlans;
        public BigDecimal totalOutstanding;
        public BigDecimal nextPaymentAmount;
        public LocalDate nextPaymentDate;
        public Double percentageOfMonthlyIncome;
    }

    public BnplAnalysis analyzeBnplCommitments(String userId, double monthlyIncome) {
        BnplAnalysis analysis = new BnplAnalysis();
        analysis.activeInstallmentPlans = 0;
        analysis.totalOutstanding = BigDecimal.ZERO;

        List<BnplInstallmentEntity> active = BnplInstallmentEntity.find(
            "user.id = ?1 AND status = 'active' ORDER BY nextPaymentDate ASC",
            UUID.fromString(userId)
        ).list();

        for (BnplInstallmentEntity plan : active) {
            analysis.activeInstallmentPlans++;
            analysis.totalOutstanding = analysis.totalOutstanding.add(plan.getNextPaymentAmount());

            if (analysis.nextPaymentDate == null || 
                plan.getNextPaymentDate().isBefore(analysis.nextPaymentDate)) {
                analysis.nextPaymentDate = plan.getNextPaymentDate();
                analysis.nextPaymentAmount = plan.getNextPaymentAmount();
            }
        }

        if (monthlyIncome > 0) {
            analysis.percentageOfMonthlyIncome = 
                (analysis.totalOutstanding.doubleValue() / monthlyIncome) * 100;
        } else {
            analysis.percentageOfMonthlyIncome = 0.0;
        }

        return analysis;
    }
}
