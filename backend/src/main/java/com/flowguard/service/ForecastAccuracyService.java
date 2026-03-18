package com.flowguard.service;

import com.flowguard.domain.ForecastAccuracyLogEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.ForecastAccuracyDto;
import com.flowguard.repository.ForecastAccuracyLogRepository;
import com.flowguard.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ForecastAccuracyService {

    @Inject ForecastAccuracyLogRepository accuracyRepo;
    @Inject UserRepository userRepository;

    public List<ForecastAccuracyDto> getByUserId(UUID userId) {
        return accuracyRepo.findByUserId(userId).stream().map(ForecastAccuracyDto::from).toList();
    }

    public List<ForecastAccuracyDto> getByUserAndHorizon(UUID userId, int horizonDays) {
        return accuracyRepo.findByUserIdAndHorizon(userId, horizonDays)
                .stream().map(ForecastAccuracyDto::from).toList();
    }

    /**
     * Record a new forecast point. Called by TreasuryService after each prediction.
     */
    @Transactional
    public void recordForecastPoint(UUID userId, LocalDate forecastDate, int horizonDays, BigDecimal predictedBalance) {
        UserEntity user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        // Avoid duplicate entries for same date+horizon
        Optional<ForecastAccuracyLogEntity> existing =
                accuracyRepo.findPendingReconciliation(userId, forecastDate, horizonDays);
        if (existing.isEmpty()) {
            ForecastAccuracyLogEntity log = ForecastAccuracyLogEntity.builder()
                    .user(user).forecastDate(forecastDate).horizonDays(horizonDays)
                    .predictedBalance(predictedBalance).build();
            accuracyRepo.persist(log);
        }
    }

    /**
     * Reconcile with actual balance once the forecast date arrives.
     * Called daily by a scheduler (or triggered by account sync).
     */
    @Transactional
    public void reconcile(UUID userId, LocalDate forecastDate, int horizonDays, BigDecimal actualBalance) {
        Optional<ForecastAccuracyLogEntity> optLog =
                accuracyRepo.findPendingReconciliation(userId, forecastDate, horizonDays);
        if (optLog.isEmpty()) return;
        ForecastAccuracyLogEntity log = optLog.get();
        log.setActualBalance(actualBalance);
        BigDecimal mae = log.getPredictedBalance().subtract(actualBalance).abs().setScale(2, RoundingMode.HALF_UP);
        log.setMae(mae);
    }

    /** Rolling average accuracy percentage across all reconciled entries for a user. */
    public Double getAverageAccuracy(UUID userId) {
        List<ForecastAccuracyDto> entries = getByUserId(userId);
        return entries.stream()
                .filter(e -> e.accuracyPct() != null)
                .mapToDouble(ForecastAccuracyDto::accuracyPct)
                .average()
                .orElse(0.0);
    }
}
