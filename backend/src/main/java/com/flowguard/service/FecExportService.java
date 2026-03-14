package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Export FEC (Fichier des Écritures Comptables) — format obligatoire
 * pour la transmission des données comptables à l'administration fiscale
 * française (article L47 A-I du LPF, norme NEF).
 *
 * Format : fichier texte tabulé (TSV) avec les 18 colonnes réglementaires.
 */
@ApplicationScoped
public class FecExportService {

    private static final DateTimeFormatter FEC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** FEC header — 18 mandatory columns per art. A47 A-1 du LPF */
    private static final String FEC_HEADER = String.join("\t",
            "JournalCode", "JournalLib", "EcritureNum", "EcritureDate",
            "CompteNum", "CompteLib", "CompAuxNum", "CompAuxLib",
            "PieceRef", "PieceDate", "EcritureLib", "Debit",
            "Credit", "EcrtureLet", "DateLet", "ValidDate",
            "Montantdevise", "Idevise"
    );

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    /**
     * Generate a FEC-compliant export for a user's exercise period.
     *
     * @param userId    user
     * @param from      start of fiscal year (e.g. 2024-01-01)
     * @param to        end of fiscal year (e.g. 2024-12-31)
     * @return FEC content as a TSV string
     */
    public String generateFec(UUID userId, LocalDate from, LocalDate to) {
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) {
            throw new IllegalStateException("Aucun compte bancaire connecté pour l'export FEC.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(FEC_HEADER).append("\n");

        AtomicInteger sequence = new AtomicInteger(1);

        for (AccountEntity account : accounts) {
            List<TransactionEntity> txns = transactionRepository
                    .findByAccountIdAndDateBetween(account.getId(), from, to);

            for (TransactionEntity t : txns) {
                String ecritureNum = String.format("FG%06d", sequence.getAndIncrement());
                String ecritureDate = t.getDate().format(FEC_DATE);
                String compteNum = mapCategoryToAccount(t.getCategory(), t.getType());
                String compteLib = mapCategoryToLabel(t.getCategory());
                String journalCode = t.getType() == TransactionEntity.TransactionType.CREDIT ? "BQ" : "BQ";
                String journalLib = "Banque " + (account.getBankName() != null ? account.getBankName() : account.getIban());

                // Debit/Credit columns
                String debit;
                String credit;
                if (t.getType() == TransactionEntity.TransactionType.DEBIT) {
                    debit = t.getAmount().toPlainString();
                    credit = "0.00";
                } else {
                    debit = "0.00";
                    credit = t.getAmount().toPlainString();
                }

                // Counter-entry on bank account (512xxx)
                String bankAccount = "512" + account.getIban().substring(account.getIban().length() - 3);

                // Main entry
                sb.append(String.join("\t",
                        journalCode, journalLib, ecritureNum, ecritureDate,
                        compteNum, compteLib, "", "",
                        ecritureNum, ecritureDate, sanitize(t.getLabel()),
                        debit, credit, "", "", ecritureDate,
                        t.getAmount().toPlainString(), account.getCurrency()
                )).append("\n");

                // Counter-entry (bank)
                sb.append(String.join("\t",
                        journalCode, journalLib, ecritureNum, ecritureDate,
                        bankAccount, "Banque", "", "",
                        ecritureNum, ecritureDate, sanitize(t.getLabel()),
                        credit, debit, "", "", ecritureDate,
                        t.getAmount().toPlainString(), account.getCurrency()
                )).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Map transaction categories to PCG (Plan Comptable Général) account numbers.
     */
    private String mapCategoryToAccount(TransactionEntity.TransactionCategory category, TransactionEntity.TransactionType type) {
        if (type == TransactionEntity.TransactionType.CREDIT) {
            return switch (category) {
                case CLIENT_PAYMENT -> "411000";
                case SALAIRE -> "421000";
                case VIREMENT -> "580000";
                default -> "700000"; // Produits
            };
        }
        return switch (category) {
            case LOYER -> "613200";
            case SALAIRE -> "641000";
            case ALIMENTATION -> "625100";
            case TRANSPORT -> "625200";
            case ABONNEMENT -> "613500";
            case ENERGIE -> "606100";
            case TELECOM -> "626000";
            case ASSURANCE -> "616000";
            case CHARGES_FISCALES -> "635000";
            case FOURNISSEUR -> "401000";
            case VIREMENT -> "580000";
            default -> "600000";
        };
    }

    private String mapCategoryToLabel(TransactionEntity.TransactionCategory category) {
        return switch (category) {
            case LOYER -> "Loyers et charges locatives";
            case SALAIRE -> "Rémunérations du personnel";
            case ALIMENTATION -> "Fournitures non stockables";
            case TRANSPORT -> "Frais de transport";
            case ABONNEMENT -> "Locations mobilières";
            case ENERGIE -> "Fournitures eau, énergie";
            case TELECOM -> "Frais postaux et télécommunications";
            case ASSURANCE -> "Primes d'assurance";
            case CHARGES_FISCALES -> "Impôts, taxes et versements assimilés";
            case FOURNISSEUR -> "Fournisseurs";
            case CLIENT_PAYMENT -> "Clients";
            case VIREMENT -> "Virements internes";
            case AUTRE -> "Charges diverses";
        };
    }

    /** Remove tabs and newlines that would break TSV format. */
    private String sanitize(String value) {
        if (value == null) return "";
        return value.replace("\t", " ").replace("\n", " ").replace("\r", "");
    }
}
