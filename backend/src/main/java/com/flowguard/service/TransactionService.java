package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.TransactionDto;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@ApplicationScoped
public class TransactionService {

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    BankStatementParserService parserService;

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

        int imported = 0;
        int skipped = 0;

        for (BankStatementParserService.ParsedRow row : rows) {
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
                        .build();
                transactionRepository.persist(tx);
                imported++;
            } catch (Exception e) {
                skipped++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("format", parserService.detectFormat(originalFilename, new byte[0]));
        return result;
    }
}
