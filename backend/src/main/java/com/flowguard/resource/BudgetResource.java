package com.flowguard.resource;

import com.flowguard.dto.BudgetCategoryDto;
import com.flowguard.dto.BudgetVsActualDto;
import com.flowguard.service.BudgetService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Path("/budget")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class BudgetResource {

    @Inject BudgetService budgetService;
    @Inject JsonWebToken jwt;

    @GET
    @Path("/{year}/{month}")
    @RunOnVirtualThread
    public List<BudgetCategoryDto> getBudget(
            @PathParam("year") int year,
            @PathParam("month") int month) {
        return budgetService.getBudgetForPeriod(UUID.fromString(jwt.getSubject()), year, month);
    }

    @PUT
    @Path("/{year}/{month}/{category}")
    @RunOnVirtualThread
    public Response upsert(
            @PathParam("year") int year,
            @PathParam("month") int month,
            @PathParam("category") String category,
            BigDecimal amount) {
        BudgetCategoryDto dto = budgetService.upsert(
                UUID.fromString(jwt.getSubject()), year, month, category, amount);
        return Response.ok(dto).build();
    }

    @DELETE
    @Path("/line/{budgetId}")
    @RunOnVirtualThread
    public Response deleteLine(@PathParam("budgetId") UUID budgetId) {
        budgetService.deleteBudgetLine(UUID.fromString(jwt.getSubject()), budgetId);
        return Response.noContent().build();
    }

    @GET
    @Path("/vs-actual/{year}/{month}")
    @RunOnVirtualThread
    public BudgetVsActualDto getVsActual(
            @PathParam("year") int year,
            @PathParam("month") int month) {
        return budgetService.getBudgetVsActual(UUID.fromString(jwt.getSubject()), year, month);
    }
}
