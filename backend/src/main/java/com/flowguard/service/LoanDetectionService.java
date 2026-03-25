package com.flowguard.service;

import com.flowguard.domain.LoanAmortizationEntity;
import com.flowguard.domain.TransactionEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.UUID;

@ApplicationScoped
public class LoanDetectionService {

    private static final List<String> LOAN_KEYWORDS = Arrays.asList(
        "LOAN", "CREDIT", "EMPRUNT", "PRET", "MORTGAGE", "HYPOTHEQUE", "PAYMENT", "REPAYMENT",
        "PRËT", "FINANCEMENT", "REMBOURSEMENT", "MENSUALITÉ"
    );

    /**
     * Detects loan-like transactions from user's account
     */
    public List<LoanAmortizationEntity> detectLoanTransactions(String userId) {
        List<LoanAmortizationEntity> detected = new ArrayList<>();

        PanacheQuery<TransactionEntity> query = TransactionEntity.find(
            "account.user.id = ?1 ORDER BY createdAt DESC",
            UUID.fromString(userId)
        );

        Map<String, LoanAmortizationEntity> loanMap = new HashMap<>();

        for (TransactionEntity tx : query.list()) {
            String merchant = tx.getLabel();
            String description = tx.getLabel();
            String combined = (merchant + " " + description).toUpperCase();

            for (String keyword : LOAN_KEYWORDS) {
                if (combined.contains(keyword)) {
                    String key = merchant.hashCode() + "|" + keyword;

                    loanMap.computeIfAbsent(key, k -> {
                        LoanAmortizationEntity entity = new LoanAmortizationEntity();
                        entity.setUser(tx.getAccount().getUser());
                        entity.setLoanName(merchant);
                        entity.setMonthlyPayment(tx.getAmount());
                        entity.setStatus("active");
                        entity.setStartDate(LocalDate.now().minusYears(1));
                        entity.setMaturityDate(LocalDate.now().plusYears(2));
                        entity.setNextPaymentDate(LocalDate.now().plusDays(30));
                        return entity;
                    });

                    break;
                }
            }
        }

        detected.addAll(loanMap.values());
        return detected;
    }

    /**
     * Calculates loan amortization schedule
     */
    public static class AmortizationSchedule {
        public List<PaymentSchedule> schedule;
        public BigDecimal totalInterestPaid;
        public BigDecimal totalAmountPaid;
    }

    public static class PaymentSchedule {
        public Integer paymentNumber;
        public BigDecimal payment;
        public BigDecimal principal;
        public BigDecimal interest;
        public BigDecimal balance;
        public LocalDate paymentDate;
    }

    public AmortizationSchedule calculateAmortization(
            BigDecimal principal,
            Double annualInterestRate,
            Integer monthlyPayments) {

        AmortizationSchedule schedule = new AmortizationSchedule();
        schedule.schedule = new ArrayList<>();

        double monthlyRate = annualInterestRate / 100.0 / 12.0;
        BigDecimal monthlyPayment = calculateMonthlyPayment(principal, monthlyRate, monthlyPayments);

        BigDecimal remainingBalance = principal;
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (int i = 1; i <= monthlyPayments; i++) {
            PaymentSchedule ps = new PaymentSchedule();
            ps.paymentNumber = i;
            ps.paymentDate = LocalDate.now().plusMonths(i);

            BigDecimal interestPayment = remainingBalance.multiply(BigDecimal.valueOf(monthlyRate));
            BigDecimal principalPayment = monthlyPayment.subtract(interestPayment);

            if (i == monthlyPayments) {
                // Last payment may differ due to rounding
                principalPayment = remainingBalance;
            }

            ps.interest = interestPayment;
            ps.principal = principalPayment;
            ps.payment = monthlyPayment;
            remainingBalance = remainingBalance.subtract(principalPayment);
            ps.balance = remainingBalance.max(BigDecimal.ZERO);

            totalInterest = totalInterest.add(interestPayment);
            schedule.schedule.add(ps);
        }

        schedule.totalInterestPaid = totalInterest;
        schedule.totalAmountPaid = principal.add(totalInterest);

        return schedule;
    }

    private static BigDecimal calculateMonthlyPayment(
            BigDecimal principal,
            Double monthlyRate,
            Integer months) {

        if (monthlyRate == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, java.math.RoundingMode.HALF_UP);
        }

        double monthlyRateD = monthlyRate;
        double numerator = monthlyRateD * Math.pow(1 + monthlyRateD, months);
        double denominator = Math.pow(1 + monthlyRateD, months) - 1;
        double monthlyPaymentD = principal.doubleValue() * (numerator / denominator);

        return BigDecimal.valueOf(monthlyPaymentD).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
