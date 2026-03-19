package fr.flowguard.banking.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "transactions")
public class TransactionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "account_id", nullable = false)
    public String accountId;

    @Column(name = "external_id")
    public String externalId;

    @Column(name = "amount", nullable = false)
    public BigDecimal amount;

    @Column(name = "currency", nullable = false)
    public String currency = "EUR";

    @Column(name = "label", nullable = false)
    public String label = "";

    @Column(name = "creditor_debtor")
    public String creditorDebtor;

    @Column(name = "category")
    public String category;

    @Column(name = "transaction_date", nullable = false)
    public LocalDate transactionDate;

    @Column(name = "booking_date")
    public LocalDate bookingDate;

    @Column(name = "is_recurring", nullable = false)
    public boolean isRecurring = false;

    @Column(name = "balance_after")
    public BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static TransactionEntity findByExternalId(String externalId) {
        return find("externalId", externalId).firstResult();
    }

    public static List<TransactionEntity> findByAccountIdOrderedAsc(String accountId) {
        return list("accountId = ?1 ORDER BY transactionDate ASC", accountId);
    }

    public static List<TransactionEntity> findByAccountIdOrderedDesc(String accountId) {
        return list("accountId = ?1 ORDER BY transactionDate DESC", accountId);
    }
}
