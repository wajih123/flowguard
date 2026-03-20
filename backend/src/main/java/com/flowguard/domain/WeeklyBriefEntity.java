package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "weekly_briefs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyBriefEntity extends PanacheEntityBase {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "snapshot_id", length = 36)
    private String snapshotId;

    @Column(name = "brief_text", nullable = false, columnDefinition = "TEXT")
    private String briefText;

    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel;

    @Column(name = "runway_days")
    private Integer runwayDays;

    @Column(name = "generated_at", nullable = false)
    @Builder.Default
    private Instant generatedAt = Instant.now();

    @Column(name = "generation_mode", nullable = false, length = 10)
    @Builder.Default
    private String generationMode = "ON_DEMAND";
}
