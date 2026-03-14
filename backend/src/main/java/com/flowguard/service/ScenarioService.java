package com.flowguard.service;

import com.flowguard.dto.ScenarioRequest;
import com.flowguard.dto.ScenarioResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class ScenarioService {

    @ConfigProperty(name = "flowguard.ml-service.url")
    String mlServiceUrl;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ScenarioResponse runScenario(UUID userId, ScenarioRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(new MlScenarioPayload(
                    userId.toString(),
                    request.type(),
                    request.amount().doubleValue(),
                    request.delayDays(),
                    request.description()
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(mlServiceUrl + "/scenario"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("ML service returned status " + response.statusCode());
            }

            return objectMapper.readValue(response.body(), ScenarioResponse.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Scenario request interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la simulation", e);
        }
    }

    private record MlScenarioPayload(
            String userId,
            String type,
            double amount,
            int delayDays,
            String description
    ) {}
}
