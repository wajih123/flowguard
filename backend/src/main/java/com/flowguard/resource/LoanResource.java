package com.flowguard.resource;

import com.flowguard.domain.LoanAmortizationEntity;
import com.flowguard.service.LoanDetectionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import java.math.BigDecimal;
import java.util.*;

@Path("/loans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LoanResource {

    @Inject JsonWebToken jwt;

    @Inject
    LoanDetectionService loanService;

    @GET
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    public Response listLoans() {
        String userId = jwt.getSubject();
        List<LoanAmortizationEntity> loans = loanService.detectLoanTransactions(userId);
        return Response.ok(loans).build();
    }

    @POST
    @RolesAllowed({"ROLE_USER","ROLE_BUSINESS","ROLE_ADMIN","ROLE_SUPER_ADMIN"})
    @Consumes(MediaType.APPLICATION_JSON)
    public Response calculateAmortization(
            @QueryParam("principal") double principal,
            @QueryParam("annualRate") double annualRate,
            @QueryParam("months") int months) {

        LoanDetectionService.AmortizationSchedule schedule = 
            loanService.calculateAmortization(
                BigDecimal.valueOf(principal),
                annualRate,
                months
            );
        return Response.ok(schedule).build();
    }
}
