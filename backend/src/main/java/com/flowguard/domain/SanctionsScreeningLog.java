package com.flowguard.domain;

import com.flowguard.service.SanctionsScreeningService.HitType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record of every sanctions screening performed.
 * Required by AMF Instruction 2019-07 and ACPR guidelines on LCB-FT.
 * Records must be retained for 5 years (Art. L561-12 CMF).
 */
@Entity
@Table(name = "sanctions_screening_log")
@Getter
@Setter
@NoArgsConstructor
public class SanctionsScreeningLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The user being screened — nullable to allow screening of unregistered individuals */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "date_of_birth", length = 20)
    private String dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "hit_type", nullable = false, length = 20)
    private HitType hitType;

    @Column(name = "match_score", precision = 5, scale = 4)
    private BigDecimal matchScore;

    /** Truncated JSON of the matched list entry (max 500 chars) */
    @Column(name = "matched_entry", length = 500)
    private String matchedEntry;

    /** Source list: EU_CFSL, OFAC_SDN, UN_CONSOLIDATED */
    @Column(name = "list_source", length = 50)
    private String listSource;

    @Column(name = "screened_at", nullable = false, updatable = false)
    private Instant screenedAt;
}
