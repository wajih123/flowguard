package com.flowguard.repository;

import com.flowguard.domain.AlertEntity;
import com.flowguard.domain.AlertThresholdEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AlertThresholdRepository implements PanacheRepositoryBase<AlertThresholdEntity, UUID> {

    public List<AlertThresholdEntity> findByUserId(UUID userId) {
        return list("user.id", userId);
    }

    public Optional<AlertThresholdEntity> findByUserIdAndType(UUID userId, AlertEntity.AlertType alertType) {
        return find("user.id = ?1 AND alertType = ?2", userId, alertType).firstResultOptional();
    }
}
