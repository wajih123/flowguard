package com.flowguard.resource;

import com.flowguard.dto.TreasuryForecastDto;
import com.flowguard.service.TreasuryService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/treasury")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class TreasuryResource {

    @Inject
    TreasuryService treasuryService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/forecast")
    @RunOnVirtualThread
    public Response getForecast(@QueryParam("horizon") @DefaultValue("30") int horizonDays) {
        if (horizonDays < 7 || horizonDays > 90) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthResource.ErrorBody("Horizon doit être entre 7 et 90 jours"))
                    .build();
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        TreasuryForecastDto forecast = treasuryService.getCachedForecast(userId, horizonDays);
        return Response.ok(forecast).build();
    }
}
