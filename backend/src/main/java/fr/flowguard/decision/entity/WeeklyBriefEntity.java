package fr.flowguard.decision.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "weekly_briefs")
public class WeeklyBriefEntity extends PanacheEntityBase {

    @Id public String id;

    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "snapshot_id") public String snapshotId;
    @Column(name = "brief_text", nullable = false) public String briefText;
    @Column(name = "risk_level", nullable = false) public String riskLevel;
    @Column(name = "runway_days") public Integer runwayDays;
    @Column(name = "generated_at", nullable = false) public Instant generatedAt;
    @Column(name = "generation_mode", nullable = false) public String generationMode = "CRON";

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (generatedAt == null) generatedAt = Instant.now();
    }

    public static Optional<WeeklyBriefEntity> findLatestByUser(String userId) {
        return find("userId = ?1 ORDER BY generatedAt DESC", userId).firstResultOptional();
    }
}