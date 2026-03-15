package com.flowguard.resource;

import com.flowguard.service.BridgeService;
import com.flowguard.service.BridgeErrorHandler;
import com.flowguard.service.PushNotificationService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Receives Bridge API webhook events and processes them asynchronously.
 *
 * <p>Security: Every incoming webhook request is verified via HMAC-SHA256
 * signature (Bridge sends the signature in the {@code Bridge-Signature} header).
 *
 * <p>Bridge doc: https://docs.bridgeapi.io/docs/webhooks
 */
@Path("/webhooks/bridge")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class BridgeWebhookResource {

    private static final Logger LOG = Logger.getLogger(BridgeWebhookResource.class);

    @ConfigProperty(name = "bridge.webhook-secret", defaultValue = "")
    String webhookSecret;

    @Inject
    BridgeService bridgeService;

    @Inject
    BridgeErrorHandler bridgeErrorHandler;

    @Inject
    PushNotificationService pushService;

    /**
     * Bridge webhook endpoint.
     *
     * <p>Bridge sends a POST with JSON body and {@code Bridge-Signature: sha256=<hex>}.
     *
     * @param signature the {@code Bridge-Signature} header value
     * @param rawBody   the raw JSON body (must be read as String for signature verification)
     */
    @POST
    @RunOnVirtualThread
    public Response handleWebhook(
            @HeaderParam("Bridge-Signature") String signature,
            String rawBody) {

        // 1. Verify HMAC signature (skip in test if secret not configured)
        if (!webhookSecret.isBlank()) {
            if (!verifySignature(rawBody, signature)) {
                LOG.warnf("Rejected Bridge webhook with invalid signature");
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("error", "Invalid signature"))
                        .build();
            }
        }

        // 2. Parse and dispatch
        try {
            processEvent(rawBody);
        } catch (Exception e) {
            // Always return 200 to Bridge to prevent retries for processing errors
            LOG.errorf("Error processing Bridge webhook: %s", e.getMessage());
        }

        return Response.ok(Map.of("status", "accepted")).build();
    }

    // ── HMAC verification ──────────────────────────────────────────────────────

    /**
     * Verifies the Bridge-Signature header.
     * Format: {@code sha256=<hex_digest>}
     */
    private boolean verifySignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            String receivedHex = signatureHeader.substring(7); // Remove "sha256="
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    receivedHex.getBytes(StandardCharsets.UTF_8),
                    computedHex.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.errorf("HMAC verification error: %s", e.getMessage());
            return false;
        }
    }

    // ── Event dispatch ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void processEvent(String rawBody) {
        // Basic JSON parsing without full object mapping to avoid schema changes
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> event;
        try {
            event = mapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            LOG.warnf("Failed to parse Bridge webhook JSON: %s", e.getMessage());
            return;
        }

        String type = (String) event.get("type");
        LOG.debugf("Received Bridge webhook event: %s", type);

        if (type == null) return;

        switch (type) {
            // Item (bank connection) events
            case "item.connection_expired",
                 "item.needs_human_action",
                 "item.wrong_credentials" -> handleConnectionIssue(event, type);

            // Transaction events — trigger re-sync
            case "item.transactions.updated" -> handleTransactionsUpdated(event);

            // Account balance events
            case "item.accounts.updated" -> handleAccountsUpdated(event);

            // Refresh events
            case "item.refreshed" -> LOG.debugf("Bridge item refreshed: %s", event.get("item_id"));

            default -> LOG.debugf("Unhandled Bridge webhook type: %s", type);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleConnectionIssue(Map<String, Object> event, String type) {
        Object itemId = event.get("item_id");
        Object bridgeUserId = event.get("user_id");

        LOG.warnf("Bridge connection issue type=%s item=%s user=%s", type, itemId, bridgeUserId);

        // Map the event to a proper Bridge error code and let BridgeErrorHandler process it
        // user_id from webhook is Bridge UUID — pass it for logging; alert targets FlowGuard user
        bridgeErrorHandler.handle(type, null, "Bridge webhook: " + type + " item=" + itemId);
    }

    @SuppressWarnings("unchecked")
    private void handleTransactionsUpdated(Map<String, Object> event) {
        Object itemId = event.get("item_id");
        LOG.infof("Bridge transactions updated for item=%s — triggering sync", itemId);
        // Trigger async sync — BridgeService will pull new transactions
        if (itemId != null) {
            try {
                bridgeService.syncTransactionsForItem(String.valueOf(itemId));
            } catch (Exception e) {
                LOG.warnf("Failed to sync transactions for item %s: %s", itemId, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleAccountsUpdated(Map<String, Object> event) {
        Object itemId = event.get("item_id");
        LOG.infof("Bridge accounts updated for item=%s — triggering balance sync", itemId);
        if (itemId != null) {
            try {
                bridgeService.syncAccountBalances(String.valueOf(itemId));
            } catch (Exception e) {
                LOG.warnf("Failed to sync balances for item %s: %s", itemId, e.getMessage());
            }
        }
    }
}
