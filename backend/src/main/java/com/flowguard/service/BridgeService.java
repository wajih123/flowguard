package com.flowguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowguard.cache.RedisCacheService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Bridge API v3 aggregation client.
 *
 * <p>Auth model:
 * <ul>
 *   <li>Every request: {@code client-id} + {@code client-secret} headers + {@code Bridge-Version}
 *   <li>User-scoped requests (accounts, transactions, connect sessions):
 *       additionally need {@code Authorization: Bearer {userAccessToken}}
 * </ul>
 *
 * <p>Flow:
 * <ol>
 *   <li>Create Bridge user once per FlowGuard user → stores their Bridge UUID.
 *   <li>Get a short-lived user access token on demand.
 *   <li>Create a connect session → get a {@code redirect_url} to show the user.
 *   <li>User connects their bank on Bridge UI, gets redirected back with {@code ?context=state}.
 *   <li>Fetch accounts + transactions with the user token.
 * </ol>
 */
@ApplicationScoped
public class BridgeService {

    private static final Logger LOG = Logger.getLogger(BridgeService.class);

    @ConfigProperty(name = "bridge.api-url", defaultValue = "https://api.bridgeapi.io")
    String apiUrl;

    @ConfigProperty(name = "bridge.client-id")
    String clientId;

    @ConfigProperty(name = "bridge.client-secret")
    String clientSecret;

    @ConfigProperty(name = "bridge.version", defaultValue = "2025-01-15")
    String bridgeVersion;

    @ConfigProperty(name = "bridge.redirect-url")
    String redirectUrl;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RedisCacheService redisCache;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ── Request builders ─────────────────────────────────────────

    /** Builds an app-level request (no user token). */
    private HttpRequest.Builder appRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + path))
                .header("client-id", clientId)
                .header("client-secret", clientSecret)
                .header("Bridge-Version", bridgeVersion)
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .timeout(Duration.ofSeconds(20));
    }

    /** Builds a user-level request (adds Authorization: Bearer). */
    private HttpRequest.Builder userRequest(String path, String userToken) {
        return appRequest(path)
                .header("Authorization", "Bearer " + userToken);
    }

    // ── 1. User management ────────────────────────────────────────

    /**
     * Gets the Bridge UUID for an existing Bridge user, or creates one and returns it.
     *
     * @param externalUserId our FlowGuard user UUID (as string)
     * @return Bridge UUID string
     */
    public String getOrCreateBridgeUser(String externalUserId) {
        try {
            String cacheKey = "bridge:user:" + externalUserId;
            String cached = redisCache.get(cacheKey);
            if (cached != null) return cached;

            String body = objectMapper.writeValueAsString(Map.of("external_user_id", externalUserId));
            HttpRequest req = appRequest("/v3/aggregation/users")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 409) {
                // User already exists on Bridge — fetch by external_user_id
                return fetchBridgeUserByExternalId(externalUserId);
            }
            if (resp.statusCode() != 201) {
                throw new BridgeApiException("Create user failed: " + resp.statusCode() + " — " + resp.body());
            }

            JsonNode json = objectMapper.readTree(resp.body());
            String bridgeUuid = json.get("uuid").asText();
            redisCache.set(cacheKey, bridgeUuid, 86400 * 30);
            return bridgeUuid;

        } catch (BridgeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BridgeApiException("Erreur création utilisateur Bridge", e);
        }
    }

    private String fetchBridgeUserByExternalId(String externalUserId) {
        try {
            String encoded = URLEncoder.encode(externalUserId, StandardCharsets.UTF_8);
            HttpRequest req = appRequest("/v3/aggregation/users?external_user_id=" + encoded)
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BridgeApiException("Get user failed: " + resp.statusCode());
            }

            JsonNode json = objectMapper.readTree(resp.body());
            JsonNode resources = json.has("resources") ? json.get("resources") : json;
            if (resources.isArray() && !resources.isEmpty()) {
                String bridgeUuid = resources.get(0).get("uuid").asText();
                redisCache.set("bridge:user:" + externalUserId, bridgeUuid, 86400 * 30);
                return bridgeUuid;
            }
            throw new BridgeApiException("Bridge user not found for externalUserId=" + externalUserId);
        } catch (BridgeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BridgeApiException("Erreur récupération utilisateur Bridge", e);
        }
    }

    // ── 2. User authorization token ───────────────────────────────

    /**
     * Obtains a short-lived user access token (valid ~30 minutes).
     * Call this before any user-scoped API request.
     *
     * @param bridgeUserUuid the Bridge UUID of the user
     * @return Bearer access token
     */
    public String getUserToken(String bridgeUserUuid) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("user_uuid", bridgeUserUuid));
            HttpRequest req = appRequest("/v3/aggregation/authorization/token")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 201) {
                throw new BridgeApiException("Auth token failed: " + resp.statusCode() + " — " + resp.body());
            }

            JsonNode json = objectMapper.readTree(resp.body());
            return json.get("access_token").asText();

        } catch (BridgeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BridgeApiException("Erreur obtention token Bridge", e);
        }
    }

    // ── 3. Connect session ────────────────────────────────────────

    public record ConnectSession(String redirectUrl, String context) {}

    /**
     * Creates a Bridge connect session for the user.
     * Bridge will redirect to {@code bridge.redirect-url?context={context}} after the user connects.
     *
     * @param userToken  user access token
     * @param userEmail  the FlowGuard user's email
     * @param context    opaque state string for CSRF protection (≤100 chars)
     * @return connect session with {@code redirectUrl} to show user
     */
    public ConnectSession createConnectSession(String userToken, String userEmail, String context) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("user_email", userEmail);
            payload.put("country_code", "fr");
            payload.put("callback_url", redirectUrl);
            payload.put("context", context);
            payload.put("account_types", "payment");

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest req = userRequest("/v3/aggregation/connect-sessions", userToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 201) {
                throw new BridgeApiException("Connect session failed: " + resp.statusCode() + " — " + resp.body());
            }

            JsonNode json = objectMapper.readTree(resp.body());
            String url = json.get("redirect_url").asText();
            String ctx = json.has("context") && !json.get("context").isNull()
                    ? json.get("context").asText() : context;
            return new ConnectSession(url, ctx);

        } catch (BridgeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BridgeApiException("Erreur création session Bridge Connect", e);
        }
    }

    // ── 4. Accounts ───────────────────────────────────────────────

    public record BridgeAccount(
            long id,
            String name,
            BigDecimal balance,
            String iban,
            String currencyCode,
            String providerName,
            String accountType,
            String lastRefresh) {}

    /**
     * Lists all aggregated accounts for the user.
     */
    public List<BridgeAccount> listAccounts(String userToken) {
        try {
            HttpRequest req = userRequest("/v3/aggregation/accounts?limit=50", userToken)
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BridgeApiException("List accounts failed: " + resp.statusCode() + " — " + resp.body());
            }

            JsonNode json = objectMapper.readTree(resp.body());
            JsonNode resources = json.has("resources") ? json.get("resources") : json;
            List<BridgeAccount> result = new ArrayList<>();

            for (JsonNode node : resources) {
                String typeName = "Unknown";
                if (node.has("type") && !node.get("type").isNull() && node.get("type").has("name")) {
                    typeName = node.get("type").get("name").asText();
                }
                String iban = node.has("iban") && !node.get("iban").isNull()
                        ? node.get("iban").asText() : "";
                String lastRefresh = node.has("last_refresh") && !node.get("last_refresh").isNull()
                        ? node.get("last_refresh").asText() : null;
                BigDecimal balance = node.has("balance") && !node.get("balance").isNull()
                        ? node.get("balance").decimalValue() : BigDecimal.ZERO;

                result.add(new BridgeAccount(
                        node.get("id").asLong(),
                        node.has("name") ? node.get("name").asText() : "",
                        balance,
                        iban,
                        node.has("currency_code") ? node.get("currency_code").asText() : "EUR",
                        node.has("provider_name") ? node.get("provider_name").asText() : "",
                        typeName,
                        lastRefresh));
            }
            return result;

        } catch (BridgeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BridgeApiException("Erreur récupération comptes Bridge", e);
        }
    }

    // ── 5. Transactions ───────────────────────────────────────────

    public record BridgeTransaction(
            long id,
            BigDecimal amount,
            String description,
            LocalDate date,
            long accountId,
            String currencyCode,
            boolean deleted) {}

    /**
     * Lists transactions for a specific Bridge account since {@code minDate}.
     */
    public List<BridgeTransaction> listTransactions(String userToken, long accountId, LocalDate minDate) {
        try {
            String path = "/v3/aggregation/transactions?limit=500&account_id=" + accountId
                    + (minDate != null ? "&min_date=" + minDate : "");
            HttpRequest req = userRequest(path, userToken).GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BridgeApiException("List transactions failed: " + resp.statusCode() + " — " + resp.body());
            }

            JsonNode json = objectMapper.readTree(resp.body());
            JsonNode resources = json.has("resources") ? json.get("resources") : json;
            List<BridgeTransaction> result = new ArrayList<>();

            for (JsonNode node : resources) {
                LocalDate date = null;
                if (node.has("date") && !node.get("date").isNull()) {
                    try { date = LocalDate.parse(node.get("date").asText()); } catch (Exception ignored) {}
                }
                String desc = "";
                if (node.has("cleaned_description") && !node.get("cleaned_description").isNull()) {
                    desc = node.get("cleaned_description").asText();
                } else if (node.has("description") && !node.get("description").isNull()) {
                    desc = node.get("description").asText();
                }
                BigDecimal amount = node.has("amount") && !node.get("amount").isNull()
                        ? node.get("amount").decimalValue() : BigDecimal.ZERO;
                boolean deleted = node.has("is_deleted") && node.get("is_deleted").asBoolean();

                result.add(new BridgeTransaction(
                        node.get("id").asLong(),
                        amount,
                        desc,
                        date,
                        accountId,
                        node.has("currency_code") ? node.get("currency_code").asText() : "EUR",
                        deleted));
            }
            return result;

        } catch (BridgeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new BridgeApiException("Erreur récupération transactions Bridge", e);
        }
    }

    // ── 6. Transaction categorization ────────────────────────────

    /**
     * Simple rule-based categorization of a transaction description.
     * Returns a {@link com.flowguard.domain.TransactionEntity.TransactionCategory} name.
     */
    public String categorize(String description) {
        if (description == null) return "AUTRE";
        String l = description.toLowerCase(Locale.FRENCH);
        if (l.matches(".*(loyer|bail|foncier|habitat|immo).*"))                  return "LOYER";
        if (l.matches(".*(salaire|paie|virement employeur|traitement).*"))       return "SALAIRE";
        if (l.matches(".*(carrefour|leclerc|lidl|auchan|intermarché|monoprix|franprix|super u|casino|picard).*")) return "ALIMENTATION";
        if (l.matches(".*(sncf|ratp|navigo|blablacar|total|esso|bp|essence|parking|autoroute|uber).*")) return "TRANSPORT";
        if (l.matches(".*(netflix|spotify|amazon prime|disney|canal|deezer|youtube|apple).*")) return "ABONNEMENT";
        if (l.matches(".*(edf|engie|électricité|gaz|direct energie|total énergie).*")) return "ENERGIE";
        if (l.matches(".*(sfr|orange|free|bouygues|numericable).*"))             return "TELECOM";
        if (l.matches(".*(axa|mma|maaf|groupama|allianz|assurance|mutuelle).*")) return "ASSURANCE";
        if (l.matches(".*(urssaf|impôts|dgfip|tva|rsi|cipav|cotisation).*"))     return "CHARGES_FISCALES";
        if (l.matches(".*(honoraires|prestation|facture|règlement client).*"))   return "CLIENT_PAYMENT";
        return "AUTRE";
    }

    // ── Exception ─────────────────────────────────────────────────

    /**
     * Triggered by webhook: syncs all transactions for the given Bridge item.
     * Delegates to AccountService for persistence.
     */
    public void syncTransactionsForItem(String bridgeItemId) {
        LOG.infof("Webhook-triggered sync for Bridge item=%s", bridgeItemId);
        // Item-level sync is driven by AccountService which owns the user tokens
        // Emit a Redis event that AccountService's scheduled job will honour
        redisCache.set("bridge:sync_requested:" + bridgeItemId, "1", 300);
    }

    /**
     * Triggered by webhook: syncs account balances for the given Bridge item.
     */
    public void syncAccountBalances(String bridgeItemId) {
        LOG.infof("Webhook-triggered balance sync for Bridge item=%s", bridgeItemId);
        redisCache.set("bridge:balance_sync_requested:" + bridgeItemId, "1", 300);
    }

    // ── Exception ─────────────────────────────────────────────────

    public static class BridgeApiException extends RuntimeException {
        public BridgeApiException(String message) { super(message); }
        public BridgeApiException(String message, Throwable cause) { super(message, cause); }
    }
}
