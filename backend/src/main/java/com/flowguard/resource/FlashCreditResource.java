package com.flowguard.resource;

import com.flowguard.dto.FlashCreditDto;
import com.flowguard.dto.FlashCreditRequest;
import com.flowguard.service.FlashCreditService;
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

@Path("/flash-credit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class FlashCreditResource {

    @Inject
    FlashCreditService flashCreditService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public Response getCredits() {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<FlashCreditDto> credits = flashCreditService.getCreditsByUserId(userId);
        return Response.ok(credits).build();
    }

    @POST
    @RunOnVirtualThread
    public Response requestCredit(@Valid FlashCreditRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        FlashCreditDto credit = flashCreditService.requestCredit(userId, request);
        return Response.status(Response.Status.CREATED).entity(credit).build();
    }

    @POST
    @Path("/{creditId}/retract")
    @RunOnVirtualThread
    public Response exerciseRetraction(@PathParam("creditId") UUID creditId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        FlashCreditDto credit = flashCreditService.exerciseRetraction(userId, creditId);
        return Response.ok(credit).build();
    }

    @POST
    @Path("/{creditId}/repay")
    @RunOnVirtualThread
    public Response repayCredit(@PathParam("creditId") UUID creditId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        FlashCreditDto credit = flashCreditService.repayCredit(userId, creditId);
        return Response.ok(credit).build();
    }
}
