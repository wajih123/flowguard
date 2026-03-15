package com.flowguard.health;

import io.smallrye.health.api.HealthType;
import io.smallrye.health.registry.HealthRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Readiness health check for the Bridge API.
 *
 * <p>Verified by: HTTP GET to Bridge's base URL with a 3-second timeout.
 * A non-5xx response (even 401/403) means Bridge is reachable.
 */
@Readiness
@ApplicationScoped
public class BridgeApiHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(BridgeApiHealthCheck.class);

    @ConfigProperty(name = "bridge.api-url", defaultValue = "https://api.bridgeapi.io")
    String bridgeApiUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public HealthCheckResponse call() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(bridgeApiUrl + "/v3/aggregation/status"))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();

            // 200–499 = Bridge is up (even 401/403 = reachable)
            if (status < 500) {
                return HealthCheckResponse.builder()
                        .name("bridge-api")
                        .up()
                        .withData("status", status)
                        .build();
            } else {
                return HealthCheckResponse.builder()
                        .name("bridge-api")
                        .down()
                        .withData("status", status)
                        .withData("reason", "Bridge API returned server error")
                        .build();
            }
        } catch (Exception e) {
            LOG.warnf("Bridge API health check failed: %s", e.getMessage());
            return HealthCheckResponse.builder()
                    .name("bridge-api")
                    .down()
                    .withData("reason", e.getMessage())
                    .build();
        }
    }
}
