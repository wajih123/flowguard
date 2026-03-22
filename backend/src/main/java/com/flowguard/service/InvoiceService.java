package com.flowguard.service;

import com.flowguard.domain.InvoiceEntity;
import com.flowguard.domain.InvoiceEntity.InvoiceStatus;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.InvoiceDto;
import com.flowguard.dto.InvoiceRequest;
import com.flowguard.repository.InvoiceRepository;
import com.flowguard.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ForbiddenException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class InvoiceService {

    @Inject InvoiceRepository invoiceRepository;
    @Inject UserRepository userRepository;

    public List<InvoiceDto> getByUserId(UUID userId) {
        markOverdue(userId);
        return invoiceRepository.findByUserId(userId).stream().map(InvoiceDto::from).toList();
    }

    public InvoiceDto getById(UUID userId, UUID invoiceId) {
        InvoiceEntity inv = findOwned(userId, invoiceId);
        return InvoiceDto.from(inv);
    }

    @Transactional
    public InvoiceDto create(UUID userId, InvoiceRequest req) {
        UserEntity user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        BigDecimal vatAmount = req.amountHt()
                .multiply(req.vatRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalTtc = req.amountHt().add(vatAmount);
        InvoiceEntity inv = InvoiceEntity.builder()
                .user(user)
                .clientName(req.clientName())
                .clientEmail(req.clientEmail())
                .number(req.number())
                .amountHt(req.amountHt())
                .vatRate(req.vatRate())
                .vatAmount(vatAmount)
                .totalTtc(totalTtc)
                .currency(req.currency() != null ? req.currency() : "EUR")
                .issueDate(req.issueDate())
                .dueDate(req.dueDate())
                .notes(req.notes())
                .build();
        invoiceRepository.persist(inv);
        return InvoiceDto.from(inv);
    }

    @Transactional
    public InvoiceDto send(UUID userId, UUID invoiceId) {
        InvoiceEntity inv = findOwned(userId, invoiceId);
        if (inv.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT invoices can be marked as SENT");
        }
        inv.setStatus(InvoiceStatus.SENT);
        return InvoiceDto.from(inv);
    }

    @Transactional
    public InvoiceDto markPaid(UUID userId, UUID invoiceId) {
        InvoiceEntity inv = findOwned(userId, invoiceId);
        if (inv.getStatus() == InvoiceStatus.PAID || inv.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Invoice already " + inv.getStatus());
        }
        inv.setStatus(InvoiceStatus.PAID);
        inv.setPaidAt(Instant.now());
        return InvoiceDto.from(inv);
    }

    @Transactional
    public InvoiceDto cancel(UUID userId, UUID invoiceId) {
        InvoiceEntity inv = findOwned(userId, invoiceId);
        if (inv.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("Cannot cancel a paid invoice");
        }
        inv.setStatus(InvoiceStatus.CANCELLED);
        return InvoiceDto.from(inv);
    }

    /** AR summary for dashboard. */
    public java.math.BigDecimal getOutstandingTotal(UUID userId) {
        return invoiceRepository.sumOutstandingByUserId(userId);
    }

    @Transactional
    public InvoiceDto toggleReminder(UUID userId, UUID invoiceId) {
        InvoiceEntity inv = findOwned(userId, invoiceId);
        inv.setReminderEnabled(!inv.isReminderEnabled());
        return InvoiceDto.from(inv);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void markOverdue(UUID userId) {
        List<InvoiceEntity> candidates = invoiceRepository.findOverdueCandidates(LocalDate.now());
        candidates.stream()
                .filter(i -> i.getUser().getId().equals(userId))
                .forEach(i -> i.setStatus(InvoiceStatus.OVERDUE));
    }

    private InvoiceEntity findOwned(UUID userId, UUID invoiceId) {
        InvoiceEntity inv = invoiceRepository.findByIdOptional(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
        if (!inv.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Access denied");
        }
        return inv;
    }
}
