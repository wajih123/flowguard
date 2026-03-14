package com.flowguard.resource;

import com.flowguard.dto.GdprExportDto;
import com.flowguard.service.GdprService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/gdpr")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class GdprResource {

    @Inject
    GdprService gdprService;

    @Inject
    JsonWebToken jwt;

    /**
     * Enregistrement du consentement RGPD (art. 7).
     */
    @POST
    @Path("/consent")
    @RunOnVirtualThread
    public Response recordConsent() {
        UUID userId = UUID.fromString(jwt.getSubject());
        gdprService.recordConsent(userId);
        return Response.ok().build();
    }

    /**
     * Export des données personnelles (art. 15 + art. 20 — portabilité).
     */
    @GET
    @Path("/export")
    @RunOnVirtualThread
    public Response exportData() {
        UUID userId = UUID.fromString(jwt.getSubject());
        GdprExportDto export = gdprService.exportUserData(userId);
        return Response.ok(export).build();
    }

    /**
     * Demande de suppression des données (art. 17 — droit à l'oubli).
     */
    @DELETE
    @Path("/data")
    @RunOnVirtualThread
    public Response requestDeletion() {
        UUID userId = UUID.fromString(jwt.getSubject());
        gdprService.requestDataDeletion(userId);
        return Response.noContent().build();
    }
}
