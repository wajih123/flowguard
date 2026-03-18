package com.flowguard.resource;

import com.flowguard.dto.TaxEstimateDto;
import com.flowguard.service.TaxEstimateService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/tax")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class TaxResource {

    @Inject TaxEstimateService taxService;
    @Inject JsonWebToken jwt;

    /** All tax estimates — paid and upcoming. */
    @GET
    @RunOnVirtualThread
    public List<TaxEstimateDto> getAll() {
        return taxService.getAll(UUID.fromString(jwt.getSubject()));
    }

    /** Only unpaid upcoming estimates. */
    @GET
    @Path("/upcoming")
    @RunOnVirtualThread
    public List<TaxEstimateDto> getUpcoming() {
        return taxService.getUpcoming(UUID.fromString(jwt.getSubject()));
    }

    /** Trigger regeneration of tax estimates based on current invoices. */
    @POST
    @Path("/regenerate")
    @RunOnVirtualThread
    public Response regenerate() {
        taxService.regenerateEstimates(UUID.fromString(jwt.getSubject()));
        return Response.noContent().build();
    }

    /** Mark a tax payment as paid. */
    @POST
    @Path("/{id}/mark-paid")
    @RunOnVirtualThread
    public TaxEstimateDto markPaid(@PathParam("id") UUID id) {
        return taxService.markPaid(UUID.fromString(jwt.getSubject()), id);
    }
}
