package com.flowguard.repository;

import com.flowguard.domain.TracfinReport;
import com.flowguard.domain.TracfinReport.ReportStatus;
import com.flowguard.domain.TracfinReport.SuspicionType;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TracfinReportRepository implements PanacheRepositoryBase<TracfinReport, UUID> {

    /**
     * Returns true if there is already a non-resolved report for this user and suspicion type
     * (PENDING_REVIEW or CONFIRMED_SUSPICION). Prevents duplicate reports for the same activity.
     */
    public boolean hasOpenReport(UUID userId, SuspicionType type) {
        return count("userId = ?1 AND suspicionType = ?2 AND status IN ('PENDING_REVIEW', 'CONFIRMED_SUSPICION')",
                userId, type) > 0;
    }

    public List<TracfinReport> findByStatus(ReportStatus status) {
        return list("status = ?1 ORDER BY createdAt DESC", status);
    }

    public List<TracfinReport> findByUserId(UUID userId) {
        return list("userId = ?1 ORDER BY createdAt DESC", userId);
    }
}
