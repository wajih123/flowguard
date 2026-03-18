package com.flowguard.repository;

import com.flowguard.domain.InvoiceEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class InvoiceRepository implements PanacheRepositoryBase<InvoiceEntity, UUID> {

    public List<InvoiceEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY createdAt DESC", userId);
    }

    public List<InvoiceEntity> findByUserIdAndStatus(UUID userId, InvoiceEntity.InvoiceStatus status) {
        return list("user.id = ?1 AND status = ?2 ORDER BY dueDate ASC", userId, status);
    }

    /** Find all SENT invoices whose due date has passed (overdue candidates). */
    public List<InvoiceEntity> findOverdueCandidates(LocalDate today) {
        return list("status = ?1 AND dueDate < ?2", InvoiceEntity.InvoiceStatus.SENT, today);
    }

    /** Sum of total_ttc for SENT+OVERDUE invoices (AR outstanding). */
    public java.math.BigDecimal sumOutstandingByUserId(UUID userId) {
        return (java.math.BigDecimal) getEntityManager()
                .createQuery("SELECT COALESCE(SUM(i.totalTtc), 0) FROM InvoiceEntity i " +
                        "WHERE i.user.id = :uid AND i.status IN ('SENT','OVERDUE')")
                .setParameter("uid", userId)
                .getSingleResult();
    }
}
