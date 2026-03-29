package com.flowguard.repository;

import com.flowguard.domain.AlertEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    /**
     * Dedup guard: returns true if an alert of the given type was already created
     * for this user today (UTC), preventing duplicate spending alerts per day.
     */
    public boolean existsByUserTypeAndCreatedToday(UUID userId, AlertEntity.AlertType type) {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = startOfDay.plusSeconds(86400);
        return count("user.id = ?1 AND type = ?2 AND createdAt >= ?3 AND createdAt < ?4",
                userId, type, startOfDay, endOfDay) > 0;
    }
}
