package com.flowguard.service;

import com.flowguard.domain.TaxEstimateEntity;
import com.flowguard.domain.TaxEstimateEntity.TaxType;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.TaxEstimateDto;
import com.flowguard.repository.TaxEstimateRepository;
import com.flowguard.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TaxEstimateService {

    /**
     * Standard French TVA rate (taux normal). Services typically 20%.
     * For reduced rates, the user can adjust via the invoice VAT rate.
     */
    private static final BigDecimal TVA_RATE = new BigDecimal("0.20");

    /** Approximate URSSAF cotisations rate for micro-entreprise (services BIC). */
    private static final BigDecimal URSSAF_RATE_SERVICES = new BigDecimal("0.221");

    @Inject TaxEstimateRepository taxRepo;
    @Inject UserRepository userRepository;
    @Inject InvoiceService invoiceService;

    public List<TaxEstimateDto> getUpcoming(UUID userId) {
        return taxRepo.findUnpaidByUserId(userId).stream().map(TaxEstimateDto::from).toList();
    }

    public List<TaxEstimateDto> getAll(UUID userId) {
        return taxRepo.findByUserId(userId).stream().map(TaxEstimateDto::from).toList();
    }

    @Transactional
    public TaxEstimateDto markPaid(UUID userId, UUID estimateId) {
        TaxEstimateEntity entity = taxRepo.findByIdOptional(estimateId)
                .orElseThrow(() -> new NotFoundException("Tax estimate not found"));
        if (!entity.getUser().getId().equals(userId)) {
            throw new jakarta.ws.rs.ForbiddenException("Access denied");
        }
        entity.setPaidAt(Instant.now());
        return TaxEstimateDto.from(entity);
    }

    /**
     * Auto-generates tax estimates for the upcoming quarter based on invoice revenue.
     * Called after each invoice is marked PAID.
     */
    @Transactional
    public void regenerateEstimates(UUID userId) {
        UserEntity user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int quarter = (now.getMonthValue() - 1) / 3 + 1;
        String periodLabel = year + "-T" + quarter;

        // Get AR outstanding as revenue proxy (sum of paid invoices for quarter)
        BigDecimal quarterlyRevenue = invoiceService.getOutstandingTotal(userId);

        // Avoid generating if already exists for this period
        List<TaxEstimateEntity> existing = taxRepo.findByUserId(userId);
        boolean hasTva = existing.stream()
                .anyMatch(e -> e.getTaxType() == TaxType.TVA && periodLabel.equals(e.getPeriodLabel()));

        if (!hasTva && quarterlyRevenue.compareTo(BigDecimal.ZERO) > 0) {
            // TVA collectée estimate
            BigDecimal tvaEstimate = quarterlyRevenue.multiply(TVA_RATE).setScale(2, RoundingMode.HALF_UP);
            LocalDate tvaDue = getDueDate(TaxType.TVA, year, quarter);
            taxRepo.persist(TaxEstimateEntity.builder()
                    .user(user).taxType(TaxType.TVA).periodLabel(periodLabel)
                    .estimatedAmount(tvaEstimate).dueDate(tvaDue).build());

            // URSSAF charges sociales estimate
            BigDecimal urssafEstimate = quarterlyRevenue.multiply(URSSAF_RATE_SERVICES).setScale(2, RoundingMode.HALF_UP);
            LocalDate urssafDue = getDueDate(TaxType.URSSAF, year, quarter);
            taxRepo.persist(TaxEstimateEntity.builder()
                    .user(user).taxType(TaxType.URSSAF).periodLabel(periodLabel)
                    .estimatedAmount(urssafEstimate).dueDate(urssafDue).build());
        }
    }

    // French fiscal calendar — quarterly deadlines
    private LocalDate getDueDate(TaxType type, int year, int quarter) {
        return switch (type) {
            case TVA -> switch (quarter) {
                case 1 -> LocalDate.of(year, 4, 30);
                case 2 -> LocalDate.of(year, 7, 31);
                case 3 -> LocalDate.of(year, 10, 31);
                default -> LocalDate.of(year + 1, 1, 31);
            };
            case URSSAF -> switch (quarter) {
                case 1 -> LocalDate.of(year, 4, 30);
                case 2 -> LocalDate.of(year, 7, 31);
                case 3 -> LocalDate.of(year, 10, 31);
                default -> LocalDate.of(year + 1, 1, 31);
            };
            default -> LocalDate.of(year + 1, 5, 31); // IS/IR annual
        };
    }
}
