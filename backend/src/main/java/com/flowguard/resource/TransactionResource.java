package com.flowguard.resource;

import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.TransactionDto;
import com.flowguard.service.TransactionService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Path("/accounts/{accountId}/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class TransactionResource {

    @Inject
    TransactionService transactionService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public Response getTransactions(
            @PathParam("accountId") UUID accountId,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("category") String category) {

        UUID userId = UUID.fromString(jwt.getSubject());
        transactionService.verifyAccountOwnership(accountId, userId);

        List<TransactionDto> transactions;

        if (from != null && to != null) {
            transactions = transactionService.getByAccountIdAndPeriod(
                    accountId, LocalDate.parse(from), LocalDate.parse(to));
        } else if (category != null) {
            TransactionEntity.TransactionCategory cat =
                    TransactionEntity.TransactionCategory.valueOf(category);
            transactions = transactionService.getByAccountIdAndCategory(accountId, cat);
        } else {
            transactions = transactionService.getByAccountId(accountId);
        }

        return Response.ok(transactions).build();
    }

    @GET
    @Path("/recurring")
    @RunOnVirtualThread
    public Response getRecurring(@PathParam("accountId") UUID accountId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        transactionService.verifyAccountOwnership(accountId, userId);

        List<TransactionDto> recurring = transactionService.getRecurringByAccountId(accountId);
        return Response.ok(recurring).build();
    }
}
