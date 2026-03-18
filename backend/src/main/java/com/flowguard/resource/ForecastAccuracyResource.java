package com.flowguard.resource;

import com.flowguard.dto.ForecastAccuracyDto;
import com.flowguard.service.ForecastAccuracyService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/forecast-accuracy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class ForecastAccuracyResource {

    @Inject ForecastAccuracyService accuracyService;
    @Inject JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public List<ForecastAccuracyDto> getAll() {
        return accuracyService.getByUserId(UUID.fromString(jwt.getSubject()));
    }

    @GET
    @Path("/{horizon}")
    @RunOnVirtualThread
    public List<ForecastAccuracyDto> getByHorizon(@PathParam("horizon") int horizonDays) {
        return accuracyService.getByUserAndHorizon(UUID.fromString(jwt.getSubject()), horizonDays);
    }

    @GET
    @Path("/summary")
    @RunOnVirtualThread
    public Map<String, Object> getSummary() {
        UUID userId = UUID.fromString(jwt.getSubject());
        double avg = accuracyService.getAverageAccuracy(userId);
        List<ForecastAccuracyDto> all = accuracyService.getByUserId(userId);
        long reconciled = all.stream().filter(e -> e.actualBalance() != null).count();
        return Map.of(
                "averageAccuracyPct", Math.round(avg * 10.0) / 10.0,
                "totalEntries", all.size(),
                "reconciled", reconciled
        );
    }
}
