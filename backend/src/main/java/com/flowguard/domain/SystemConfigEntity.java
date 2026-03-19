package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_config")
public class SystemConfigEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, unique = true, length = 100)
    public String configKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String configValue;

    @Column(length = 20, nullable = false)
    public String valueType = "STRING"; // STRING, BOOLEAN, INTEGER, DECIMAL

    public String description;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    public Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void setUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
