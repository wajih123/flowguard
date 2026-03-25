package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.TransactionDto;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class TransactionService {

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    BankStatementParserService parserService;

    /** Injected for batch flush/clear during large imports (Gap 7). */
    @Inject
    EntityManager em;

    @ConfigProperty(name = "flowguard.ml-service.url")
    String mlServiceUrl;

    private static final int BATCH_SIZE = 100;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<TransactionDto> getByAccountId(UUID accountId) {
        return transactionRepository.findByAccountId(accountId).stream()
                .map(TransactionDto::from)
                .toList();
    }

    public List<TransactionDto> getByAccountIdAndPeriod(UUID accountId, LocalDate from, LocalDate to) {
        return transactionRepository.findByAccountIdAndDateBetween(accountId, from, to).stream()
                .map(TransactionDto::from)
                .toList();
    }

    public List<TransactionDto> getByAccountIdAndCategory(UUID accountId,
            TransactionEntity.TransactionCategory category) {
        return transactionRepository.findByAccountIdAndCategory(accountId, category).stream()
                .map(TransactionDto::from)
                .toList();
    }

    public List<TransactionDto> getRecurringByAccountId(UUID accountId) {
        return transactionRepository.findRecurringByAccountId(accountId).stream()
                .map(TransactionDto::from)
                .toList();
    }

    /**
     * Vérifie que le compte appartient bien à l'utilisateur.
     * Must run inside a transaction so that the LAZY {@code user} association
     * can be navigated without triggering a LazyInitializationException.
     */
    @Transactional
    public void verifyAccountOwnership(UUID accountId, UUID userId) {
        AccountEntity account = AccountEntity.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Compte introuvable");
        }
        if (!account.getUser().getId().equals(userId)) {
            throw new SecurityException("Accès non autorisé à ce compte");
        }
    }

    /**
     * Imports transactions from a CSV file.
     * Expected columns (header required): date, label, amount, type
     * Amount must be a positive decimal; type must be DEBIT or CREDIT.
     * Rows with a parsing error are counted as skipped.
     */
    @Transactional
    public Map<String, Integer> importFromCsv(UUID accountId, File csvFile) {
        AccountEntity account = AccountEntity.findById(accountId);
        int imported = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), "UTF-8"))) {
            String headerLine = reader.readLine(); // skip header
            if (headerLine == null)
                return Map.of("imported", 0, "skipped", 0);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank())
                    continue;
                String[] cols = line.split(",", -1);
                if (cols.length < 4) {
                    skipped++;
                    continue;
                }
                try {
                    LocalDate date = LocalDate.parse(cols[0].trim());
                    String label = cols[1].trim();
                    BigDecimal amount = new BigDecimal(cols[2].trim().replace(" ", "").replace("€", "")).abs();
                    TransactionEntity.TransactionType type = TransactionEntity.TransactionType
                            .valueOf(cols[3].trim().toUpperCase());

                    // Deduplicate by date + label + amount to avoid re-importing
                    boolean exists = transactionRepository.existsByAccountIdDateLabelAmount(accountId, date, label,
                            amount);
                    if (exists) {
                        skipped++;
                        continue;
                    }

                    TransactionEntity.TransactionCategory category = BankStatementParserService
                            .inferCategoryFromLabel(label);
                    if (category == null)
                        category = TransactionEntity.TransactionCategory.AUTRE;

                    TransactionEntity tx = TransactionEntity.builder()
                            .account(account)
                            .date(date)
                            .label(label)
                            .amount(amount)
                            .type(type)
                            .category(category)
                            .isRecurring(false)
                            .build();
                    transactionRepository.persist(tx);
                    imported++;
                } catch (Exception e) {
                    skipped++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Erreur de lecture du fichier CSV", e);
        }

        return Map.of("imported", imported, "skipped", skipped);
    }

    /**
     * Imports transactions from a bank statement file in any supported format:
     * PDF, OFX, QIF, MT940, CFONB, XLSX, XLS, CSV.
     * <p>
     * Format is auto-detected from the original filename. Duplicate rows are
     * suppressed using the same date+label+amount deduplication as CSV import.
     * <p>
     * Gaps implemented here:
     * <ul>
     * <li>Gap 3 — import provenance (importSource, importBatchId, isHistorical,
     * balanceAfter)</li>
     * <li>Gap 6 — daily_balances view refreshed after persist (best-effort,
     * non-blocking)</li>
     * <li>Gap 7 — batch EntityManager flush every {@value #BATCH_SIZE} rows</li>
     * <li>Gap 8 — async POST /retrain to ML service when new data arrives</li>
     * </ul>
     */
    @Transactional
    public Map<String, Object> importFromStatement(UUID accountId, InputStream stream, String originalFilename) {
        AccountEntity account = AccountEntity.findById(accountId);
        if (account == null)
            throw new IllegalArgumentException("Compte introuvable");

        List<BankStatementParserService.ParsedRow> rows;
        try {
            rows = parserService.parse(originalFilename, stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de lire le relevé bancaire : " + e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("Erreur lors de l'analyse du fichier : " + e.getMessage());
        }

        // Gap 3: derive import provenance before the loop
        TransactionEntity.ImportSource importSource = detectImportSource(originalFilename);
        UUID batchId = UUID.randomUUID();
        LocalDate historicalCutoff = LocalDate.now().minusDays(90);

        int imported = 0;
        int skipped = 0;
        int total = 0;

        for (BankStatementParserService.ParsedRow row : rows) {
            total++;
            try {
                if (row.date() == null || row.amount() == null || row.label() == null) {
                    skipped++;
                    continue;
                }
                boolean exists = transactionRepository
                        .existsByAccountIdDateLabelAmount(accountId, row.date(), row.label(), row.amount());
                if (exists) {
                    skipped++;
                    continue;
                }

                TransactionEntity.TransactionType type = "CREDIT".equalsIgnoreCase(row.type())
                        ? TransactionEntity.TransactionType.CREDIT
                        : TransactionEntity.TransactionType.DEBIT;

                TransactionEntity.TransactionCategory category = BankStatementParserService
                        .inferCategoryFromLabel(row.label());
                if (category == null)
                    category = TransactionEntity.TransactionCategory.AUTRE;

                TransactionEntity tx = TransactionEntity.builder()
                        .account(account)
                        .date(row.date())
                        .label(row.label())
                        .amount(row.amount())
                        .type(type)
                        .category(category)
                        .isRecurring(false)
                        // Gap 3: provenance
                        .importSource(importSource)
                        .importBatchId(batchId)
                        .isHistorical(row.date().isBefore(historicalCutoff))
                        // Gap 2: bank-verified running balance from PDF/OFX
                        .balanceAfter(row.balanceAfter())
                        .build();
                transactionRepository.persist(tx);
                imported++;

                // Gap 7: batch flush to avoid JPA first-level cache overflow on large imports
                if (total % BATCH_SIZE == 0) {
                    em.flush();
                    em.clear();
                }
            } catch (Exception e) {
                skipped++;
            }
        }

        // Gap 6: refresh the materialised view so the ML service sees fresh data.
        // Uses non-CONCURRENT refresh (takes a brief exclusive lock — acceptable for
        // statement imports which are infrequent user-initiated operations).
        if (imported > 0) {
            try {
                em.createNativeQuery("REFRESH MATERIALIZED VIEW daily_balances").executeUpdate();
            } catch (Exception e) {
                // Non-fatal: the view will be refreshed on the next import or nightly job.
            }

            // Gap 8: notify the ML service to re-evaluate whether retraining is warranted.
            // Fire-and-forget — we do not block the HTTP response waiting for ML.
            final String url = mlServiceUrl;
            final int finalImported = imported;
            CompletableFuture.runAsync(() -> {
                try {
                    String body = String.format(
                            "{\"account_id\":\"%s\",\"new_transactions\":%d,\"batch_id\":\"%s\"}",
                            accountId, finalImported, batchId);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url + "/retrain"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(30))
                            .build();
                    httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignored) {
                    // ML service unavailable — import still succeeded, retrain deferred.
                }
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("format", parserService.detectFormat(originalFilename, new byte[0]));
        result.put("batchId", batchId.toString());
        return result;
    }

    /**
     * Maps a filename extension to an {@link TransactionEntity.ImportSource} enum
     * value.
     * Falls back to {@code BRIDGE_API} (which should never trigger from a file
     * upload —
     * callers can override if needed).
     */
    private static TransactionEntity.ImportSource detectImportSource(String filename) {
        if (filename == null)
            return TransactionEntity.ImportSource.MANUAL;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))
            return TransactionEntity.ImportSource.PDF;
        if (lower.endsWith(".ofx") || lower.endsWith(".qfx"))
            return TransactionEntity.ImportSource.OFX;
        if (lower.endsWith(".qif"))
            return TransactionEntity.ImportSource.OFX; // QIF handled by OFX source bucket
        if (lower.endsWith(".sta") || lower.endsWith(".mt940"))
            return TransactionEntity.ImportSource.MT940;
        if (lower.endsWith(".txt") && filename.toLowerCase().contains("cfonb"))
            return TransactionEntity.ImportSource.CFONB;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls"))
            return TransactionEntity.ImportSource.XLSX;
        if (lower.endsWith(".csv"))
            return TransactionEntity.ImportSource.CSV;
        return TransactionEntity.ImportSource.MANUAL;
    }
}
