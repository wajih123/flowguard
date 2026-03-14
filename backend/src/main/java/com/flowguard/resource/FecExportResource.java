package com.flowguard.resource;

import com.flowguard.service.FecExportService;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;
import java.util.UUID;

@Path("/export")
@Authenticated
@RunOnVirtualThread
public class FecExportResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    FecExportService fecExportService;

    /**
     * Download a FEC (Fichier des Écritures Comptables) for a given fiscal period.
     */
    @GET
    @Path("/fec")
    @Produces("text/tab-separated-values")
    public Response exportFec(
            @QueryParam("from") String fromParam,
            @QueryParam("to") String toParam
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        LocalDate from = fromParam != null ? LocalDate.parse(fromParam) : LocalDate.now().withDayOfYear(1);
        LocalDate to = toParam != null ? LocalDate.parse(toParam) : LocalDate.now();

        String fecContent = fecExportService.generateFec(userId, from, to);

        String filename = String.format("FEC_%s_%s_%s.txt",
                userId.toString().substring(0, 8), from, to);

        return Response.ok(fecContent)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .type(MediaType.valueOf("text/tab-separated-values"))
                .build();
    }
}
