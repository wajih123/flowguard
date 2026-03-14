package com.flowguard.repository;

import com.flowguard.domain.AlertEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AlertRepository implements PanacheRepositoryBase<AlertEntity, UUID> {

    public List<AlertEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY createdAt DESC", userId);
    }

    public List<AlertEntity> findUnreadByUserId(UUID userId) {
        return list("user.id = ?1 AND isRead = false ORDER BY createdAt DESC", userId);
    }

    public long countUnreadByUserId(UUID userId) {
        return count("user.id = ?1 AND isRead = false", userId);
    }

    public int markAsRead(UUID alertId, UUID userId) {
        return update("isRead = true WHERE id = ?1 AND user.id = ?2", alertId, userId);
    }

    public int markAllAsReadByUserId(UUID userId) {
        return update("isRead = true WHERE user.id = ?1 AND isRead = false", userId);
    }
}
