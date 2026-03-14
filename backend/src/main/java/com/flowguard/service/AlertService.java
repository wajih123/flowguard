package com.flowguard.service;

import com.flowguard.domain.AlertEntity;
import com.flowguard.domain.AlertThresholdEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.AlertDto;
import com.flowguard.dto.TreasuryForecastDto;
import com.flowguard.repository.AlertRepository;
import com.flowguard.repository.AlertThresholdRepository;
import com.flowguard.repository.UserRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AlertService {

    private static final Logger LOG = Logger.getLogger(AlertService.class);

    @Inject
    AlertRepository alertRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    TreasuryService treasuryService;

    @Inject
    AlertWebSocketService alertWebSocketService;

    @Inject
    AlertThresholdRepository alertThresholdRepository;

    public List<AlertDto> getAlertsByUserId(UUID userId) {
        return alertRepository.findByUserId(userId).stream()
                .map(AlertDto::from)
                .toList();
    }

    public List<AlertDto> getUnreadAlerts(UUID userId) {
        return alertRepository.findUnreadByUserId(userId).stream()
                .map(AlertDto::from)
                .toList();
    }

    public long getUnreadCount(UUID userId) {
        return alertRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAsRead(UUID alertId, UUID userId) {
        int updated = alertRepository.markAsRead(alertId, userId);
        if (updated == 0) {
            throw new IllegalArgumentException("Alerte introuvable");
        }
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        alertRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public AlertDto createAlert(UUID userId, AlertEntity.AlertType type, AlertEntity.Severity severity,
                                String message, BigDecimal projectedDeficit, java.time.LocalDate triggerDate) {
        // Check user thresholds — skip alert if filtered out
        var threshold = alertThresholdRepository.findByUserIdAndType(userId, type);
        if (threshold.isPresent()) {
            AlertThresholdEntity t = threshold.get();
            if (!t.isEnabled()) {
                return null;
            }
            if (severity.ordinal() < t.getMinSeverity().ordinal()) {
                return null;
            }
            if (projectedDeficit != null && projectedDeficit.abs().compareTo(t.getMinAmount()) < 0) {
                return null;
            }
        }

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("Utilisateur introuvable");
        }

        AlertEntity alert = AlertEntity.builder()
                .user(user)
                .type(type)
                .severity(severity)
                .message(message)
                .projectedDeficit(projectedDeficit)
                .triggerDate(triggerDate)
                .build();

        alertRepository.persist(alert);

        AlertDto dto = AlertDto.from(alert);
        alertWebSocketService.broadcastToUser(userId, dto);

        return dto;
    }

    @Scheduled(cron = "${flowguard.alert.cron}")
    @Transactional
    void checkAndGenerateAlerts() {
        LOG.info("Running scheduled alert generation...");

        List<UserEntity> users = userRepository.listAll();
        for (UserEntity user : users) {
            try {
                generateAlertsForUser(user);
            } catch (Exception e) {
                LOG.errorf(e, "Error generating alerts for user %s", user.getId());
            }
        }
    }

    private void generateAlertsForUser(UserEntity user) {
        TreasuryForecastDto forecast = treasuryService.getCachedForecast(user.getId(), 30);

        if (forecast == null || forecast.criticalPoints() == null) {
            return;
        }

        for (TreasuryForecastDto.CriticalPoint cp : forecast.criticalPoints()) {
            if (cp.predictedBalance().compareTo(BigDecimal.ZERO) < 0) {
                createAlert(
                        user.getId(),
                        AlertEntity.AlertType.CASH_SHORTAGE,
                        determineSeverity(cp.predictedBalance()),
                        String.format("Déficit de trésorerie prévu le %s : %s €. %s",
                                cp.date(), cp.predictedBalance().toPlainString(), cp.reason()),
                        cp.predictedBalance(),
                        cp.date()
                );
            }
        }
    }

    private AlertEntity.Severity determineSeverity(BigDecimal deficit) {
        BigDecimal abs = deficit.abs();
        if (abs.compareTo(new BigDecimal("10000")) >= 0) {
            return AlertEntity.Severity.CRITICAL;
        } else if (abs.compareTo(new BigDecimal("5000")) >= 0) {
            return AlertEntity.Severity.HIGH;
        } else if (abs.compareTo(new BigDecimal("1000")) >= 0) {
            return AlertEntity.Severity.MEDIUM;
        }
        return AlertEntity.Severity.LOW;
    }
}
