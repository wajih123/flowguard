package com.flowguard.resource;

import com.flowguard.dto.SpendingPatternDto;
import com.flowguard.service.SpendingPatternService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/spending")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class SpendingPatternResource {

    @Inject
    SpendingPatternService spendingPatternService;

    @Inject
    JsonWebToken jwt;

    /**
     * GET /api/spending/patterns
     * Returns 90-day spending pattern analysis: daily average, today's total,
     * weekend vs weekday comparison, and detected hidden subscriptions.
     */
    @GET
    @Path("/patterns")
    @RunOnVirtualThread
    public SpendingPatternDto getPatterns() {
        return spendingPatternService.computePatterns(UUID.fromString(jwt.getSubject()));
    }
}
