package fr.flowguard.benchmark.resource;

import fr.flowguard.banking.entity.BankAccountEntity;
import fr.flowguard.benchmark.entity.SectorBenchmarkEntity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Path("/api/benchmarks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Benchmarks", description = "Benchmarks sectoriels")
public class BenchmarkResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/sectors")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Liste des secteurs disponibles")
    public Response getSectors() {
        List<String> sectors = SectorBenchmarkEntity.findDistinctSectors();
        return Response.ok(sectors).build();
    }

    @GET
    @Path("/{sector}/{companySize}")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Benchmarks pour un secteur et une taille d entreprise")
    public Response getBenchmarks(@PathParam("sector") String sector,
                                   @PathParam("companySize") String companySize) {
        List<SectorBenchmarkEntity> benchmarks = SectorBenchmarkEntity.findBySectorAndSize(sector, companySize);
        List<BenchmarkDto> dtos = benchmarks.stream().map(b ->
                new BenchmarkDto(b.id, b.sector, b.companySize, b.metricName,
                        b.p25.doubleValue(), b.p50.doubleValue(), b.p75.doubleValue(), b.unit)
        ).toList();
        return Response.ok(dtos).build();
    }

    @GET
    @Path("/compare/{sector}/{companySize}")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Comparer les metriques utilisateur aux benchmarks du secteur")
    public Response compare(@PathParam("sector") String sector,
                             @PathParam("companySize") String companySize) {
        String userId = jwt.getSubject();

        // Get user's current balance
        List<BankAccountEntity> accounts = BankAccountEntity.list("userId = ?1 AND isActive = true", userId);
        BigDecimal userBalance = accounts.stream()
                .map(a -> a.currentBalance != null ? a.currentBalance : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SectorBenchmarkEntity> benchmarks = SectorBenchmarkEntity.findBySectorAndSize(sector, companySize);

        List<UserBenchmarkDto> comparisons = benchmarks.stream().map(b -> {
            double userValue = switch (b.metricName) {
                case "cash_balance" -> userBalance.doubleValue();
                default -> 0.0;
            };
            String percentile = computePercentile(userValue, b.p25.doubleValue(), b.p50.doubleValue(), b.p75.doubleValue());
            String insight = buildInsight(b.metricName, percentile, userValue, b.p50.doubleValue());
            return new UserBenchmarkDto(b.sector, b.companySize, b.metricName,
                    userValue, b.unit, b.p25.doubleValue(), b.p50.doubleValue(), b.p75.doubleValue(),
                    percentile, insight);
        }).toList();

        return Response.ok(comparisons).build();
    }

    private String computePercentile(double value, double p25, double p50, double p75) {
        if (value < p25) return "BOTTOM_25";
        if (value < p50) return "Q1_Q2";
        if (value < p75) return "Q2_Q3";
        return "TOP_25";
    }

    private String buildInsight(String metric, String percentile, double userValue, double median) {
        if ("cash_balance".equals(metric)) {
            return switch (percentile) {
                case "BOTTOM_25" -> "Votre tresorerie est inferieure a 75% de votre secteur. Attention aux projections.";
                case "Q1_Q2"     -> "Votre tresorerie est dans la moyenne basse du secteur.";
                case "Q2_Q3"     -> "Votre tresorerie est dans la moyenne haute du secteur.";
                case "TOP_25"    -> "Excellente tresorerie, dans le top 25% de votre secteur.";
                default          -> "";
            };
        }
        return "";
    }

    public record BenchmarkDto(String id, String sector, String companySize, String metricName,
                                double p25, double p50, double p75, String unit) {}
    public record UserBenchmarkDto(String sector, String companySize, String metricName,
                                    double userValue, String unit, double p25, double p50, double p75,
                                    String percentileBand, String insight) {}
}