package com.flowguard.health;

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
 * Readiness health check for the ML service.
 *
 * <p>Calls the ML service {@code /health} endpoint with a 5-second timeout.
 * Used by Kubernetes readiness probes and the Quarkus /health/ready endpoint.
 */
@Readiness
@ApplicationScoped
public class MlServiceHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(MlServiceHealthCheck.class);

    @ConfigProperty(name = "flowguard.ml-service.url", defaultValue = "http://localhost:8000")
    String mlServiceUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public HealthCheckResponse call() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServiceUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) {
                return HealthCheckResponse.builder()
                        .name("ml-service")
                        .up()
                        .withData("url", mlServiceUrl)
                        .build();
            } else {
                return HealthCheckResponse.builder()
                        .name("ml-service")
                        .down()
                        .withData("status", status)
                        .withData("body", response.body())
                        .build();
            }
        } catch (Exception e) {
            LOG.warnf("ML service health check failed: %s", e.getMessage());
            return HealthCheckResponse.builder()
                    .name("ml-service")
                    .down()
                    .withData("reason", e.getMessage())
                    .build();
        }
    }
}
