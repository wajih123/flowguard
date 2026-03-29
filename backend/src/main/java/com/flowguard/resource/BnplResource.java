package com.flowguard.resource;

import com.flowguard.domain.BnplInstallmentEntity;
import com.flowguard.service.BnplDetectionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import java.util.*;

@Path("/bnpl")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BnplResource {

    @Inject JsonWebToken jwt;

    @Inject
    BnplDetectionService bnplService;

    @GET
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    public Response listInstallments() {
        String userId = jwt.getSubject();
        List<BnplInstallmentEntity> plans = bnplService.detectBnplTransactions(userId);
        return Response.ok(plans).build();
    }

    @GET
    @Path("/summary")
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    public Response getSummary(@QueryParam("monthlyIncome") @DefaultValue("0") double monthlyIncome) {
        String userId = jwt.getSubject();
        BnplDetectionService.BnplAnalysis analysis = bnplService.analyzeBnplCommitments(userId, monthlyIncome);
        return Response.ok(analysis).build();
    }
}
