package com.flowguard.repository;

import com.flowguard.domain.PaymentInitiationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PaymentInitiationRepository implements PanacheRepositoryBase<PaymentInitiationEntity, UUID> {

    public List<PaymentInitiationEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY initiatedAt DESC", userId);
    }

    public Optional<PaymentInitiationEntity> findByIdempotencyKey(String key) {
        return find("idempotencyKey = ?1", key).firstResultOptional();
    }

    public Optional<PaymentInitiationEntity> findBySwanPaymentId(String swanPaymentId) {
        return find("swanPaymentId = ?1", swanPaymentId).firstResultOptional();
    }
}
