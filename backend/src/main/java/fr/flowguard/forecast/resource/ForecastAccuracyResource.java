package fr.flowguard.forecast.resource;

import fr.flowguard.forecast.entity.ForecastAccuracyEntity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Path("/api/forecast-accuracy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "ForecastAccuracy", description = "Suivi de la precision des previsions")
public class ForecastAccuracyResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Lister toutes les entrees de precision")
    public Response getAll() {
        String userId = jwt.getSubject();
        List<ForecastAccuracyEntity> entries = ForecastAccuracyEntity.findByUser(userId);
        return Response.ok(entries.stream().map(this::toDto).toList()).build();
    }

    @GET
    @Path("/{horizonDays}")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Entrees filtrees par horizon")
    public Response getByHorizon(@PathParam("horizonDays") int horizonDays) {
        String userId = jwt.getSubject();
        List<ForecastAccuracyEntity> entries = ForecastAccuracyEntity.findByUserAndHorizon(userId, horizonDays);
        return Response.ok(entries.stream().map(this::toDto).toList()).build();
    }

    @GET
    @Path("/summary")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Resume de la precision globale")
    public Response getSummary() {
        String userId = jwt.getSubject();
        List<ForecastAccuracyEntity> all = ForecastAccuracyEntity.findByUser(userId);
        long total = all.size();
        long reconciled = all.stream().filter(e -> e.actualBalance != null).count();
        double avgAccuracy = all.stream()
                .filter(e -> e.accuracyPct != null)
                .mapToDouble(e -> e.accuracyPct.doubleValue())
                .average().orElse(0.0);
        return Response.ok(new SummaryDto(
                BigDecimal.valueOf(avgAccuracy).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                total, reconciled)).build();
    }

    private ForecastAccuracyDto toDto(ForecastAccuracyEntity e) {
        return new ForecastAccuracyDto(
                e.id, e.forecastDate.toString(), e.horizonDays,
                e.predictedBalance.doubleValue(),
                e.actualBalance != null ? e.actualBalance.doubleValue() : null,
                e.mae != null ? e.mae.doubleValue() : null,
                e.accuracyPct != null ? e.accuracyPct.doubleValue() : null,
                e.recordedAt.toString()
        );
    }

    public record ForecastAccuracyDto(
        String id, String forecastDate, int horizonDays,
        double predictedBalance, Double actualBalance,
        Double mae, Double accuracyPct, String recordedAt
    ) {}

    public record SummaryDto(double averageAccuracyPct, long totalEntries, long reconciled) {}
}