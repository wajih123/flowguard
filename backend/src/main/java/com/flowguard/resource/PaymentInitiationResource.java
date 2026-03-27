package com.flowguard.resource;

import com.flowguard.dto.PaymentInitiationDto;
import com.flowguard.dto.PaymentInitiationRequest;
import com.flowguard.service.PaymentInitiationService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class PaymentInitiationResource {

    @Inject PaymentInitiationService paymentService;
    @Inject JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public List<PaymentInitiationDto> list() {
        return paymentService.getByUserId(UUID.fromString(jwt.getSubject()));
    }

    /**
     * Initiates a SEPA payment via Swan PIS.
     * Requires an Idempotency-Key header to prevent duplicate submissions.
     */
    @POST
    @RunOnVirtualThread
    public Response initiate(
            @Valid PaymentInitiationRequest req,
            @HeaderParam("Idempotency-Key") String idempotencyKey) {
        PaymentInitiationDto dto = paymentService.initiate(
                UUID.fromString(jwt.getSubject()), req, idempotencyKey);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @POST
    @Path("/{id}/cancel")
    @RunOnVirtualThread
    public Response cancel(@PathParam("id") UUID id) {
        return Response.ok(paymentService.cancel(UUID.fromString(jwt.getSubject()), id)).build();
    }
}
