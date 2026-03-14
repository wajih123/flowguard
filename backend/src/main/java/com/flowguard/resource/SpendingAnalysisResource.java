package com.flowguard.resource;

import com.flowguard.dto.SpendingAnalysisDto;
import com.flowguard.service.AccountService;
import com.flowguard.service.SpendingAnalysisService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;
import java.util.UUID;

@Path("/accounts/{accountId}/spending-analysis")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class SpendingAnalysisResource {

    @Inject
    SpendingAnalysisService spendingAnalysisService;

    @Inject
    AccountService accountService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public Response analyze(
            @PathParam("accountId") UUID accountId,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {

        UUID userId = UUID.fromString(jwt.getSubject());
        accountService.getEntityAndVerifyOwnership(accountId, userId);

        LocalDate fromDate = from != null ? LocalDate.parse(from) : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? LocalDate.parse(to) : LocalDate.now();

        SpendingAnalysisDto analysis = spendingAnalysisService.analyze(accountId, fromDate, toDate);
        return Response.ok(analysis).build();
    }
}
