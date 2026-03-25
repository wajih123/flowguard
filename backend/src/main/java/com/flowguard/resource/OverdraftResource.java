package com.flowguard.resource;

import com.flowguard.service.OverdraftPredictionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import java.util.*;

@Path("/api/overdraft")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OverdraftResource {

    @Inject JsonWebToken jwt;

    @Inject
    OverdraftPredictionService overdraftService;

    @GET
    @Path("/{accountId}")
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    public Response predictRisk(@PathParam("accountId") String accountId) {
        String userId = jwt.getSubject();
        OverdraftPredictionService.OverdraftRisk risk = overdraftService.predictOverdraftRisk(accountId);
        return Response.ok(risk).build();
    }

    @GET
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    public Response predictAllAccounts() {
        String userId = jwt.getSubject();
        List<Map<String, Object>> risks = new ArrayList<>();
        // Would normally fetch all user accounts and analyze each
        return Response.ok(risks).build();
    }
}
