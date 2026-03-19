package fr.flowguard.banking.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "bank_accounts")
public class BankAccountEntity extends PanacheEntityBase {

    @Id @Column(name = "id") public String id;
    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "bank_name", nullable = false) public String bankName;
    @Column(name = "iban_masked") public String ibanMasked;
    @Column(name = "current_balance", nullable = false) public BigDecimal currentBalance = BigDecimal.ZERO;
    @Column(name = "currency", nullable = false) public String currency = "EUR";
    @Column(name = "nordigen_account_id") public String nordigenAccountId;
    @Column(name = "nordigen_requisition_id") public String nordigenRequisitionId;
    @Column(name = "last_sync_at") public Instant lastSyncAt;
    @Column(name = "sync_status", nullable = false) public String syncStatus = "PENDING";
    @Column(name = "is_active", nullable = false) public boolean isActive = true;
    @Column(name = "bridge_account_id") public String bridgeAccountId;
    @Column(name = "bridge_item_id") public String bridgeItemId;
    @Column(name = "bridge_user_uuid") public String bridgeUserUuid;
    @Column(name = "account_type") public String accountType = "CHECKING";
    @Column(name = "account_name") public String accountName;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;

    @PrePersist void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public static List<BankAccountEntity> findActiveByUserId(String userId) {
        return list("userId = ?1 AND isActive = true", userId);
    }
    public static BankAccountEntity findByBridgeAccountId(String bridgeAccountId) {
        return find("bridgeAccountId", bridgeAccountId).firstResult();
    }
}