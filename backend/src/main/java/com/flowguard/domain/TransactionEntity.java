package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionCategory category;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private boolean isRecurring = false;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // ── Gap 3: import provenance fields ───────────────────────────────────
    // Tracks where the transaction came from so the ML model can apply
    // source-quality weights (BRIDGE_API = gold, CSV = silver, PDF = bronze).

    @Enumerated(EnumType.STRING)
    @Column(name = "import_source", nullable = false)
    @Builder.Default
    private ImportSource importSource = ImportSource.BRIDGE_API;

    /**
     * Groups all transactions from one file upload; used for rollback — see
     * import_batches table.
     */
    @Column(name = "import_batch_id")
    private UUID importBatchId;

    /**
     * True when the transaction date is more than 90 days before the import date.
     * Drives quality-weight reduction in the ML training pipeline.
     */
    @Column(name = "is_historical", nullable = false)
    @Builder.Default
    private boolean isHistorical = false;

    // ── Gap 2: bank-verified running balance from PDF/OFX ──────────────────
    // When parsed from a PDF (column: Solde) or OFX AVAILBAL, this is the
    // bank-certified balance after the transaction, with no reconstruction.
    // NULL for Bridge API transactions (balance reconstructed at query time).
    @Column(name = "balance_after", precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    public enum TransactionType {
        DEBIT, CREDIT
    }

    public enum TransactionCategory {
        LOYER, SALAIRE, ALIMENTATION, TRANSPORT, ABONNEMENT,
        ENERGIE, TELECOM, ASSURANCE, CHARGES_FISCALES, FOURNISSEUR,
        CLIENT_PAYMENT, VIREMENT, AUTRE
    }

    public enum ImportSource {
        BRIDGE_API, // Live Open Banking — highest quality
        OFX, // OFX / QFX file export
        MT940, // SWIFT MT940 / STA
        CFONB, // French CFONB 120
        XLSX, // Excel spreadsheet
        PDF, // PDF bank statement (heuristic parser)
        CSV, // Manual CSV export
        MANUAL // User-entered manually
    }
}
