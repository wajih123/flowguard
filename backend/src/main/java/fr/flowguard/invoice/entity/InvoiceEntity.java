package fr.flowguard.invoice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "invoices")
public class InvoiceEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "client_name", nullable = false)
    public String clientName;

    @Column(name = "client_email")
    public String clientEmail;

    @Column(name = "number", nullable = false)
    public String number;

    @Column(name = "amount_ht", nullable = false)
    public BigDecimal amountHt;

    @Column(name = "vat_rate", nullable = false)
    public BigDecimal vatRate = new BigDecimal("0.20");

    @Column(name = "vat_amount", nullable = false)
    public BigDecimal vatAmount;

    @Column(name = "total_ttc", nullable = false)
    public BigDecimal totalTtc;

    @Column(name = "currency", nullable = false)
    public String currency = "EUR";

    @Column(name = "status", nullable = false)
    public String status = "DRAFT";

    @Column(name = "issue_date", nullable = false)
    public LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    public LocalDate dueDate;

    @Column(name = "paid_at")
    public Instant paidAt;

    @Column(name = "notes")
    public String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public static List<InvoiceEntity> findByUser(String userId) {
        return list("userId = ?1 ORDER BY issueDate DESC", userId);
    }

    public static Optional<InvoiceEntity> findByIdAndUser(String id, String userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResultOptional();
    }
}