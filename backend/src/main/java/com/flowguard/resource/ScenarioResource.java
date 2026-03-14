package com.flowguard.resource;

import com.flowguard.dto.ScenarioRequest;
import com.flowguard.dto.ScenarioResponse;
import com.flowguard.service.ScenarioService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/scenario")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class ScenarioResource {

    @Inject
    ScenarioService scenarioService;

    @Inject
    JsonWebToken jwt;

    @POST
    @RunOnVirtualThread
    public Response runScenario(@Valid ScenarioRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ScenarioResponse response = scenarioService.runScenario(userId, request);
        return Response.ok(response).build();
    }
}
