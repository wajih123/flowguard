package com.flowguard.resource;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.InvoiceEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.TaxDeclarationDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.InvoiceRepository;
import com.flowguard.repository.TransactionRepository;
import com.flowguard.repository.UserRepository;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * GET /api/tax-declaration?year=2025
 *
 * Produces a tax declaration preparation sheet for the given fiscal year.
 * Does NOT file anything — output is a helper document the user copies
 * into impots.gouv.fr or gives to their accountant.
 *
 * Regime mapping (based on UserType):
 *   FREELANCE / INDIVIDUAL → MICRO_BNC  (abattement 34%, box 5HQ / 5KO)
 *   TPE / SME              → MICRO_BIC  (abattement 50%, box 5KO) or REEL depending on revenue
 *   PME                    → REEL_SIMPLIFIE
 *   B2C_*                  → MICRO_BNC  (salarié, famille — uses 2042 standard)
 */
@Path("/tax-declaration")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class TaxDeclarationResource {

    // Abattement rates (Micro regime)
    private static final BigDecimal ABATTEMENT_BNC = new BigDecimal("0.34");  // 34% BNC
    private static final BigDecimal ABATTEMENT_BIC = new BigDecimal("0.50");  // 50% BIC services

    // TVA rate (standard 20%)
    private static final BigDecimal TVA_RATE = new BigDecimal("0.20");

    @Inject TransactionRepository transactionRepository;
    @Inject AccountRepository     accountRepository;
    @Inject InvoiceRepository     invoiceRepository;
    @Inject UserRepository        userRepository;
    @Inject JsonWebToken          jwt;

    @GET
    @RunOnVirtualThread
    public TaxDeclarationDto get(@QueryParam("year") Integer yearParam) {
        UUID userId = UUID.fromString(jwt.getSubject());

        int year = (yearParam != null) ? yearParam : LocalDate.now().getYear() - 1;
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to   = LocalDate.of(year, 12, 31);

        // ── User info ────────────────────────────────────────────────────────
        UserEntity user = userRepository.findById(userId);
        String businessType = (user != null && user.getUserType() != null)
                ? user.getUserType().name()
                : "FREELANCE";
        String regime = detectRegime(businessType);

        // ── Invoices for the year ────────────────────────────────────────────
        List<InvoiceEntity> allInvoices = invoiceRepository.findByUserId(userId)
                .stream()
                .filter(inv -> !inv.getIssueDate().isBefore(from) && !inv.getIssueDate().isAfter(to))
                .toList();

        BigDecimal grossRevenue = BigDecimal.ZERO;
        int paidCount = 0;
        for (InvoiceEntity inv : allInvoices) {
            if (inv.getStatus() == InvoiceEntity.InvoiceStatus.PAID) {
                grossRevenue = grossRevenue.add(inv.getAmountHt());
                paidCount++;
            }
        }

        // ── Transactions for the year ─────────────────────────────────────────
        List<AccountEntity> accounts = accountRepository.findActiveByUserId(userId);
        List<TransactionEntity> allTx = new ArrayList<>();
        for (AccountEntity acc : accounts) {
            allTx.addAll(transactionRepository.findByAccountIdAndDateBetween(acc.getId(), from, to));
        }

        // If no paid invoices, fall back to CREDIT transactions as revenue estimate
        if (grossRevenue.compareTo(BigDecimal.ZERO) == 0) {
            for (TransactionEntity tx : allTx) {
                if (tx.getType() == TransactionEntity.TransactionType.CREDIT) {
                    grossRevenue = grossRevenue.add(tx.getAmount().abs());
                }
            }
        }

        // ── Deductible charges (DEBIT transactions, grouped by category) ──────
        BigDecimal chargesLoyer        = BigDecimal.ZERO;
        BigDecimal chargesTelecom      = BigDecimal.ZERO;
        BigDecimal chargesAssurance    = BigDecimal.ZERO;
        BigDecimal chargesTransport    = BigDecimal.ZERO;
        BigDecimal chargesAbonnements  = BigDecimal.ZERO;
        BigDecimal chargesEnergie      = BigDecimal.ZERO;
        BigDecimal chargesFournisseurs = BigDecimal.ZERO;
        BigDecimal chargesAutres       = BigDecimal.ZERO;
        int uncategorized = 0;

        for (TransactionEntity tx : allTx) {
            if (tx.getType() != TransactionEntity.TransactionType.DEBIT) continue;
            BigDecimal amt = tx.getAmount().abs();
            if (tx.getCategory() == null) { uncategorized++; continue; }
            switch (tx.getCategory()) {
                case LOYER         -> chargesLoyer        = chargesLoyer.add(amt);
                case TELECOM       -> chargesTelecom      = chargesTelecom.add(amt);
                case ASSURANCE     -> chargesAssurance    = chargesAssurance.add(amt);
                case TRANSPORT     -> chargesTransport    = chargesTransport.add(amt);
                case ABONNEMENT    -> chargesAbonnements  = chargesAbonnements.add(amt);
                case ENERGIE       -> chargesEnergie      = chargesEnergie.add(amt);
                case FOURNISSEUR   -> chargesFournisseurs = chargesFournisseurs.add(amt);
                case CHARGES_FISCALES, AUTRE, ALIMENTATION,
                     SALAIRE, VIREMENT, CLIENT_PAYMENT
                                   -> chargesAutres       = chargesAutres.add(amt);
            }
        }

        BigDecimal totalCharges = chargesLoyer.add(chargesTelecom).add(chargesAssurance)
                .add(chargesTransport).add(chargesAbonnements).add(chargesEnergie)
                .add(chargesFournisseurs).add(chargesAutres);

        // ── TVA ───────────────────────────────────────────────────────────────
        BigDecimal tvaCollectee  = grossRevenue.multiply(TVA_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tvaDeductible = totalCharges.multiply(TVA_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tvaSolde      = tvaCollectee.subtract(tvaDeductible).max(BigDecimal.ZERO);

        // ── Regime-specific calculations ──────────────────────────────────────
        BigDecimal abattementRate = regime.equals("MICRO_BIC") ? ABATTEMENT_BIC : ABATTEMENT_BNC;
        BigDecimal abattement     = grossRevenue.multiply(abattementRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableIncome  = grossRevenue.subtract(abattement).max(BigDecimal.ZERO);
        BigDecimal netRevenue     = regime.equals("REEL_SIMPLIFIE")
                ? grossRevenue.subtract(totalCharges).max(BigDecimal.ZERO)
                : taxableIncome;
        BigDecimal beneficeNet    = grossRevenue.subtract(totalCharges);

        // ── 2042-C Pro form box codes ─────────────────────────────────────────
        Map<String, BigDecimal> boxes = buildFormBoxes(regime, grossRevenue, taxableIncome,
                tvaCollectee, tvaDeductible, tvaSolde, beneficeNet, grossRevenue);

        // ── Warnings ──────────────────────────────────────────────────────────
        List<String> warnings = buildWarnings(allInvoices, uncategorized,
                grossRevenue, regime, year, allTx);

        return new TaxDeclarationDto(
                year, regime, businessType,
                grossRevenue.setScale(2, RoundingMode.HALF_UP),
                tvaCollectee,
                netRevenue.setScale(2, RoundingMode.HALF_UP),
                abattement,
                taxableIncome,
                chargesLoyer, chargesTelecom, chargesAssurance, chargesTransport,
                chargesAbonnements, chargesEnergie, chargesFournisseurs, chargesAutres,
                totalCharges.setScale(2, RoundingMode.HALF_UP),
                beneficeNet.setScale(2, RoundingMode.HALF_UP),
                tvaCollectee, tvaDeductible, tvaSolde,
                boxes,
                warnings,
                allInvoices.size(), paidCount, uncategorized
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String detectRegime(String businessType) {
        return switch (businessType) {
            case "PME"  -> "REEL_SIMPLIFIE";
            case "TPE", "SME" -> "MICRO_BIC";
            default     -> "MICRO_BNC"; // FREELANCE, INDIVIDUAL, B2C_*
        };
    }

    /**
     * Map computed values to official French tax form box codes.
     *
     * 2042-C Pro (Déclaration complémentaire des revenus des professions non-salariées):
     *   5HQ — BNC Micro: recettes brutes (prestations)
     *   5KO — BIC Micro: chiffre d'affaires (services)
     *   5KC — BIC Micro: chiffre d'affaires (ventes)
     *   5QC — BNC Réel: bénéfice
     *   5DF — BIC Réel: bénéfice
     *
     * CA3 (Déclaration TVA):
     *   0979 — TVA collectée base 20%
     *   0703 — TVA déductible sur achats
     *   0705 — TVA à payer (solde)
     */
    private Map<String, BigDecimal> buildFormBoxes(String regime, BigDecimal grossRevenue,
            BigDecimal taxableIncome, BigDecimal tvaCollectee, BigDecimal tvaDeductible,
            BigDecimal tvaSolde, BigDecimal beneficeNet, BigDecimal ca) {

        Map<String, BigDecimal> boxes = new LinkedHashMap<>();
        switch (regime) {
            case "MICRO_BNC" -> {
                boxes.put("5HQ", grossRevenue.setScale(0, RoundingMode.HALF_UP)); // recettes brutes BNC
                // taxable income shown for reference (computed by DGFiP automatically)
            }
            case "MICRO_BIC" -> {
                boxes.put("5KO", grossRevenue.setScale(0, RoundingMode.HALF_UP)); // CA BIC services
            }
            case "REEL_SIMPLIFIE" -> {
                boxes.put("5QC", beneficeNet.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP)); // BNC réel bénéfice
                boxes.put("5DF", beneficeNet.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP)); // BIC réel bénéfice
            }
        }
        // TVA boxes (CA3 / CA12) — always computed
        boxes.put("CA3_0979", tvaCollectee.setScale(0, RoundingMode.HALF_UP));
        boxes.put("CA3_0703", tvaDeductible.setScale(0, RoundingMode.HALF_UP));
        boxes.put("CA3_0705", tvaSolde.setScale(0, RoundingMode.HALF_UP));
        return boxes;
    }

    private List<String> buildWarnings(List<InvoiceEntity> invoices, int uncategorized,
            BigDecimal grossRevenue, String regime, int year,
            List<TransactionEntity> allTx) {
        List<String> warnings = new ArrayList<>();

        long unpaid = invoices.stream()
                .filter(i -> i.getStatus() == InvoiceEntity.InvoiceStatus.SENT
                          || i.getStatus() == InvoiceEntity.InvoiceStatus.OVERDUE)
                .count();
        if (unpaid > 0) {
            warnings.add(unpaid + " facture(s) non encaissée(s) sur " + year
                    + " — exclues du CA déclaré (encaissements).");
        }

        if (uncategorized > 0) {
            warnings.add(uncategorized + " transaction(s) sans catégorie — catégorisez-les"
                    + " pour un calcul précis des charges déductibles.");
        }

        if (grossRevenue.compareTo(BigDecimal.ZERO) == 0) {
            warnings.add("Aucune recette détectée pour " + year
                    + ". Vérifiez que vos comptes sont bien connectés.");
        }

        // Seuil de franchise TVA
        if (regime.equals("MICRO_BNC") && grossRevenue.compareTo(new BigDecimal("36800")) > 0) {
            warnings.add("CA supérieur à 36 800 € : vous êtes assujetti à la TVA."
                    + " Vérifiez votre statut auprès de votre expert-comptable.");
        }
        if (regime.equals("MICRO_BIC") && grossRevenue.compareTo(new BigDecimal("91900")) > 0) {
            warnings.add("CA supérieur à 91 900 € : seuil micro-BIC dépassé."
                    + " Vous devez basculer au régime réel.");
        }

        warnings.add("Ces données sont préparées à titre indicatif. Vérifiez et validez"
                + " avec un expert-comptable avant dépôt.");

        return warnings;
    }
}
