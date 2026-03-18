package com.flowguard.resource;

import com.flowguard.dto.AccountantAccessDto;
import com.flowguard.dto.InvoiceDto;
import com.flowguard.dto.TaxEstimateDto;
import com.flowguard.service.AccountantAccessService;
import com.flowguard.service.FecExportService;
import com.flowguard.service.InvoiceService;
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

@Path("/accountant")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountantResource {

    @Inject AccountantAccessService accessService;
    @Inject InvoiceService invoiceService;
    @Inject TaxEstimateService taxService;
    @Inject FecExportService fecExportService;
    @Inject JsonWebToken jwt;

    // ── Owner endpoints (authenticated) ──────────────────────────────────────

    @GET
    @Path("/grants")
    @RolesAllowed("user")
    @RunOnVirtualThread
    public List<AccountantAccessDto> listGrants() {
        return accessService.listGrants(UUID.fromString(jwt.getSubject()));
    }

    @POST
    @Path("/grants")
    @RolesAllowed("user")
    @RunOnVirtualThread
    public Response grant(@QueryParam("email") String accountantEmail) {
        AccountantAccessDto dto = accessService.grantAccess(
                UUID.fromString(jwt.getSubject()), accountantEmail);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @DELETE
    @Path("/grants/{grantId}")
    @RolesAllowed("user")
    @RunOnVirtualThread
    public Response revoke(@PathParam("grantId") UUID grantId) {
        accessService.revokeAccess(UUID.fromString(jwt.getSubject()), grantId);
        return Response.noContent().build();
    }

    // ── Accountant read-only portal (token-based, no JWT) ────────────────────

    @GET
    @Path("/portal/invoices")
    @RunOnVirtualThread
    public List<InvoiceDto> portalInvoices(
            @HeaderParam("X-Accountant-Token") String token) {
        UUID ownerId = accessService.validateToken(token);
        return invoiceService.getByUserId(ownerId);
    }

    @GET
    @Path("/portal/tax")
    @RunOnVirtualThread
    public List<TaxEstimateDto> portalTax(
            @HeaderParam("X-Accountant-Token") String token) {
        UUID ownerId = accessService.validateToken(token);
        return taxService.getAll(ownerId);
    }

    @GET
    @Path("/portal/fec")
    @Produces("text/plain")
    @RunOnVirtualThread
    public Response portalFec(
            @HeaderParam("X-Accountant-Token") String token,
            @QueryParam("year") int year) {
        UUID ownerId = accessService.validateToken(token);
        String fec = fecExportService.exportFec(ownerId, year);
        return Response.ok(fec)
                .header("Content-Disposition", "attachment; filename=FEC_" + year + ".txt")
                .build();
    }
}
