package com.flowguard.resource;

import com.flowguard.domain.SweepSuggestionEntity;
import com.flowguard.service.SweepSuggestionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import java.util.*;

@Path("/api/sweep")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SweepResource {

    @Inject JsonWebToken jwt;

    @Inject
    SweepSuggestionService sweepService;

    @GET
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    public Response generateSuggestions() {
        String userId = jwt.getSubject();
        List<SweepSuggestionEntity> suggestions = sweepService.generateSweepSuggestions(userId);
        return Response.ok(suggestions).build();
    }

    @POST
    @Path("/{suggestionId}/execute")
    @Transactional
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    public Response executeSweep(@PathParam("suggestionId") String suggestionId) {
        String userId = jwt.getSubject();
        sweepService.executeSweep(suggestionId);
        return Response.ok(Map.of("status", "executed", "suggestionId", suggestionId)).build();
    }
}
