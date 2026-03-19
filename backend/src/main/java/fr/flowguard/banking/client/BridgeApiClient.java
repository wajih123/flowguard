package fr.flowguard.banking.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridge API v3 aggregation HTTP client.
 *
 * Auth model:
 *  - App-level: client-id + client-secret headers on every request
 *  - User-level: above + Authorization: Bearer {userAccessToken}
 */
@ApplicationScoped
public class BridgeApiClient {

    private static final Logger LOG = Logger.getLogger(BridgeApiClient.class);
    private static final String BASE_URL = "https://api.bridgeapi.io";
    private static final String BRIDGE_VERSION = "2025-01-15";

    @ConfigProperty(name = "bridge.client-id")
    String clientId;

    @ConfigProperty(name = "bridge.client-secret")
    String clientSecret;

    private HttpClient http;
    private ObjectMapper mapper;

    @PostConstruct
    void init() {
        http = HttpClient.newHttpClient();
        mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        LOG.infof("[Bridge] Client init - clientId=%s version=%s", clientId, BRIDGE_VERSION);
    }

    // ── 1. User management ────────────────────────────────────────────────────

    /** Creates a Bridge user identified by our FlowGuard UUID. */
    public CreateUserResponse createUser(String externalUserId) {
        LOG.infof("[Bridge] createUser externalUserId=%s", externalUserId);
        Map<String, String> payload = Map.of("external_user_id", externalUserId);
        HttpRequest req = appRequest("/v3/aggregation/users")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json(payload)))
            .build();
        return call(req, CreateUserResponse.class);
    }

    /** Fetches an existing Bridge user by our external ID. */
    public GetUsersResponse getUserByExternalId(String externalUserId) {
        LOG.infof("[Bridge] getUserByExternalId=%s", externalUserId);
        HttpRequest req = appRequest("/v3/aggregation/users?external_user_id=" + externalUserId)
            .GET()
            .build();
        return call(req, GetUsersResponse.class);
    }

    // ── 2. User access token ──────────────────────────────────────────────────

    /** Obtains a short-lived access token for a Bridge user (valid ~30 min). */
    public TokenResponse getUserToken(String bridgeUserUuid) {
        LOG.infof("[Bridge] getUserToken bridgeUserUuid=%s", bridgeUserUuid);
        Map<String, String> payload = Map.of("user_uuid", bridgeUserUuid);
        HttpRequest req = appRequest("/v3/aggregation/authorization/token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json(payload)))
            .build();
        return call(req, TokenResponse.class);
    }

    // ── 3. Connect session ────────────────────────────────────────────────────

    /** Creates a connect session; returns redirect_url + context. */
    public ConnectSessionResponse createConnectSession(
            String userToken, String userEmail, String context, String callbackUrl) {
        LOG.infof("[Bridge] createConnectSession userEmail=%s context=%s", userEmail, context);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callback_url", callbackUrl);
        payload.put("context", context);
        payload.put("country_code", "FR");
        payload.put("user_email", userEmail);
        HttpRequest req = userRequest("/v3/aggregation/connect-sessions", userToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json(payload)))
            .build();
        return call(req, ConnectSessionResponse.class);
    }

    // ── 4. Accounts ───────────────────────────────────────────────────────────

    public AccountsListResponse listAccounts(String userToken) {
        LOG.infof("[Bridge] listAccounts");
        HttpRequest req = userRequest("/v3/aggregation/accounts?limit=50", userToken)
            .GET()
            .build();
        return call(req, AccountsListResponse.class);
    }

    // ── Request builders ──────────────────────────────────────────────────────

    private HttpRequest.Builder appRequest(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .header("client-id", clientId)
            .header("client-secret", clientSecret)
            .header("Bridge-Version", BRIDGE_VERSION)
            .header("accept", "application/json");
    }

    private HttpRequest.Builder userRequest(String path, String userToken) {
        return appRequest(path)
            .header("Authorization", "Bearer " + userToken);
    }

    private <T> T call(HttpRequest req, Class<T> responseType) {
        try {
            LOG.infof("[Bridge] --> %s %s", req.method(), req.uri());
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            LOG.infof("[Bridge] <-- status=%d body=%s", resp.statusCode(), resp.body());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                LOG.errorf("[Bridge] ERREUR HTTP %d: %s", resp.statusCode(), resp.body());
                throw new BridgeApiException(resp.statusCode(), resp.body());
            }
            return mapper.readValue(resp.body(), responseType);
        } catch (BridgeApiException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "[Bridge] Exception reseau: %s", e.getMessage());
            throw new RuntimeException("Bridge API call failed: " + e.getMessage(), e);
        }
    }

    private String json(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    // ── Exception ─────────────────────────────────────────────────────────────

    public static class BridgeApiException extends RuntimeException {
        public final int statusCode;
        public final String responseBody;
        public BridgeApiException(int statusCode, String responseBody) {
            super("Bridge API error " + statusCode + ": " + responseBody);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
    }

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateUserResponse {
        public String uuid;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GetUsersResponse {
        public List<UserResource> resources;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class UserResource {
            public String uuid;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenResponse {
        public String access_token;
        public String expires_at;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConnectSessionResponse {
        public String redirect_url;   // v3 standard
        public String connect_url;    // some sandbox variants
        public String url;            // v2 legacy fallback
        public String context;
        public String getConnectUrl() {
            if (redirect_url != null && !redirect_url.isBlank()) return redirect_url;
            if (connect_url  != null && !connect_url.isBlank())  return connect_url;
            return url;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountsListResponse {
        public List<AccountDto> resources;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class AccountDto {
            public Long id;
            public String name;
            public Double balance;
            public String currency_code;
            public String iban;
            public String provider_name;
            public String type;
        }
    }

    // ── 5. Transactions ──────────────────────────────────────────────────────────

    /** Lists all transactions for a Bridge account (up to 500 per page). */
    public TransactionsListResponse listTransactions(String userToken, long bridgeAccountId) {
        LOG.infof("[Bridge] listTransactions bridgeAccountId=%d", bridgeAccountId);
        HttpRequest req = userRequest(
                "/v3/aggregation/transactions?account_id=" + bridgeAccountId + "&limit=500",
                userToken)
            .GET()
            .build();
        return call(req, TransactionsListResponse.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionsListResponse {
        public List<TransactionDto> resources;
        public PaginationDto pagination;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class TransactionDto {
            public Long id;
            public String clean_description;
            public String provider_description;
            public Double amount;
            public String date;
            public String booking_date;
            public String currency_code;
            public Boolean deleted;
            public Integer category_id;
            public String operation_type;
            public Long account_id;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class PaginationDto {
            public String next_uri;
        }
    }

}
