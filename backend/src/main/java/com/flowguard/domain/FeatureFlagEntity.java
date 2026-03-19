package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_flags")
public class FeatureFlagEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, unique = true)
    public String flagKey;

    @Column(nullable = false)
    public Boolean enabled = true;

    public String description;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    public Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void setUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
