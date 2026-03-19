package fr.flowguard.banking.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@ApplicationScoped
public class MlServiceClient {

    private static final Logger LOG = Logger.getLogger(MlServiceClient.class);

    @ConfigProperty(name = "ml.service.url", defaultValue = "http://localhost:8000")
    String mlUrl;

    private HttpClient http;
    private ObjectMapper mapper;

    @PostConstruct
    void init() {
        http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        mapper = new ObjectMapper();
    }

    /**
     * Calls ML service POST /v2/predict with real transaction data.
     *
     * @param accountId    FlowGuard DB account UUID
     * @param transactions list of {date, amount, balance, description, category}
     * @param horizon      prediction horizon in days
     * @return prediction map or null if service unavailable
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> predict(String accountId, List<Map<String, Object>> transactions, int horizon) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("account_id", accountId);
            body.put("transactions", transactions);
            body.put("horizon", horizon);

            String json = mapper.writeValueAsString(body);
            LOG.infof("[ML] POST /v2/predict accountId=%s txCount=%d horizon=%d",
                accountId, transactions.size(), horizon);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(mlUrl + "/v2/predict"))
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(60))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                LOG.infof("[ML] Prediction OK for accountId=%s", accountId);
                return mapper.readValue(resp.body(), Map.class);
            }
            LOG.errorf("[ML] HTTP %d: %.500s", resp.statusCode(), resp.body());
            return null;
        } catch (java.net.ConnectException e) {
            LOG.warnf("[ML] Service unreachable at %s — start the ML service", mlUrl);
            return null;
        } catch (Exception e) {
            LOG.errorf(e, "[ML] Exception: %s", e.getMessage());
            return null;
        }
    }

    public boolean isHealthy() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(mlUrl + "/v2/health"))
                .GET().timeout(Duration.ofSeconds(3)).build();
            return http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
