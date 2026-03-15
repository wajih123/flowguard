package com.flowguard.resource;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.BankAccountDto;
import com.flowguard.service.BankAccountSyncService;
import com.flowguard.service.BridgeService;
import com.flowguard.service.BridgeService.BridgeApiException;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Banking endpoints powered by Bridge API v3.
 *
 * <p>Endpoints consumed by the frontend:
 * <ul>
 *   <li>{@code POST /api/banking/connect/start}    — starts a connect session, returns {connect_url, state}
 *   <li>{@code POST /api/banking/connect/callback} — finalises connection, syncs accounts + transactions
 *   <li>{@code POST /api/banking/sync}             — manual re-sync
 *   <li>{@code GET  /api/banking/accounts}         — list connected accounts
 * </ul>
 */
@Path("/banking")
@RolesAllowed("user")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NordigenResource {

    @Inject
    BridgeService bridgeService;

    @Inject
    BankAccountSyncService bankAccountSyncService;

    @Inject
    JsonWebToken jwt;

    // ══════════════════════════════════════════════════════════════
    //  POST /api/banking/connect/start
    // ══════════════════════════════════════════════════════════════

    /**
     * Step 1 of the Bridge connect flow.
     * Creates (or retrieves) a Bridge user for the caller, obtains a user token,
     * then creates a Bridge connect session.
     *
     * @return {@code {"connect_url": "...", "state": "..."}}
     */
    @POST
    @Path("/connect/start")
    @Transactional
    public Response startConnect() {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();

        try {
            // Lazily create Bridge user and cache their UUID
            String bridgeUuid = user.getBridgeUserUuid();
            if (bridgeUuid == null || bridgeUuid.isBlank()) {
                bridgeUuid = bridgeService.getOrCreateBridgeUser(userId.toString());
                user.setBridgeUserUuid(bridgeUuid);
            }

            // Get a fresh user access token (short-lived, ~30 min)
            String userToken = bridgeService.getUserToken(bridgeUuid);

            // State = CSRF token stored by the frontend in sessionStorage
            String state = UUID.randomUUID().toString().replace("-", "");

            // Create Bridge connect session — Bridge will redirect to
            // bridge.redirect-url?context={state} after user connects
            BridgeService.ConnectSession session =
                    bridgeService.createConnectSession(userToken, user.getEmail(), state);

            Map<String, String> body = new LinkedHashMap<>();
            body.put("connect_url", session.redirectUrl());
            body.put("state", session.context());      // "state" key matches frontend ConnectStartResponse
            return Response.ok(body).build();

        } catch (BridgeApiException e) {
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  POST /api/banking/connect/callback
    // ══════════════════════════════════════════════════════════════

    /**
     * Step 2 of the Bridge connect flow.
     * Called after Bridge redirects the user back to the frontend.
     * Syncs all accounts and last-6-months transactions.
     *
     * @return {@code {"accounts_synced": N, "synced_at": "..."}}
     */
    @POST
    @Path("/connect/callback")
    @Transactional
    public Response handleCallback(CallbackRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();

        String bridgeUuid = user.getBridgeUserUuid();
        if (bridgeUuid == null || bridgeUuid.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Aucun compte Bridge associé à cet utilisateur"))
                    .build();
        }

        try {
            String userToken = bridgeService.getUserToken(bridgeUuid);
            int synced = syncUserAccounts(user, userToken);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("accounts_synced", synced);
            body.put("synced_at", Instant.now().toString());
            return Response.ok(body).build();

        } catch (BridgeApiException e) {
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  POST /api/banking/sync
    // ══════════════════════════════════════════════════════════════

    /** Manual re-sync of accounts and transactions. */
    @POST
    @Path("/sync")
    @Transactional
    public Response sync() {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();

        String bridgeUuid = user.getBridgeUserUuid();
        if (bridgeUuid == null || bridgeUuid.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Aucun compte Bridge associé. Connectez d'abord une banque."))
                    .build();
        }

        try {
            String userToken = bridgeService.getUserToken(bridgeUuid);
            int synced = syncUserAccounts(user, userToken);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("accounts_synced", synced);
            body.put("synced_at", Instant.now().toString());
            return Response.ok(body).build();

        } catch (BridgeApiException e) {
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  GET /api/banking/accounts
    // ══════════════════════════════════════════════════════════════

    /** Returns the user's connected bank accounts from the local DB. */
    @GET
    @Path("/accounts")
    public Response listAccounts() {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<BankAccountDto> accounts = AccountEntity
                .<AccountEntity>find("user.id", userId)
                .stream()
                .map(BankAccountDto::from)
                .toList();
        return Response.ok(accounts).build();
    }

    // ══════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════

    /**
     * Fetches all accounts from Bridge and delegates each one to
     * {@link BankAccountSyncService#syncAccount} which runs in its own
     * {@code REQUIRES_NEW} transaction.  A failure on one account is logged
     * but does not abort the others.
     *
     * @return number of accounts successfully synced
     */
    private int syncUserAccounts(UserEntity user, String userToken) {
        List<BridgeService.BridgeAccount> bridgeAccounts = bridgeService.listAccounts(userToken);
        int synced = 0;
        for (BridgeService.BridgeAccount ba : bridgeAccounts) {
            try {
                bankAccountSyncService.syncAccount(user, userToken, ba);
                synced++;
            } catch (Exception e) {
                // One account failing must not block the rest
            }
        }
        return synced;
    }

    // ── Request/response records ──────────────────────────────────

    /** Body sent by the frontend after Bridge redirects back. */
    public record CallbackRequest(String state) {}
}
