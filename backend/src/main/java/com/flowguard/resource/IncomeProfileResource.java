package com.flowguard.resource;

import com.flowguard.service.IncomeProfileService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import java.util.*;

@Path("/api/income-profile")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IncomeProfileResource {

    @Inject JsonWebToken jwt;

    @Inject
    IncomeProfileService incomeProfileService;

    @GET
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    public Response analyze(@QueryParam("months") @DefaultValue("12") int months) {
        String userId = jwt.getSubject();
        IncomeProfileService.IncomeProfile profile = incomeProfileService.analyzeIncomeProfile(userId, months);
        return Response.ok(profile).build();
    }

    @GET
    @Path("/sustainable-income")
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    public Response sustainableIncome() {
        String userId = jwt.getSubject();
        double sustainable = incomeProfileService.calculateSustainableIncome(userId);
        return Response.ok(Map.of("sustainableIncome", sustainable)).build();
    }
}
