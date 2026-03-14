package com.flowguard.service;

import com.flowguard.dto.TreasuryForecastDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class TreasuryService {

    @ConfigProperty(name = "flowguard.ml-service.url")
    String mlServiceUrl;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public TreasuryForecastDto getForecast(UUID userId, int horizonDays) {
        try {
            String url = mlServiceUrl + "/predict?user_id=" + userId + "&horizon_days=" + horizonDays;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("ML service returned status " + response.statusCode());
            }

            return objectMapper.readValue(response.body(), TreasuryForecastDto.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Forecast request interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la prévision de trésorerie", e);
        }
    }

    public TreasuryForecastDto getCachedForecast(UUID userId, int horizonDays) {
        // In a real implementation this would check Redis cache first
        return getForecast(userId, horizonDays);
    }
}
