package com.flowguard.resource;

import com.flowguard.dto.TreasuryForecastDto;
import com.flowguard.service.AccountService;
import com.flowguard.service.TreasuryService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/accounts/{accountId}/treasury-forecast")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class TreasuryForecastResource {

    @Inject
    TreasuryService treasuryService;

    @Inject
    AccountService accountService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public Response getForecast(
            @PathParam("accountId") UUID accountId,
            @QueryParam("horizonDays") @DefaultValue("30") int horizonDays) {

        UUID userId = UUID.fromString(jwt.getSubject());
        accountService.getEntityAndVerifyOwnership(accountId, userId);

        TreasuryForecastDto forecast = treasuryService.getCachedForecast(userId, horizonDays);
        return Response.ok(forecast).build();
    }
}
