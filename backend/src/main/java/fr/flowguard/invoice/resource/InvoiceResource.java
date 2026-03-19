package fr.flowguard.invoice.resource;

import fr.flowguard.invoice.entity.InvoiceEntity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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

@Path("/api/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Invoices", description = "Gestion des factures")
public class InvoiceResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Lister les factures")
    public Response list() {
        String userId = jwt.getSubject();
        // Mark overdue before returning
        markOverdue(userId);
        List<InvoiceEntity> invoices = InvoiceEntity.findByUser(userId);
        return Response.ok(invoices.stream().map(this::toDto).toList()).build();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Obtenir une facture")
    public Response get(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        return InvoiceEntity.findByIdAndUser(id, userId)
                .map(inv -> Response.ok(toDto(inv)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Facture introuvable")).build());
    }

    @POST
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Transactional
    @Operation(summary = "Creer une facture")
    public Response create(@Valid CreateInvoiceRequest req) {
        String userId = jwt.getSubject();
        InvoiceEntity inv = new InvoiceEntity();
        inv.userId       = userId;
        inv.clientName   = req.clientName();
        inv.clientEmail  = req.clientEmail();
        inv.number       = req.number();
        inv.amountHt     = req.amountHt();
        inv.vatRate      = req.vatRate() != null ? req.vatRate() : new BigDecimal("0.20");
        inv.vatAmount    = inv.amountHt.multiply(inv.vatRate).setScale(2, java.math.RoundingMode.HALF_UP);
        inv.totalTtc     = inv.amountHt.add(inv.vatAmount);
        inv.currency     = req.currency() != null ? req.currency() : "EUR";
        inv.issueDate    = LocalDate.parse(req.issueDate());
        inv.dueDate      = LocalDate.parse(req.dueDate());
        inv.notes        = req.notes();
        inv.persist();
        return Response.status(Response.Status.CREATED).entity(toDto(inv)).build();
    }

    @POST
    @Path("/{id}/send")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Transactional
    @Operation(summary = "Marquer comme envoyee")
    public Response send(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        return InvoiceEntity.findByIdAndUser(id, userId).map(inv -> {
            if (!inv.status.equals("DRAFT")) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "Seules les factures DRAFT peuvent etre envoyees")).build();
            }
            inv.status = "SENT";
            return Response.ok(toDto(inv)).build();
        }).orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Facture introuvable")).build());
    }

    @POST
    @Path("/{id}/mark-paid")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Transactional
    @Operation(summary = "Marquer comme payee")
    public Response markPaid(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        return InvoiceEntity.findByIdAndUser(id, userId).map(inv -> {
            if (inv.status.equals("CANCELLED") || inv.status.equals("PAID")) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "Statut invalide")).build();
            }
            inv.status = "PAID";
            inv.paidAt = Instant.now();
            return Response.ok(toDto(inv)).build();
        }).orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Facture introuvable")).build());
    }

    @POST
    @Path("/{id}/cancel")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Transactional
    @Operation(summary = "Annuler une facture")
    public Response cancel(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        return InvoiceEntity.findByIdAndUser(id, userId).map(inv -> {
            if (inv.status.equals("PAID")) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "Impossible d annuler une facture payee")).build();
            }
            inv.status = "CANCELLED";
            return Response.ok(toDto(inv)).build();
        }).orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Facture introuvable")).build());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @Transactional
    void markOverdue(String userId) {
        InvoiceEntity.update(
            "status = 'OVERDUE' WHERE userId = ?1 AND status = 'SENT' AND dueDate < ?2",
            userId, LocalDate.now()
        );
    }

    private InvoiceDto toDto(InvoiceEntity inv) {
        Long daysOverdue = null;
        if ("OVERDUE".equals(inv.status)) {
            daysOverdue = ChronoUnit.DAYS.between(inv.dueDate, LocalDate.now());
        }
        return new InvoiceDto(
            inv.id, inv.clientName, inv.clientEmail, inv.number,
            inv.amountHt.doubleValue(), inv.vatRate.doubleValue(),
            inv.vatAmount.doubleValue(), inv.totalTtc.doubleValue(),
            inv.currency, inv.status,
            inv.issueDate.toString(), inv.dueDate.toString(),
            inv.paidAt != null ? inv.paidAt.toString() : null,
            inv.notes, inv.createdAt.toString(),
            daysOverdue
        );
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record CreateInvoiceRequest(
        @NotBlank String clientName,
        String clientEmail,
        @NotBlank String number,
        @NotNull BigDecimal amountHt,
        BigDecimal vatRate,
        String currency,
        @NotBlank String issueDate,
        @NotBlank String dueDate,
        String notes
    ) {}

    public record InvoiceDto(
        String id, String clientName, String clientEmail,
        String number, double amountHt, double vatRate,
        double vatAmount, double totalTtc, String currency,
        String status, String issueDate, String dueDate,
        String paidAt, String notes, String createdAt,
        Long daysOverdue
    ) {}
}