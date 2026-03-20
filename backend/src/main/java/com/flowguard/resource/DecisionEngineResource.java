package com.flowguard.resource;

import com.flowguard.dto.*;
import com.flowguard.service.DecisionEngineService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/decision-engine")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class DecisionEngineResource {

    @Inject
    DecisionEngineService decisionEngineService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/summary")
    @RunOnVirtualThread
    public Response getSummary() {
        UUID userId = UUID.fromString(jwt.getSubject());
        DecisionSummaryDto summary = decisionEngineService.getSummary(userId);
        return Response.ok(summary).build();
    }

    @POST
    @Path("/refresh")
    @RunOnVirtualThread
    public Response refresh() {
        UUID userId = UUID.fromString(jwt.getSubject());
        DecisionSummaryDto summary = decisionEngineService.refresh(userId);
        return Response.ok(summary).build();
    }

    @GET
    @Path("/drivers")
    @RunOnVirtualThread
    public Response getDrivers() {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<CashDriverDto> drivers = decisionEngineService.getDrivers(userId);
        return Response.ok(drivers).build();
    }

    @GET
    @Path("/actions")
    @RunOnVirtualThread
    public Response getActions() {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<CashActionDto> actions = decisionEngineService.getActions(userId);
        return Response.ok(actions).build();
    }

    @POST
    @Path("/simulate")
    @RunOnVirtualThread
    public Response simulate(SimulateRequestDto req) {
        if (req == null || req.scenarioType() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthResource.ErrorBody("scenarioType est requis"))
                    .build();
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        SimulateResultDto result = decisionEngineService.simulate(userId, req);
        return Response.ok(result).build();
    }

    @POST
    @Path("/actions/{id}/apply")
    @RunOnVirtualThread
    public Response applyAction(@PathParam("id") String actionId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        try {
            CashActionDto result = decisionEngineService.applyAction(actionId, userId);
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new AuthResource.ErrorBody("Action introuvable"))
                    .build();
        }
    }

    @POST
    @Path("/actions/{id}/dismiss")
    @RunOnVirtualThread
    public Response dismissAction(@PathParam("id") String actionId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        try {
            CashActionDto result = decisionEngineService.dismissAction(actionId, userId);
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new AuthResource.ErrorBody("Action introuvable"))
                    .build();
        }
    }

    @GET
    @Path("/brief")
    @RunOnVirtualThread
    public Response getLatestBrief() {
        UUID userId = UUID.fromString(jwt.getSubject());
        WeeklyBriefDto brief = decisionEngineService.getLatestBrief(userId);
        if (brief == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new AuthResource.ErrorBody("Aucun bilan disponible"))
                    .build();
        }
        return Response.ok(brief).build();
    }

    @POST
    @Path("/brief/generate")
    @RunOnVirtualThread
    public Response generateBrief() {
        UUID userId = UUID.fromString(jwt.getSubject());
        WeeklyBriefDto brief = decisionEngineService.generateBrief(userId);
        return Response.status(Response.Status.CREATED).entity(brief).build();
    }
}
