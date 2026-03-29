package com.flowguard.resource;

import com.flowguard.domain.SavingsGoalEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.SavingsGoalDto;
import com.flowguard.repository.UserRepository;
import com.flowguard.service.SavingsGoalService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Path("/savings-goals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class SavingsGoalResource {

    public record CreateGoalRequest(
            @NotNull SavingsGoalEntity.GoalType goalType,
            @NotBlank @Size(max = 200) String label,
            @NotNull @DecimalMin("1") BigDecimal targetAmount,
            LocalDate targetDate,
            @DecimalMin("0") BigDecimal monthlyContribution
    ) {}

    public record UpdateGoalRequest(
            SavingsGoalEntity.GoalType goalType,
            @Size(max = 200) String label,
            @DecimalMin("1") BigDecimal targetAmount,
            LocalDate targetDate,
            @DecimalMin("0") BigDecimal monthlyContribution
    ) {}

    @Inject SavingsGoalService savingsGoalService;
    @Inject UserRepository     userRepository;
    @Inject JsonWebToken        jwt;

    @GET
    @RunOnVirtualThread
    public List<SavingsGoalDto> list() {
        return savingsGoalService.listGoals(currentUserId());
    }

    @GET
    @Path("/{id}")
    @RunOnVirtualThread
    public SavingsGoalDto get(@PathParam("id") UUID id) {
        return savingsGoalService.getGoal(id, currentUserId());
    }

    @POST
    @Transactional
    @RunOnVirtualThread
    public Response create(@Valid CreateGoalRequest req) {
        UUID userId = currentUserId();
        UserEntity user = userRepository.findById(userId);
        if (user == null) throw new NotFoundException("User not found");

        SavingsGoalDto dto = savingsGoalService.create(
                user,
                req.goalType(),
                req.label(),
                req.targetAmount(),
                req.targetDate(),
                req.monthlyContribution()
        );
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @RunOnVirtualThread
    public SavingsGoalDto update(@PathParam("id") UUID id, @Valid UpdateGoalRequest req) {
        return savingsGoalService.update(
                id,
                currentUserId(),
                req.goalType(),
                req.label(),
                req.targetAmount(),
                req.targetDate(),
                req.monthlyContribution()
        );
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RunOnVirtualThread
    public Response delete(@PathParam("id") UUID id) {
        savingsGoalService.delete(id, currentUserId());
        return Response.noContent().build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID currentUserId() {
        return UUID.fromString(jwt.getSubject());
    }
}
