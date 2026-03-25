package fr.flowguard.bnpl.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity @Table(name = "bnpl_installments")
public class BnplInstallmentEntity extends PanacheEntityBase {
    @Id public String id;
    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "merchant_label") public String merchantLabel;
    @Column(name = "provider") public String provider = "ONEY";
    @Column(name = "original_amount") public BigDecimal originalAmount;
    @Column(name = "installment_amount") public BigDecimal installmentAmount;
    @Column(name = "installments_total") public short installmentsTotal = 3;
    @Column(name = "installments_paid") public short installmentsPaid = 0;
    @Column(name = "first_debit_date") public LocalDate firstDebitDate;
    @Column(name = "next_debit_date") public LocalDate nextDebitDate;
    @Column(name = "completed_at") public Instant completedAt;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;

    @PrePersist void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static List<BnplInstallmentEntity> findActiveByUser(String userId) {
        return list("userId = ?1 AND completedAt IS NULL ORDER BY nextDebitDate ASC", userId);
    }

    public BigDecimal remainingAmount() {
        if (installmentAmount == null) return BigDecimal.ZERO;
        return installmentAmount.multiply(BigDecimal.valueOf(installmentsTotal - installmentsPaid));
    }
}
