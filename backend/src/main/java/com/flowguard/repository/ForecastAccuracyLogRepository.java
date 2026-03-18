package com.flowguard.repository;

import com.flowguard.domain.ForecastAccuracyLogEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ForecastAccuracyLogRepository implements PanacheRepositoryBase<ForecastAccuracyLogEntity, UUID> {

    public List<ForecastAccuracyLogEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY forecastDate DESC", userId);
    }

    public List<ForecastAccuracyLogEntity> findByUserIdAndHorizon(UUID userId, int horizonDays) {
        return list("user.id = ?1 AND horizonDays = ?2 ORDER BY forecastDate DESC", userId, horizonDays);
    }

    /** Find the log entry to be reconciled when actual balance becomes known. */
    public Optional<ForecastAccuracyLogEntity> findPendingReconciliation(UUID userId, LocalDate forecastDate, int horizonDays) {
        return find("user.id = ?1 AND forecastDate = ?2 AND horizonDays = ?3 AND actualBalance IS NULL",
                userId, forecastDate, horizonDays).firstResultOptional();
    }
}
