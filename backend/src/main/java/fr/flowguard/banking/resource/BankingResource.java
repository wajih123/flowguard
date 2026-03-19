package fr.flowguard.banking.resource;

import fr.flowguard.banking.entity.BankAccountEntity;
import fr.flowguard.banking.service.BridgeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Path("/api/banking")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Banking", description = "Connexion bancaire via Bridge API")
public class BankingResource {

    @Inject
    BridgeService bridgeService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/connect/start")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    @Operation(summary = "Demarrer le flow de connexion Bridge")
    public Response startConnect() {
        String userId = jwt.getSubject();
        try {
            BridgeService.ConnectStartResult result = bridgeService.startConnect(userId);
            return Response.ok(Map.of(
                "connect_url", result.connectUrl(),
                "state", result.state()
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", "Impossible de demarrer la connexion Bridge: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/connect/callback")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    @Operation(summary = "Finaliser la connexion apres le callback Bridge")
    public Response handleCallback(CallbackRequest request) {
        if (request == null || request.state() == null || request.state().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "state requis")).build();
        }
        String userId = jwt.getSubject();
        try {
            BridgeService.SyncResult result = bridgeService.handleCallback(request.state(), userId);
            return Response.ok(Map.of(
                "accounts_synced", result.accountsSynced(),
                "synced_at", result.syncedAt().toString()
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage())).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", "Erreur lors de la synchronisation: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/sync")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    @Operation(summary = "Synchronisation manuelle des comptes")
    public Response syncAccounts() {
        String userId = jwt.getSubject();
        try {
            BridgeService.SyncResult result = bridgeService.syncAccounts(userId);
            return Response.ok(Map.of(
                "accounts_synced", result.accountsSynced(),
                "synced_at", result.syncedAt().toString()
            )).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", "Erreur de synchronisation: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/accounts")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    @Operation(summary = "Lister les comptes bancaires connectes")
    public Response getAccounts() {
        String userId = jwt.getSubject();
        List<BankAccountEntity> accounts = bridgeService.getAccounts(userId);
        List<AccountDto> dtos = accounts.stream().map(a -> new AccountDto(
            a.id,
            a.bankName,
            a.accountName != null ? a.accountName : a.bankName,
            a.ibanMasked,
            a.currentBalance != null ? a.currentBalance.doubleValue() : 0.0,
            a.currency,
            a.accountType != null ? a.accountType : "CHECKING",
            a.syncStatus,
            a.lastSyncAt != null ? a.lastSyncAt.toString() : null
        )).toList();
        return Response.ok(dtos).build();
    }

    public record CallbackRequest(String state) {}

    public record AccountDto(
        String id,
        String bankName,
        String accountName,
        String ibanMasked,
        double balance,
        String currency,
        String accountType,
        String syncStatus,
        String lastSyncAt
    ) {}
}