package com.flowguard.service;

import com.flowguard.domain.PaymentInitiationEntity;
import com.flowguard.domain.PaymentInitiationEntity.PaymentStatus;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.PaymentInitiationDto;
import com.flowguard.dto.PaymentInitiationRequest;
import com.flowguard.repository.PaymentInitiationRepository;
import com.flowguard.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class PaymentInitiationService {

    /** Basic IBAN structural check — full validation deferred to Swan. */
    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");

    @Inject PaymentInitiationRepository paymentRepo;
    @Inject UserRepository userRepository;
    @Inject SwanService swanService;

    public List<PaymentInitiationDto> getByUserId(UUID userId) {
        return paymentRepo.findByUserId(userId).stream().map(PaymentInitiationDto::from).toList();
    }

    @Transactional
    public PaymentInitiationDto initiate(UUID userId, PaymentInitiationRequest req, String idempotencyKey) {
        // Idempotency check
        if (idempotencyKey != null) {
            var existing = paymentRepo.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) return PaymentInitiationDto.from(existing.get());
        }

        String normalizedIban = req.creditorIban().replaceAll("\\s", "").toUpperCase();
        if (!IBAN_PATTERN.matcher(normalizedIban).matches()) {
            throw new BadRequestException("IBAN format invalide : " + req.creditorIban());
        }

        UserEntity user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        PaymentInitiationEntity payment = PaymentInitiationEntity.builder()
                .user(user)
                .creditorName(req.creditorName())
                .creditorIban(normalizedIban)
                .amount(req.amount())
                .currency(req.currency() != null ? req.currency() : "EUR")
                .reference(req.reference())
                .idempotencyKey(idempotencyKey)
                .build();
        paymentRepo.persist(payment);

        // Submit to Swan PIS API (fire-and-forget; webhook will update status)
        try {
            String swanId = swanService.initiatePayment(
                    req.creditorName(), normalizedIban,
                    req.amount(), req.currency(), req.reference());
            payment.setSwanPaymentId(swanId);
            payment.setStatus(PaymentStatus.SUBMITTED);
        } catch (Exception ex) {
            payment.setStatus(PaymentStatus.REJECTED);
        }

        return PaymentInitiationDto.from(payment);
    }

    @Transactional
    public PaymentInitiationDto cancel(UUID userId, UUID paymentId) {
        PaymentInitiationEntity payment = paymentRepo.findByIdOptional(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (!payment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Access denied");
        }
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.SUBMITTED) {
            throw new BadRequestException("Cannot cancel a payment in status " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        return PaymentInitiationDto.from(payment);
    }
}
