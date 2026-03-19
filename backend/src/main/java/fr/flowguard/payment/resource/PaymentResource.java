package fr.flowguard.payment.resource;

import fr.flowguard.payment.entity.PaymentInitiationEntity;
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
import java.util.List;
import java.util.UUID;

@Path("/api/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Payments", description = "Initiation de virements")
public class PaymentResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Lister les paiements de l utilisateur")
    public Response list() {
        String userId = jwt.getSubject();
        List<PaymentInitiationEntity> payments = PaymentInitiationEntity.findByUser(userId);
        return Response.ok(payments.stream().map(this::toDto).toList()).build();
    }

    @POST
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Initier un virement")
    public Response initiate(InitiateRequest req, @HeaderParam("Idempotency-Key") String idempotencyKey) {
        String userId = jwt.getSubject();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDto("Idempotency-Key header is required")).build();
        }
        // Idempotency check
        PaymentInitiationEntity existing = PaymentInitiationEntity.findByIdempotencyKeyAndUser(idempotencyKey, userId);
        if (existing != null) {
            return Response.ok(toDto(existing)).build();
        }
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDto("amount must be positive")).build();
        }
        PaymentInitiationEntity p = new PaymentInitiationEntity();
        p.id = UUID.randomUUID().toString();
        p.userId = userId;
        p.creditorName = req.creditorName();
        p.creditorIban = req.creditorIban();
        p.amount = req.amount();
        p.currency = req.currency() != null ? req.currency() : "EUR";
        p.reference = req.reference() != null ? req.reference() : "";
        p.status = "PENDING";
        p.idempotencyKey = idempotencyKey;
        p.persist();
        return Response.status(Response.Status.CREATED).entity(toDto(p)).build();
    }

    @POST
    @Path("/{id}/cancel")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Annuler un paiement en attente")
    public Response cancel(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        PaymentInitiationEntity p = PaymentInitiationEntity.findByIdAndUser(id, userId);
        if (p == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(new ErrorDto("Payment not found")).build();
        }
        if (!p.status.equals("PENDING") && !p.status.equals("SUBMITTED")) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorDto("Cannot cancel payment with status: " + p.status)).build();
        }
        p.status = "CANCELLED";
        return Response.ok(toDto(p)).build();
    }

    private PaymentDto toDto(PaymentInitiationEntity p) {
        return new PaymentDto(
                p.id, p.creditorName, p.creditorIban,
                p.amount.doubleValue(), p.currency,
                p.reference, p.status, p.swanPaymentId,
                p.initiatedAt != null ? p.initiatedAt.toString() : null,
                p.executedAt != null ? p.executedAt.toString() : null
        );
    }

    public record InitiateRequest(
        String creditorName, String creditorIban,
        BigDecimal amount, String currency, String reference
    ) {}

    public record PaymentDto(
        String id, String creditorName, String creditorIban,
        double amount, String currency, String reference,
        String status, String swanPaymentId,
        String initiatedAt, String executedAt
    ) {}

    public record ErrorDto(String message) {}
}