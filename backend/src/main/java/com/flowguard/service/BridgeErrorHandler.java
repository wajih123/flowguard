package com.flowguard.service;

import com.flowguard.domain.AlertEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Centralised handler for Bridge API error codes.
 *
 * <p>Bridge returns error codes in the {@code code} field of error responses.
 * Each code maps to a specific FlowGuard action (alert, re-auth request, etc.).
 *
 * @see <a href="https://docs.bridgeapi.io/docs/errors">Bridge error reference</a>
 */
@ApplicationScoped
public class BridgeErrorHandler {

    private static final Logger LOG = Logger.getLogger(BridgeErrorHandler.class);

    @Inject
    AlertService alertService;

    /**
     * Handle a Bridge API error code for a given user/account.
     *
     * @param errorCode  the {@code code} field from Bridge's error response
     * @param userId     FlowGuard user ID (for alerting)
     * @param rawMessage original Bridge error message (for logging)
     * @return a {@link BridgeErrorAction} describing what the caller should do
     */
    public BridgeErrorAction handle(String errorCode, UUID userId, String rawMessage) {
        LOG.infof("Bridge error [%s] for user %s: %s", errorCode, userId, rawMessage);

        return switch (errorCode) {

            // ── Connection errors — user action required ──────────────────────
            case "item.connection_expired" -> {
                sendAlert(userId, AlertEntity.AlertType.BANK_SYNC_ERROR,
                    "Votre connexion bancaire a expiré. Reconnectez votre banque pour continuer.");
                yield BridgeErrorAction.REAUTH_REQUIRED;
            }

            case "item.needs_human_action" -> {
                sendAlert(userId, AlertEntity.AlertType.BANK_SYNC_ERROR,
                    "Votre banque demande une validation supplémentaire (SCA). Ouvrez FlowGuard pour la compléter.");
                yield BridgeErrorAction.SCA_REQUIRED;
            }

            case "item.wrong_credentials" -> {
                sendAlert(userId, AlertEntity.AlertType.BANK_SYNC_ERROR,
                    "Vos identifiants bancaires semblent incorrects. Reconnectez votre banque.");
                yield BridgeErrorAction.REAUTH_REQUIRED;
            }

            case "item.not_found" -> {
                LOG.warnf("Bridge item not found for user %s — cleaning up DB reference", userId);
                yield BridgeErrorAction.CLEANUP_ACCOUNT;
            }

            case "item.refreshing" -> {
                LOG.debugf("Bridge is already refreshing for user %s — skip", userId);
                yield BridgeErrorAction.RETRY_LATER;
            }

            // ── Rate limiting ─────────────────────────────────────────────────
            case "rate_limit_exceeded" -> {
                LOG.warnf("Bridge rate limit exceeded for user %s", userId);
                yield BridgeErrorAction.BACKOFF_EXPONENTIAL;
            }

            case "too_many_requests" -> {
                LOG.warnf("Bridge too_many_requests for user %s", userId);
                yield BridgeErrorAction.BACKOFF_EXPONENTIAL;
            }

            // ── Auth / token errors ───────────────────────────────────────────
            case "invalid_token", "token_expired", "unauthorized" -> {
                LOG.warnf("Bridge token error [%s] for user %s", errorCode, userId);
                yield BridgeErrorAction.REFRESH_TOKEN;
            }

            // ── Transient / server errors ─────────────────────────────────────
            case "server_error", "service_unavailable", "gateway_timeout" -> {
                LOG.warnf("Bridge server error [%s] for user %s — will retry", errorCode, userId);
                yield BridgeErrorAction.RETRY_LATER;
            }

            // ── Bank-side errors ──────────────────────────────────────────────
            case "bank_unavailable" -> {
                LOG.infof("Bank unavailable for user %s — using cached data", userId);
                yield BridgeErrorAction.USE_CACHE;
            }

            default -> {
                LOG.warnf("Unknown Bridge error code [%s] for user %s: %s",
                    errorCode, userId, rawMessage);
                yield BridgeErrorAction.LOG_AND_CONTINUE;
            }
        };
    }

    public enum BridgeErrorAction {
        REAUTH_REQUIRED,        // Ask user to reconnect their bank
        SCA_REQUIRED,           // Strong Customer Authentication needed
        CLEANUP_ACCOUNT,        // Remove the dead Bridge item from DB
        RETRY_LATER,            // Transient error — retry in a few minutes
        BACKOFF_EXPONENTIAL,    // Rate-limited — use exponential backoff
        REFRESH_TOKEN,          // Get a new Bridge user access token
        USE_CACHE,              // Serve from Redis cache, skip live sync
        LOG_AND_CONTINUE        // Unknown/non-critical — log and proceed
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendAlert(UUID userId, AlertEntity.AlertType type, String message) {
        try {
            alertService.createAlert(userId, type,
                AlertEntity.Severity.HIGH, message, null, java.time.LocalDate.now());
        } catch (Exception e) {
            LOG.warnf("Could not create Bridge error alert for user %s: %s", userId, e.getMessage());
        }
    }
}
