package fr.flowguard.forecast.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "forecast_accuracy")
public class ForecastAccuracyEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "account_id")
    public String accountId;

    @Column(name = "forecast_date", nullable = false)
    public LocalDate forecastDate;

    @Column(name = "horizon_days", nullable = false)
    public int horizonDays;

    @Column(name = "predicted_balance", nullable = false)
    public BigDecimal predictedBalance;

    @Column(name = "actual_balance")
    public BigDecimal actualBalance;

    @Column(name = "mae")
    public BigDecimal mae;

    @Column(name = "accuracy_pct")
    public BigDecimal accuracyPct;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    public Instant recordedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (recordedAt == null) recordedAt = Instant.now();
    }

    public static List<ForecastAccuracyEntity> findByUser(String userId) {
        return list("userId = ?1 ORDER BY forecastDate DESC", userId);
    }

    public static List<ForecastAccuracyEntity> findByUserAndHorizon(String userId, int horizonDays) {
        return list("userId = ?1 AND horizonDays = ?2 ORDER BY forecastDate DESC", userId, horizonDays);
    }
}