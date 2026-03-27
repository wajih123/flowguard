package com.flowguard.resource;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.CashGoalEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.CashGoalDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.CashGoalRepository;
import com.flowguard.repository.UserRepository;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Path("/cash-goal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class CashGoalResource {

    public record UpsertGoalRequest(
            @NotNull @DecimalMin("1") BigDecimal targetAmount,
            @Size(max = 200) String label
    ) {}

    @Inject CashGoalRepository  cashGoalRepository;
    @Inject AccountRepository   accountRepository;
    @Inject UserRepository      userRepository;
    @Inject JsonWebToken         jwt;

    @GET
    @RunOnVirtualThread
    public Response get() {
        UUID userId = UUID.fromString(jwt.getSubject());
        Optional<CashGoalEntity> goal = cashGoalRepository.findByUserId(userId);
        if (goal.isEmpty()) return Response.noContent().build();
        return Response.ok(toDto(goal.get(), userId)).build();
    }

    @PUT
    @Transactional
    @RunOnVirtualThread
    public Response upsert(@Valid UpsertGoalRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserEntity user = userRepository.findById(userId);
        if (user == null) throw new NotFoundException("User not found");

        Optional<CashGoalEntity> existing = cashGoalRepository.findByUserId(userId);
        CashGoalEntity goal = existing.orElse(CashGoalEntity.builder().user(user).build());
        goal.setTargetAmount(req.targetAmount());
        goal.setLabel(req.label() != null && !req.label().isBlank()
                ? req.label() : "Réserve de trésorerie");
        cashGoalRepository.persistAndFlush(goal);
        return Response.ok(toDto(goal, userId)).build();
    }

    @DELETE
    @Transactional
    @RunOnVirtualThread
    public Response delete() {
        UUID userId = UUID.fromString(jwt.getSubject());
        cashGoalRepository.deleteByUserId(userId);
        return Response.noContent().build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CashGoalDto toDto(CashGoalEntity goal, UUID userId) {
        List<AccountEntity> accounts = accountRepository.findActiveByUserId(userId);
        BigDecimal balance = accounts.stream()
                .map(AccountEntity::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal target = goal.getTargetAmount();
        BigDecimal progressPct = target.compareTo(BigDecimal.ZERO) > 0
                ? balance.multiply(BigDecimal.valueOf(100))
                        .divide(target, 1, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Estimate days to reach: assume average monthly net income of last known data
        // Simple heuristic: if progress < 100%, estimate based on current shortfall
        long estimatedDays = -1;
        LocalDate estimatedDate = null;
        BigDecimal shortfall = target.subtract(balance);
        if (shortfall.compareTo(BigDecimal.ZERO) > 0) {
            // Assume a modest monthly savings rate of 10% of balance as placeholder
            BigDecimal monthlySavings = balance.multiply(new BigDecimal("0.10"));
            if (monthlySavings.compareTo(BigDecimal.ZERO) > 0) {
                long months = shortfall.divide(monthlySavings, 0, RoundingMode.CEILING).longValue();
                estimatedDays = months * 30L;
                estimatedDate = LocalDate.now().plusDays(estimatedDays);
            }
        } else {
            estimatedDays = 0;
            estimatedDate = LocalDate.now();
        }

        return new CashGoalDto(
                goal.getId(),
                target,
                goal.getLabel(),
                balance,
                progressPct,
                estimatedDays,
                estimatedDate
        );
    }
}
