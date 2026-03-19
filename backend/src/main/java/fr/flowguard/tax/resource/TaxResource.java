package fr.flowguard.tax.resource;

import fr.flowguard.tax.entity.TaxEstimateEntity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Path("/api/tax")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tax", description = "Estimation des obligations fiscales")
public class TaxResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Lister toutes les obligations fiscales")
    public Response getAll() {
        String userId = jwt.getSubject();
        List<TaxEstimateEntity> estimates = TaxEstimateEntity.findByUser(userId);
        return Response.ok(estimates.stream().map(this::toDto).toList()).build();
    }

    @GET
    @Path("/upcoming")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Prochaines echeances non payees")
    public Response getUpcoming() {
        String userId = jwt.getSubject();
        List<TaxEstimateEntity> estimates = TaxEstimateEntity.findUpcomingByUser(userId);
        return Response.ok(estimates.stream().map(this::toDto).toList()).build();
    }

    @POST
    @Path("/{id}/mark-paid")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Transactional
    @Operation(summary = "Marquer une obligation comme payee")
    public Response markPaid(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        return TaxEstimateEntity.findByIdAndUser(id, userId).map(est -> {
            est.paidAt = Instant.now();
            return Response.ok(toDto(est)).build();
        }).orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Estimation introuvable")).build());
    }

    @POST
    @Path("/regenerate")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Transactional
    @Operation(summary = "Regenerer les estimations fiscales pour l annee en cours")
    public Response regenerate() {
        String userId = jwt.getSubject();
        int year = LocalDate.now().getYear();

        // Delete non-paid future estimates
        TaxEstimateEntity.delete(
                "userId = ?1 AND paidAt IS NULL AND dueDate > ?2", userId, LocalDate.now());

        // Generate standard French fiscal deadlines (approximations)
        List<TaxEstimateEntity> generated = List.of(
            makeTax(userId, "TVA",    "TVA T1 " + year,     new BigDecimal("2400"), LocalDate.of(year, 4, 20)),
            makeTax(userId, "TVA",    "TVA T2 " + year,     new BigDecimal("2400"), LocalDate.of(year, 7, 20)),
            makeTax(userId, "TVA",    "TVA T3 " + year,     new BigDecimal("2400"), LocalDate.of(year, 10, 20)),
            makeTax(userId, "TVA",    "TVA T4 " + year,     new BigDecimal("2400"), LocalDate.of(year + 1, 1, 20)),
            makeTax(userId, "URSSAF", "URSSAF T1 " + year,  new BigDecimal("1800"), LocalDate.of(year, 4, 5)),
            makeTax(userId, "URSSAF", "URSSAF T2 " + year,  new BigDecimal("1800"), LocalDate.of(year, 7, 5)),
            makeTax(userId, "URSSAF", "URSSAF T3 " + year,  new BigDecimal("1800"), LocalDate.of(year, 10, 5)),
            makeTax(userId, "URSSAF", "URSSAF T4 " + year,  new BigDecimal("1800"), LocalDate.of(year + 1, 1, 5)),
            makeTax(userId, "IS",     "IS " + year,          new BigDecimal("5000"), LocalDate.of(year, 9, 15)),
            makeTax(userId, "CFE",    "CFE " + year,         new BigDecimal("800"),  LocalDate.of(year, 12, 15))
        );

        for (TaxEstimateEntity est : generated) {
            // Skip if already past due
            if (!est.dueDate.isBefore(LocalDate.now())) {
                est.persist();
            }
        }

        List<TaxEstimateEntity> all = TaxEstimateEntity.findByUser(userId);
        return Response.ok(all.stream().map(this::toDto).toList()).build();
    }

    private TaxEstimateEntity makeTax(String userId, String type, String label,
                                       BigDecimal amount, LocalDate dueDate) {
        TaxEstimateEntity t = new TaxEstimateEntity();
        t.userId          = userId;
        t.taxType         = type;
        t.periodLabel     = label;
        t.estimatedAmount = amount;
        t.dueDate         = dueDate;
        return t;
    }

    private TaxDto toDto(TaxEstimateEntity e) {
        long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), e.dueDate);
        return new TaxDto(e.id, e.taxType, e.periodLabel,
                e.estimatedAmount.doubleValue(), e.dueDate.toString(),
                e.paidAt != null ? e.paidAt.toString() : null,
                daysUntilDue, e.paidAt != null);
    }

    public record TaxDto(
        String id, String taxType, String periodLabel,
        double estimatedAmount, String dueDate, String paidAt,
        long daysUntilDue, boolean isPaid
    ) {}
}