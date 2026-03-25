package com.flowguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Uses a local Llama model (served via Ollama) to intelligently normalize bank statement data.
 * <p>
 * Two capabilities:
 * <ol>
 *   <li><b>Column detection</b> — given a CSV/XLSX header + sample rows, returns
 *       a column-index→semantic-role mapping ({@code date, label, amount, debit, credit, type, ignore}).
 *       This is done with a single inference call; all subsequent row-parsing stays local.</li>
 *   <li><b>PDF text extraction</b> — given raw text from PDFBox, asks the LLM to identify
 *       and return every transaction as a structured JSON array.</li>
 * </ol>
 *
 * <h3>Configuration</h3>
 * <pre>
 *   flowguard.ai.endpoint  — Ollama (or any OpenAI-compatible) chat/completions URL
 *                            Set to empty string to disable AI and fall back to heuristics.
 *   flowguard.ai.model     — model name (default llama3.2:3b)
 *   flowguard.ai.timeout-seconds — HTTP timeout (default 60)
 * </pre>
 *
 * No API key is required when using Ollama (self-hosted). Works with any
 * OpenAI-compatible endpoint that accepts {@code POST /v1/chat/completions}.
 */
@ApplicationScoped
public class AiBankNormalizerService {

    private static final Logger LOG = Logger.getLogger(AiBankNormalizerService.class);

    @ConfigProperty(name = "flowguard.ai.endpoint",
                    defaultValue = "http://ollama:11434/v1/chat/completions")
    String endpoint;

    @ConfigProperty(name = "flowguard.ai.model", defaultValue = "llama3.2:3b")
    String model;

    @ConfigProperty(name = "flowguard.ai.timeout-seconds", defaultValue = "60")
    int timeoutSeconds;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /** Returns true when an LLM endpoint is configured (endpoint is non-blank). */
    public boolean isEnabled() {
        return endpoint != null && !endpoint.isBlank();
    }

    // ──────────────────────────────────────────────────────────────────
    // Column mapping for CSV / XLSX
    // ──────────────────────────────────────────────────────────────────

    /**
     * Given column headers and a few sample data rows from a CSV or XLSX file,
     * asks the LLM to map each column index to one of these semantic roles:
     * <ul>
     *  <li>{@code date}    — transaction date</li>
     *  <li>{@code label}   — description / payee / motion</li>
     *  <li>{@code amount}  — single signed or unsigned amount column</li>
     *  <li>{@code debit}   — debit-only / withdrawal column</li>
     *  <li>{@code credit}  — credit-only / deposit column</li>
     *  <li>{@code type}    — explicit DEBIT/CREDIT/D/C type column</li>
     *  <li>{@code balance} — running balance (will be ignored)</li>
     *  <li>{@code ignore}  — not useful</li>
     * </ul>
     *
     * Returns an empty map if AI is disabled or the call fails.
     * Callers must always provide a heuristic fallback.
     */
    public Map<Integer, String> detectColumnMapping(List<String> headers,
                                                     List<List<String>> sampleRows) {
        if (!isEnabled() || headers.isEmpty()) return Map.of();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, sampleRows.size()); i++) {
            sb.append("  Ligne ").append(i + 1).append(": ")
              .append(String.join(" | ", sampleRows.get(i))).append("\n");
        }

        String prompt = """
                Tu es un expert en comptabilité bancaire française.
                Voici les en-têtes d'un fichier relevé bancaire (CSV ou Excel) :
                  Colonnes : [%s]
                Exemples de données :
                %s
                
                Associe chaque colonne (index 0-based) à un rôle parmi :
                  "date"    = date de l'opération
                  "label"   = libellé / description / bénéficiaire / motif
                  "amount"  = montant unique (positif = entrée, négatif = sortie)
                  "debit"   = montant débit uniquement (sorties)
                  "credit"  = montant crédit uniquement (entrées)
                  "type"    = type explicite (DEBIT/CREDIT/D/C/+/-)
                  "balance" = solde courant (à ignorer)
                  "ignore"  = non pertinent

                Réponds UNIQUEMENT avec un objet JSON.
                Exemple : {"0":"date","1":"label","2":"debit","3":"credit"}
                """.formatted(String.join(", ", headers), sb.toString());

        try {
            String response = callLlm(prompt, 300);
            String json = extractJsonObject(response);
            JsonNode node = mapper.readTree(json);
            Map<Integer, String> mapping = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> {
                try {
                    int idx = Integer.parseInt(e.getKey());
                    String role = e.getValue().asText().toLowerCase().trim();
                    mapping.put(idx, role);
                } catch (NumberFormatException ignored) {}
            });
            LOG.infof("AI column mapping result: %s", mapping);
            return mapping;
        } catch (Exception e) {
            LOG.warnf("AI column mapping failed (%s), falling back to heuristic", e.getMessage());
            return Map.of();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Text extraction for PDF
    // ──────────────────────────────────────────────────────────────────

    /**
     * Given raw text extracted from a bank statement PDF (any bank, any layout),
     * asks the LLM to identify and return all transactions as a JSON array.
     * <p>
     * The text is truncated to {@code ~8 000} characters to control API cost.
     * For multi-page statements this may miss the last pages; the heuristic
     * fallback in {@link BankStatementParserService} handles that case.
     * </p>
     */
    public List<BankStatementParserService.ParsedRow> extractFromText(String rawText) {
        if (!isEnabled()) return List.of();

        // Limit to ~8 000 chars (≈ 2 000 tokens) to keep cost low
        String text = rawText.length() > 8000 ? rawText.substring(0, 8000) : rawText;

        String prompt = """
                Tu es un expert en analyse de relevés bancaires.
                Le texte suivant provient d'un relevé bancaire (extrait via PDF). 
                Extrais TOUTES les transactions bancaires.

                Pour chaque transaction, retourne un objet JSON avec :
                  "date"   : date ISO "YYYY-MM-DD"
                  "label"  : libellé de l'opération (max 200 caractères, texte propre)
                  "amount" : montant en valeur absolue (nombre décimal positif)
                  "type"   : "DEBIT" (sortie d'argent) ou "CREDIT" (entrée d'argent)

                Réponds UNIQUEMENT avec un tableau JSON. Aucun texte, aucun markdown, aucun code block.

                Texte du relevé :
                ---
                %s
                ---
                """.formatted(text);

        List<BankStatementParserService.ParsedRow> results = new ArrayList<>();
        try {
            String response = callLlm(prompt, 3000);
            String json = extractJsonArray(response);
            JsonNode arr = mapper.readTree(json);
            if (!arr.isArray()) return List.of();

            for (JsonNode item : arr) {
                try {
                    LocalDate date = parseDate(item.path("date").asText(""));
                    if (date == null) continue;

                    String label = item.path("label").asText("Opération").trim();
                    if (label.isBlank()) label = "Opération";
                    if (label.length() > 200) label = label.substring(0, 200);

                    BigDecimal amount = new BigDecimal(
                            item.path("amount").asText("0").replace(",", "."));
                    String type = item.path("type").asText("DEBIT").trim().toUpperCase();
                    if (!type.equals("CREDIT")) type = "DEBIT";

                    results.add(new BankStatementParserService.ParsedRow(
                            date, label, amount.abs(), type, null));
                } catch (Exception ignored) {}
            }
            LOG.infof("AI PDF extraction: %d transactions found", results.size());
        } catch (Exception e) {
            LOG.warnf("AI PDF extraction failed (%s), falling back to regex", e.getMessage());
        }
        return results;
    }

    // ──────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * Calls the configured LLM endpoint and returns the raw text content of
     * the first assistant message choice.
     */
    private String callLlm(String userPrompt, int maxTokens) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "temperature", 0,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        ));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                // Ollama ignores the Authorization header; kept for compatibility
                // with services that do require a Bearer token (set via env var).
                .header("Authorization", "Bearer ollama")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            String body2 = resp.body();
            throw new RuntimeException("LLM API error " + resp.statusCode() + ": "
                    + body2.substring(0, Math.min(300, body2.length())));
        }
        JsonNode respNode = mapper.readTree(resp.body());
        return respNode.at("/choices/0/message/content").asText("");
    }

    /** Strips markdown fences and returns the first JSON object found. */
    private String extractJsonObject(String text) {
        text = stripMarkdownFence(text);
        int start = text.indexOf('{');
        if (start < 0) throw new IllegalArgumentException("No JSON object in: " + text.substring(0, Math.min(100, text.length())));
        int end = text.lastIndexOf('}');
        return text.substring(start, end + 1);
    }

    /** Strips markdown fences and returns the first JSON array found. */
    private String extractJsonArray(String text) {
        text = stripMarkdownFence(text);
        int start = text.indexOf('[');
        if (start >= 0) {
            int end = text.lastIndexOf(']');
            if (end > start) return text.substring(start, end + 1);
        }
        // Try object array wrapped in object
        return extractJsonObject(text);
    }

    private String stripMarkdownFence(String text) {
        text = text.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence    = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return text;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy")
        );
        for (DateTimeFormatter f : fmts) {
            try { return LocalDate.parse(raw.trim(), f); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
