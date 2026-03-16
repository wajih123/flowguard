package com.flowguard.repository;

import com.flowguard.service.SanctionsScreeningService.HitType;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class SanctionsScreeningLogRepository implements PanacheRepositoryBase<com.flowguard.domain.SanctionsScreeningLog, UUID> {

    @Transactional
    public void persist(
            UUID userId,
            String fullName,
            String dateOfBirth,
            HitType hitType,
            double matchScore,
            String matchedEntry,
            String listSource
    ) {
        com.flowguard.domain.SanctionsScreeningLog log = new com.flowguard.domain.SanctionsScreeningLog();
        log.setUserId(userId);
        log.setFullName(fullName);
        log.setDateOfBirth(dateOfBirth);
        log.setHitType(hitType == null ? HitType.NO_HIT : hitType);
        log.setMatchScore(java.math.BigDecimal.valueOf(matchScore).setScale(4, java.math.RoundingMode.HALF_UP));
        log.setMatchedEntry(matchedEntry);
        log.setListSource(listSource);
        log.setScreenedAt(java.time.Instant.now());
        persist(log);
    }
}
