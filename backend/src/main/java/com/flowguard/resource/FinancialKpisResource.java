package com.flowguard.resource;

import com.flowguard.dto.FinancialKpisDto;
import com.flowguard.service.FinancialKpisService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/financial-kpis")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class FinancialKpisResource {

    @Inject
    FinancialKpisService kpisService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public Response getKpis() {
        UUID userId = UUID.fromString(jwt.getSubject());
        FinancialKpisDto kpis = kpisService.computeKpis(userId);
        return Response.ok(kpis).build();
    }
}
