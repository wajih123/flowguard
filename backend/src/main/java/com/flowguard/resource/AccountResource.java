package com.flowguard.resource;

import com.flowguard.dto.AccountDto;
import com.flowguard.service.AccountService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class AccountResource {

    @Inject
    AccountService accountService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public Response getAccounts() {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<AccountDto> accounts = accountService.getAccountsByUserId(userId);
        return Response.ok(accounts).build();
    }

    @GET
    @Path("/{accountId}")
    @RunOnVirtualThread
    public Response getAccount(@PathParam("accountId") UUID accountId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        AccountDto account = accountService.getAccountById(accountId, userId);
        return Response.ok(account).build();
    }
}
