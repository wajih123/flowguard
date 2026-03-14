package com.flowguard.resource;

import com.flowguard.domain.AlertEntity;
import com.flowguard.domain.AlertThresholdEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.AlertThresholdDto;
import com.flowguard.dto.AlertThresholdRequest;
import com.flowguard.repository.AlertThresholdRepository;
import com.flowguard.repository.UserRepository;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Path("/alert-thresholds")
@Authenticated
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AlertThresholdResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    AlertThresholdRepository thresholdRepository;

    @Inject
    UserRepository userRepository;

    @GET
    public List<AlertThresholdDto> getThresholds() {
        UUID userId = UUID.fromString(jwt.getSubject());
        return thresholdRepository.findByUserId(userId).stream()
                .map(AlertThresholdDto::from)
                .toList();
    }

    @PUT
    @Transactional
    public AlertThresholdDto upsertThreshold(@Valid AlertThresholdRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        AlertEntity.AlertType type = AlertEntity.AlertType.valueOf(request.alertType());
        AlertEntity.Severity minSeverity = AlertEntity.Severity.valueOf(request.minSeverity());

        AlertThresholdEntity threshold = thresholdRepository
                .findByUserIdAndType(userId, type)
                .orElseGet(() -> {
                    UserEntity user = userRepository.findById(userId);
                    if (user == null) {
                        throw new IllegalArgumentException("Utilisateur introuvable");
                    }
                    AlertThresholdEntity t = new AlertThresholdEntity();
                    t.setUser(user);
                    t.setAlertType(type);
                    return t;
                });

        threshold.setMinAmount(request.minAmount() != null ? request.minAmount() : BigDecimal.ZERO);
        threshold.setEnabled(request.enabled());
        threshold.setMinSeverity(minSeverity);

        thresholdRepository.persist(threshold);
        return AlertThresholdDto.from(threshold);
    }
}
