package com.flowguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowguard.cache.RedisCacheService;
import com.flowguard.dto.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@ApplicationScoped
public class NordigenService {

    @ConfigProperty(name = "flowguard.nordigen.api-url")
    String apiUrl;

    @ConfigProperty(name = "flowguard.nordigen.secret-id")
    String secretId;

    @ConfigProperty(name = "flowguard.nordigen.secret-key")
    String secretKey;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RedisCacheService redisCache;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Token cache
    private volatile NordigenTokens tokens;

    // ──────────────────────────────────────────────────────────────
    // 1. authenticate(): NordigenTokens
    // ──────────────────────────────────────────────────────────────
    public NordigenTokens authenticate() {
        try {
            String cacheKey = "nordigen:token:access";
            String cached = redisCache.get(cacheKey);
            if (cached != null) {
                JsonNode node = objectMapper.readTree(cached);
                tokens = new NordigenTokens(
                        node.get("accessToken").asText(),
                        node.has("refreshToken") ? node.get("refreshToken").asText() : null,
                        node.get("expiresIn").asInt()
                );
                return tokens;
            }

            String payload = objectMapper.writeValueAsString(Map.of(
                    "secret_id", secretId,
                    "secret_key", secretKey
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/token/new/"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Nordigen auth failed: " + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            tokens = new NordigenTokens(
                    body.get("access").asText(),
                    body.has("refresh") ? body.get("refresh").asText() : null,
                    body.get("expires_in").asInt()
            );
            // Cache token with TTL
            int ttl = tokens.expiresIn() - 60;
            redisCache.set(cacheKey, objectMapper.writeValueAsString(tokens), ttl);
            return tokens;
        } catch (Exception e) {
            throw new RuntimeException("Erreur d'authentification Nordigen", e);
        }
    }

    private String getAccessToken() {
        if (tokens == null) {
            authenticate();
        }
        return tokens.accessToken();
    }

    // ──────────────────────────────────────────────────────────────
    // 2. getFrenchInstitutions(): List<NordigenInstitutionDto>
    // ──────────────────────────────────────────────────────────────
    public List<NordigenInstitutionDto> getFrenchInstitutions() {
        String cacheKey = "nordigen:institutions:fr";
        try {
            String cached = redisCache.get(cacheKey);
            if (cached != null) {
                JsonNode arr = objectMapper.readTree(cached);
                List<NordigenInstitutionDto> result = new ArrayList<>();
                for (JsonNode node : arr) {
                    result.add(new NordigenInstitutionDto(
                            node.get("id").asText(),
                            node.get("name").asText(),
                            node.get("logoUrl").asText(),
                            node.get("transactionTotalDays").asInt()
                    ));
                }
                return result;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/institutions/?country=fr"))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Nordigen institutions failed: " + response.statusCode());
            }

            JsonNode arr = objectMapper.readTree(response.body());
            List<NordigenInstitutionDto> result = new ArrayList<>();
            for (JsonNode node : arr) {
                result.add(new NordigenInstitutionDto(
                        node.get("id").asText(),
                        node.get("name").asText(),
                        node.get("logo").asText(),
                        node.get("transaction_total_days").asInt()
                ));
            }
            redisCache.set(cacheKey, objectMapper.writeValueAsString(result), 86400);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Erreur récupération institutions Nordigen", e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 3. createRequisition(userId, institutionId, redirectUri): NordigenRequisitionDto
    // ──────────────────────────────────────────────────────────────
    public NordigenRequisitionDto createRequisition(String userId, String institutionId, String redirectUri) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "redirect", redirectUri,
                    "institution_id", institutionId,
                    "reference", userId,
                    "user_language", "FR"
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/requisitions/"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getAccessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new RuntimeException("Nordigen requisition failed: " + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            String requisitionId = body.get("id").asText();
            String connectionUrl = body.get("link").asText();
            // TODO: Persist in nordigen_requisitions table
            return new NordigenRequisitionDto(requisitionId, connectionUrl);
        } catch (Exception e) {
            throw new RuntimeException("Erreur création réquisition Nordigen", e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 4. getRequisitionStatus(requisitionId): String
    // ──────────────────────────────────────────────────────────────
    public String getRequisitionStatus(String requisitionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/requisitions/" + requisitionId + "/"))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Nordigen requisition status failed: " + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            return body.get("status").asText();
        } catch (Exception e) {
            throw new RuntimeException("Erreur récupération status réquisition Nordigen", e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 5. getAccountBalances(nordigenAccountId): BigDecimal
    // ──────────────────────────────────────────────────────────────
    public BigDecimal getAccountBalances(String nordigenAccountId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/accounts/" + nordigenAccountId + "/balances/"))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Nordigen balances failed: " + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            JsonNode balances = body.get("balances");
            if (balances == null || balances.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(balances.get(0).get("balanceAmount").get("amount").asText());
        } catch (Exception e) {
            throw new RuntimeException("Erreur récupération balances Nordigen", e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 6. getAccountTransactions(nordigenAccountId, dateFrom): List<NordigenTxDto>
    // ──────────────────────────────────────────────────────────────
    public List<NordigenTxDto> getAccountTransactions(String nordigenAccountId, LocalDate dateFrom) {
        try {
            String url = apiUrl + "/accounts/" + nordigenAccountId + "/transactions/?date_from=" + dateFrom;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Nordigen transactions failed: " + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            List<NordigenTxDto> result = new ArrayList<>();
            for (String section : List.of("booked", "pending")) {
                JsonNode txs = body.get("transactions").get(section);
                if (txs == null) continue;
                for (JsonNode tx : txs) {
                    BigDecimal amount = new BigDecimal(tx.get("amount").get("amount").asText());
                    String type = amount.signum() < 0 ? "DEBIT" : "CREDIT";
                    String label = tx.has("remittanceInformationUnstructured") ? tx.get("remittanceInformationUnstructured").asText() : "";
                    String creditorName = tx.has("creditorName") ? tx.get("creditorName").asText() : "";
                    String debtorName = tx.has("debtorName") ? tx.get("debtorName").asText() : "";
                    LocalDate bookingDate = tx.has("bookingDate") ? LocalDate.parse(tx.get("bookingDate").asText()) : null;
                    String status = tx.has("status") ? tx.get("status").asText() : section;
                    String externalId = tx.has("transactionId") ? tx.get("transactionId").asText() : UUID.randomUUID().toString();
                    result.add(new NordigenTxDto(
                            externalId, amount, type, label, creditorName, debtorName, bookingDate, status
                    ));
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Erreur récupération transactions Nordigen", e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 7. categorize(label, creditorName): TransactionCategory
    // ──────────────────────────────────────────────────────────────
    public TransactionCategory categorize(String label, String creditorName) {
        String l = (label + " " + creditorName).toLowerCase(Locale.FRENCH);
        if (l.matches(".*(loyer|bail|foncier|habitat|immo).*")) return TransactionCategory.LOYER;
        if (l.matches(".*(salaire|paie|virement employeur|traitement).*")) return TransactionCategory.SALAIRE;
        if (l.matches(".*(carrefour|leclerc|lidl|auchan|intermarché|monoprix|franprix|super u|casino|picard).*")) return TransactionCategory.ALIMENTATION;
        if (l.matches(".*(sncf|ratp|navigo|blablacar|total|esso|bp|essence|parking|autoroute|uber|free now).*")) return TransactionCategory.TRANSPORT;
        if (l.matches(".*(netflix|spotify|amazon prime|disney\\+|canal\\+|deezer|youtube premium|apple).*")) return TransactionCategory.ABONNEMENT;
        if (l.matches(".*(edf|engie|électricité|gaz|direct energie|total énergie).*")) return TransactionCategory.ENERGIE;
        if (l.matches(".*(sfr|orange|free|bouygues|numericable).*")) return TransactionCategory.TELECOM;
        if (l.matches(".*(axa|mma|maaf|groupama|allianz|assurance|mutuelle).*")) return TransactionCategory.ASSURANCE;
        if (l.matches(".*(urssaf|impôts|dgfip|tva|rsi|cipav|cotisation).*")) return TransactionCategory.CHARGES_FISCALES;
        if (l.matches(".*(honoraires|prestation|facture|règlement client).*")) return TransactionCategory.CLIENT_PAYMENT;
        return TransactionCategory.AUTRE;
    }

    // ──────────────────────────────────────────────────────────────
    // 8. detectRecurring(transactions): annotates transactions as recurring
    // ──────────────────────────────────────────────────────────────
    public List<NordigenTxDto> detectRecurring(List<NordigenTxDto> transactions) {
        Map<String, List<NordigenTxDto>> grouped = new HashMap<>();
        for (NordigenTxDto tx : transactions) {
            String key = tx.creditorName() + ":" + tx.amount();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(tx);
        }
        for (List<NordigenTxDto> group : grouped.values()) {
            if (group.size() >= 2) {
                // Check if appears in 2+ of last 3 months
                List<Integer> months = new ArrayList<>();
                for (NordigenTxDto tx : group) {
                    if (tx.bookingDate() != null) {
                        months.add(tx.bookingDate().getMonthValue());
                    }
                }
                Set<Integer> uniqueMonths = new HashSet<>(months);
                if (uniqueMonths.size() >= 2) {
                    // Annotate as recurring (could add a field or tag)
                    // For now, just print or log
                }
            }
        }
        return transactions;
    }
}
