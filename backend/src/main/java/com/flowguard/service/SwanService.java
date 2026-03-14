package com.flowguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class SwanService {

    @ConfigProperty(name = "flowguard.swan.api-url")
    String apiUrl;

    @ConfigProperty(name = "flowguard.swan.client-id")
    String clientId;

    @ConfigProperty(name = "flowguard.swan.client-secret")
    String clientSecret;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile String accessToken;

    /**
     * Authentification OAuth2 auprès de Swan.
     */
    public String authenticate() {
        try {
            String body = "grant_type=client_credentials"
                    + "&client_id=" + clientId
                    + "&client_secret=" + clientSecret;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Swan auth failed: " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            this.accessToken = json.get("access_token").asText();
            return this.accessToken;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Swan auth interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Erreur d'authentification Swan", e);
        }
    }

    /**
     * Lance un onboarding Swan pour un utilisateur.
     */
    public JsonNode createOnboarding(String email, String firstName, String lastName, String companyName) {
        ensureAuthenticated();
        try {
            String graphql = """
                mutation {
                    onboardCompanyAccountHolder(input: {
                        email: "%s"
                        legalRepresentativePersonalAddress: {}
                        name: "%s"
                    }) {
                        ... on OnboardCompanyAccountHolderSuccessPayload {
                            onboarding { id statusInfo { status } }
                        }
                    }
                }
                """.formatted(email, companyName);

            String payload = objectMapper.writeValueAsString(new GraphqlRequest(graphql));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/partner/graphql"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Swan onboarding failed: " + response.statusCode());
            }

            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Swan onboarding interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Erreur d'onboarding Swan", e);
        }
    }

    /**
     * Récupère les détails d'un compte Swan.
     */
    public JsonNode getAccount(String swanAccountId) {
        ensureAuthenticated();
        try {
            String graphql = """
                query {
                    account(id: "%s") {
                        id
                        IBAN
                        BIC
                        balances { available { value currency } }
                        statusInfo { status }
                    }
                }
                """.formatted(swanAccountId);

            String payload = objectMapper.writeValueAsString(new GraphqlRequest(graphql));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/partner/graphql"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Swan getAccount failed: " + response.statusCode());
            }

            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Swan getAccount interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Erreur récupération compte Swan", e);
        }
    }

    private void ensureAuthenticated() {
        if (accessToken == null) {
            authenticate();
        }
    }

    record GraphqlRequest(String query) {}
}
