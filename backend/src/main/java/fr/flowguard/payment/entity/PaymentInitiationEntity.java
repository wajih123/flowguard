package fr.flowguard.payment.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "payment_initiations", indexes = {
    @Index(name = "idx_payment_user", columnList = "user_id"),
    @Index(name = "idx_payment_idempotency", columnList = "idempotency_key", unique = true)
})
public class PaymentInitiationEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "creditor_name", nullable = false)
    public String creditorName;

    @Column(name = "creditor_iban", nullable = false)
    public String creditorIban;

    @Column(nullable = false, precision = 18, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false, length = 3)
    public String currency = "EUR";

    @Column(nullable = false)
    public String reference;

    @Column(nullable = false, length = 20)
    public String status = "PENDING";

    @Column(name = "idempotency_key", unique = true, nullable = false)
    public String idempotencyKey;

    @Column(name = "swan_payment_id")
    public String swanPaymentId;

    @Column(name = "initiated_at")
    public Instant initiatedAt;

    @Column(name = "executed_at")
    public Instant executedAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        initiatedAt = Instant.now();
        updatedAt = initiatedAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static List<PaymentInitiationEntity> findByUser(String userId) {
        return find("userId = ?1 ORDER BY initiatedAt DESC", userId).list();
    }

    public static PaymentInitiationEntity findByIdAndUser(String id, String userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResult();
    }

    public static PaymentInitiationEntity findByIdempotencyKeyAndUser(String key, String userId) {
        return find("idempotencyKey = ?1 AND userId = ?2", key, userId).firstResult();
    }
}