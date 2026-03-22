package com.flowguard.resource;

import com.flowguard.dto.InvoiceDto;
import com.flowguard.dto.InvoiceRequest;
import com.flowguard.service.InvoiceService;
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

@Path("/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class InvoiceResource {

    @Inject InvoiceService invoiceService;
    @Inject JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public List<InvoiceDto> list() {
        return invoiceService.getByUserId(UUID.fromString(jwt.getSubject()));
    }

    @GET
    @Path("/{id}")
    @RunOnVirtualThread
    public InvoiceDto get(@PathParam("id") UUID id) {
        return invoiceService.getById(UUID.fromString(jwt.getSubject()), id);
    }

    @POST
    @RunOnVirtualThread
    public Response create(@Valid InvoiceRequest req) {
        InvoiceDto dto = invoiceService.create(UUID.fromString(jwt.getSubject()), req);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @POST
    @Path("/{id}/send")
    @RunOnVirtualThread
    public InvoiceDto send(@PathParam("id") UUID id) {
        return invoiceService.send(UUID.fromString(jwt.getSubject()), id);
    }

    @POST
    @Path("/{id}/mark-paid")
    @RunOnVirtualThread
    public InvoiceDto markPaid(@PathParam("id") UUID id) {
        return invoiceService.markPaid(UUID.fromString(jwt.getSubject()), id);
    }

    @POST
    @Path("/{id}/cancel")
    @RunOnVirtualThread
    public InvoiceDto cancel(@PathParam("id") UUID id) {
        return invoiceService.cancel(UUID.fromString(jwt.getSubject()), id);
    }

    @POST
    @Path("/{id}/toggle-reminder")
    @RunOnVirtualThread
    public InvoiceDto toggleReminder(@PathParam("id") UUID id) {
        return invoiceService.toggleReminder(UUID.fromString(jwt.getSubject()), id);
    }
}
