package com.flowguard.service;

import com.flowguard.domain.TransactionEntity;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit + integration tests for {@link BankStatementParserService}.
 *
 * <h3>Integration test</h3>
 * Copy the real bank statement PDF to:
 * {@code backend/src/test/resources/fixtures/Releve_compte_31_12_2025.pdf}
 * The test is automatically skipped when the file is absent.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BankStatementParserServiceTest {

    /** AI normaliser is always disabled in these unit tests (no Ollama in CI). */
    @Mock
    AiBankNormalizerService aiNormalizer;

    @InjectMocks
    BankStatementParserService parser;

    @BeforeEach
    void disableAi() {
        when(aiNormalizer.isEnabled()).thenReturn(false);
        when(aiNormalizer.detectColumnMapping(any(), any())).thenReturn(Map.of());
        when(aiNormalizer.extractFromText(any())).thenReturn(List.of());
    }

    // ──────────────────────────────────────────────────────────────────
    // inferTypeFromLabel — keyword detection
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("inferTypeFromLabel")
    class InferTypeFromLabel {

        @Test
        void prlv_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("PRLV SEPA NETFLIX 01/01/2026"))
                    .isEqualTo("DEBIT");
        }

        @Test
        void carte_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("CARTE 09/01/26 LECLERC CB5134"))
                    .isEqualTo("DEBIT");
        }

        @Test
        void cb_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("CB PAIEMENT 09/01/26")).isEqualTo("DEBIT");
        }

        @Test
        void retrait_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("RETRAIT DAB 05/01/26 NICE CB5134"))
                    .isEqualTo("DEBIT");
        }

        @Test
        void dab_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("RETRAIT DAB NICE")).isEqualTo("DEBIT");
        }

        @Test
        void frais_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("FRAIS TENUE COMPTE")).isEqualTo("DEBIT");
        }

        @Test
        void avoir_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("AVOIR 03/12/25 PAYPAL RAKUTEN"))
                    .isEqualTo("CREDIT");
        }

        @Test
        void remboursement() {
            assertThat(BankStatementParserService.inferTypeFromLabel("REMBOURSEMENT ASSURANCE")).isEqualTo("CREDIT");
        }

        @Test
        void salaire_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("SALAIRE DECEMBRE 2025")).isEqualTo("CREDIT");
        }

        // BoursoBank-specific VIR INST direction rules (from real Dec 2025 – Feb 2026
        // statements)
        @Test
        void virInstMonsieur_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("VIR INST MONSIEUR TARSIM WAJIH"))
                    .isEqualTo("CREDIT");
        }

        @Test
        void virInstMme_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("VIR INST MME MARIEM SALTI")).isEqualTo("CREDIT");
        }

        @Test
        void virVirementDepuis_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("VIR Virement depuis BoursoBank MOALLA OMAR"))
                    .isEqualTo("CREDIT");
        }

        @Test
        void virInstBareNameIsAmbiguous() {
            assertThat(BankStatementParserService.inferTypeFromLabel("VIR INST ALAIN BALTEAU")).isNull();
        }

        @Test
        void primeParrainage_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Prime Parrainage")).isEqualTo("CREDIT");
        }

        @Test
        void virement_unknown() {
            assertThat(BankStatementParserService.inferTypeFromLabel("VIR EMIS LOYER")).isEqualTo("DEBIT");
        }

        @Test
        void unknownLabel() {
            assertThat(BankStatementParserService.inferTypeFromLabel("OPERATION DIVERSE")).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // extractTransactionsFromText — core PDF heuristic parser
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractTransactionsFromText — synthetic French bank statement text")
    class ExtractTransactionsFromText {

        /**
         * Simulates a typical 2-column French bank statement (transaction | balance).
         * Each line: DATE LABEL TRANSACTION_AMOUNT RUNNING_BALANCE
         */
        private static final String SAMPLE_TEXT = String.join("\n",
                "Releve de compte au 31/12/2025",
                "IBAN FR76 1234 5678 9012 3456 7890 123",
                "Solde precedent au 01/11/2025 : 1 000,00",
                "",
                "01/12/2025 PRLV SEPA NETFLIX 01/12/2025          15,99     984,01",
                "02/12/2025 CARTE 01/12/25 CARREFOUR CB5134        87,42     896,59",
                "03/12/2025 RETRAIT DAB 02/12/25 NICE CB5134       50,00     846,59",
                "04/12/2025 VIR RECU SALAIRE DECEMBRE 2025       2 500,00  3 346,59",
                "05/12/2025 AVOIR 03/12/25 PAYPAL RAKUTENFR CB5134 29,99   3 376,58",
                "06/12/2025 FRAIS TENUE COMPTE NOVEMBRE 2025        6,25   3 370,33",
                "07/12/2025 VIR INST MONSIEUR TARSIM WAJIH          10,00  3 360,33",
                "Solde au 31/12/2025 : 3 360,33");

        private List<BankStatementParserService.ParsedRow> rows;

        @BeforeEach
        void parse() {
            rows = parser.extractTransactionsFromText(SAMPLE_TEXT);
        }

        @Test
        @DisplayName("Exactly 7 transactions parsed (header/footer/balance lines skipped)")
        void transactionCount() {
            assertThat(rows).hasSize(7);
        }

        @Test
        @DisplayName("Amounts are transaction amounts, NOT running balances (< 3 000 here)")
        void amountsAreNotBalances() {
            rows.forEach(r -> assertThat(r.amount())
                    .as("Amount for '%s' should be the transaction amount, not balance", r.label())
                    .isLessThan(new BigDecimal("3000")));
        }

        @Test
        @DisplayName("PRLV SEPA → DEBIT, amount 15,99")
        void prlvIsDebit() {
            BankStatementParserService.ParsedRow prlv = rows.stream()
                    .filter(r -> r.label().contains("PRLV"))
                    .findFirst().orElseThrow();
            assertThat(prlv.type()).isEqualTo("DEBIT");
            assertThat(prlv.amount()).isEqualByComparingTo("15.99");
        }

        @Test
        @DisplayName("CARTE → DEBIT, amount 87,42")
        void carteIsDebit() {
            BankStatementParserService.ParsedRow carte = rows.stream()
                    .filter(r -> r.label().startsWith("CARTE"))
                    .findFirst().orElseThrow();
            assertThat(carte.type()).isEqualTo("DEBIT");
            assertThat(carte.amount()).isEqualByComparingTo("87.42");
        }

        @Test
        @DisplayName("RETRAIT DAB → DEBIT, amount 50,00")
        void retraitIsDebit() {
            BankStatementParserService.ParsedRow retrait = rows.stream()
                    .filter(r -> r.label().contains("RETRAIT"))
                    .findFirst().orElseThrow();
            assertThat(retrait.type()).isEqualTo("DEBIT");
            assertThat(retrait.amount()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("VIR RECU SALAIRE → CREDIT, amount 2 500,00")
        void salaireIsCredit() {
            BankStatementParserService.ParsedRow sal = rows.stream()
                    .filter(r -> r.label().contains("SALAIRE"))
                    .findFirst().orElseThrow();
            assertThat(sal.type()).isEqualTo("CREDIT");
            assertThat(sal.amount()).isEqualByComparingTo("2500.00");
        }

        @Test
        @DisplayName("AVOIR → CREDIT, amount 29,99")
        void avoirIsCredit() {
            BankStatementParserService.ParsedRow avoir = rows.stream()
                    .filter(r -> r.label().contains("AVOIR"))
                    .findFirst().orElseThrow();
            assertThat(avoir.type()).isEqualTo("CREDIT");
            assertThat(avoir.amount()).isEqualByComparingTo("29.99");
        }

        @Test
        @DisplayName("FRAIS TENUE COMPTE → DEBIT, amount 6,25")
        void fraisIsDebit() {
            BankStatementParserService.ParsedRow frais = rows.stream()
                    .filter(r -> r.label().contains("FRAIS"))
                    .findFirst().orElseThrow();
            assertThat(frais.type()).isEqualTo("DEBIT");
            assertThat(frais.amount()).isEqualByComparingTo("6.25");
        }

        @Test
        @DisplayName("No transaction has a null date or blank label")
        void noNullFields() {
            rows.forEach(r -> {
                assertThat(r.date()).as("date for %s", r.label()).isNotNull();
                assertThat(r.label()).as("label").isNotBlank();
                assertThat(r.amount()).as("amount for %s", r.label()).isPositive();
                assertThat(r.type()).as("type for %s", r.label()).isIn("DEBIT", "CREDIT");
            });
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // parsePDF — round-trip through PDFBox
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("parsePDF — synthetic in-memory PDF")
    class ParsePdf {

        @Test
        @DisplayName("Transactions extracted from an in-memory PDF match expected values")
        void syntheticPdf() throws Exception {
            // ── Build a minimal PDF with statement-like content ────────────────
            String[] lines = {
                    "Releve de compte - Decembre 2025",
                    "01/12/2025 PRLV SEPA NETFLIX          15,99      984,01",
                    "02/12/2025 CARTE 01/12/25 LECLERC      87,42      896,59",
                    "04/12/2025 VIR RECU SALAIRE DEC 2025 2 500,00   3 396,59",
                    "06/12/2025 RETRAIT DAB NICE CB5134       50,00   3 346,59",
                    "Solde final : 3 346,59"
            };
            byte[] pdfBytes = buildPdf(lines);

            List<BankStatementParserService.ParsedRow> rows = parser.parsePDF(pdfBytes);

            assertThat(rows).hasSize(4);

            assertThat(rows).anySatisfy(r -> {
                assertThat(r.label()).contains("PRLV");
                assertThat(r.type()).isEqualTo("DEBIT");
                assertThat(r.amount()).isEqualByComparingTo("15.99");
            });
            assertThat(rows).anySatisfy(r -> {
                assertThat(r.label()).contains("CARTE");
                assertThat(r.type()).isEqualTo("DEBIT");
                assertThat(r.amount()).isEqualByComparingTo("87.42");
            });
            assertThat(rows).anySatisfy(r -> {
                assertThat(r.label()).contains("SALAIRE");
                assertThat(r.type()).isEqualTo("CREDIT");
                assertThat(r.amount()).isEqualByComparingTo("2500.00");
            });
            assertThat(rows).anySatisfy(r -> {
                assertThat(r.label()).contains("RETRAIT");
                assertThat(r.type()).isEqualTo("DEBIT");
                assertThat(r.amount()).isEqualByComparingTo("50.00");
            });
        }

        /** Builds a PDF in memory with the given text lines using PDFBox. */
        private byte[] buildPdf(String[] lines) throws Exception {
            try (PDDocument doc = new PDDocument();
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                PDPage page = new PDPage();
                doc.addPage(page);
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 10);
                    cs.setLeading(14.5f);
                    cs.newLineAtOffset(25, 700);
                    for (String line : lines) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
                }
                doc.save(out);
                return out.toByteArray();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Integration test — real file from user's bank
    // ──────────────────────────────────────────────────────────────────

    /**
     * Integration test using the real BoursoBank statement PDF.
     * Place the file at:
     * backend/src/test/resources/fixtures/Releve_compte_31_12_2025.pdf
     *
     * Test is skipped automatically when the file is absent (CI / clean checkout).
     */
    @Nested
    @DisplayName("parsePDF — real statement Releve_compte_31_12_2025.pdf")
    class RealPdfIntegration {

        private static final String FIXTURE_PATH = "src/test/resources/fixtures/Releve_compte_31_12_2025.pdf";

        @Test
        @DisplayName("Parses real PDF: no amount exceeds 10 000 (balance not picked up)")
        void noBalanceAmounts() throws Exception {
            File f = new File(FIXTURE_PATH);
            Assumptions.assumeTrue(f.exists(),
                    "Skipping: place the PDF at " + FIXTURE_PATH);

            byte[] bytes;
            try (FileInputStream fis = new FileInputStream(f)) {
                bytes = fis.readAllBytes();
            }

            List<BankStatementParserService.ParsedRow> rows = parser.parsePDF(bytes);

            assertThat(rows).isNotEmpty();

            // Running balances in this statement are in the 25 000 range.
            // Transaction amounts should all be well below 10 000.
            rows.forEach(r -> assertThat(r.amount())
                    .as("Amount for '%s' looks like a balance, not a transaction", r.label())
                    .isLessThan(new BigDecimal("10000")));
        }

        @Test
        @DisplayName("Parses real PDF: PRLV / CARTE / RETRAIT rows are DEBIT")
        void debitKeywordsAreDebit() throws Exception {
            File f = new File(FIXTURE_PATH);
            Assumptions.assumeTrue(f.exists(),
                    "Skipping: place the PDF at " + FIXTURE_PATH);

            byte[] bytes;
            try (FileInputStream fis = new FileInputStream(f)) {
                bytes = fis.readAllBytes();
            }

            List<BankStatementParserService.ParsedRow> rows = parser.parsePDF(bytes);

            rows.stream()
                    .filter(r -> r.label().toUpperCase().startsWith("PRLV")
                            || r.label().toUpperCase().startsWith("CARTE")
                            || r.label().toUpperCase().contains("RETRAIT"))
                    .forEach(r -> assertThat(r.type())
                            .as("'%s' should be DEBIT", r.label())
                            .isEqualTo("DEBIT"));
        }

        @Test
        @DisplayName("Parses real PDF: AVOIR rows are CREDIT")
        void avoirRowsAreCredit() throws Exception {
            File f = new File(FIXTURE_PATH);
            Assumptions.assumeTrue(f.exists(),
                    "Skipping: place the PDF at " + FIXTURE_PATH);

            byte[] bytes;
            try (FileInputStream fis = new FileInputStream(f)) {
                bytes = fis.readAllBytes();
            }

            List<BankStatementParserService.ParsedRow> rows = parser.parsePDF(bytes);

            rows.stream()
                    .filter(r -> r.label().toUpperCase().contains("AVOIR"))
                    .forEach(r -> assertThat(r.type())
                            .as("'%s' should be CREDIT", r.label())
                            .isEqualTo("CREDIT"));
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // inferCategoryFromLabel — calibrated against real BoursoBank data
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("inferCategoryFromLabel — real merchant patterns from Dec 2025–Feb 2026")
    class InferCategoryFromLabel {

        private TransactionEntity.TransactionCategory cat(String label) {
            return BankStatementParserService.inferCategoryFromLabel(label);
        }

        // CHARGES_FISCALES
        @Test
        void echeancePret_isCharges() {
            assertThat(cat("PRLV SEPA ECHEANCE PRET 00005849074"))
                    .isEqualTo(TransactionEntity.TransactionCategory.CHARGES_FISCALES);
        }

        @Test
        void impayCbOney_isCharges() {
            assertThat(cat("CARTE 08/01/26 IMPAYE CB ONEY CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.CHARGES_FISCALES);
        }

        @Test
        void oney3x_isCharges() {
            assertThat(cat("CARTE 09/12/25 3X 4X ONEY CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.CHARGES_FISCALES);
        }

        @Test
        void financemtOney_isCharges() {
            assertThat(cat("CARTE 05/12/25 FINANCEMT ONEY CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.CHARGES_FISCALES);
        }

        // ENERGIE
        @Test
        void totalEnergies_isEnergie() {
            assertThat(cat("PRLV SEPA TotalEnergies Electricite et G France"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ENERGIE);
        }

        @Test
        void edf_isEnergie() {
            assertThat(cat("PRLV SEPA EDF FACTURE")).isEqualTo(TransactionEntity.TransactionCategory.ENERGIE);
        }

        // ASSURANCE
        @Test
        void avanssur_isAssurance() {
            assertThat(cat("PRLV SEPA AVANSSUR Direct Assurance 754860516"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ASSURANCE);
        }

        @Test
        void lolivier_isAssurance() {
            assertThat(cat("PRLV SEPA LOLIVIER ASSURANCE EUI France"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ASSURANCE);
        }

        @Test
        void directAssuranc_isAssurance() {
            assertThat(cat("CARTE 10/02/26 DIRECT ASSURANC 4 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ASSURANCE);
        }

        // TELECOM
        @Test
        void sfr_isTelecom() {
            assertThat(cat("PRLV SEPA SFR SFR Prlvt SEPA")).isEqualTo(TransactionEntity.TransactionCategory.TELECOM);
        }

        @Test
        void sfrPaiementCarte_isTelecom() {
            assertThat(cat("CARTE 03/02/26 SFR PAIEMENT CB 2 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TELECOM);
        }

        // ABONNEMENT
        @Test
        void spotify_isAbonnement() {
            assertThat(cat("CARTE 08/12/25 SpotifyFR CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ABONNEMENT);
        }

        @Test
        void appleBill_isAbonnement() {
            assertThat(cat("CARTE 05/12/25 APPLE.COM/BILL CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ABONNEMENT);
        }

        @Test
        void deliverooPlus_isAbonnement() {
            assertThat(cat("CARTE 06/12/25 Deliveroo Plus Su CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ABONNEMENT);
        }

        @Test
        void wodify_isAbonnement() {
            assertThat(cat("CARTE 04/12/25 WODIFY* WP CROSSF CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ABONNEMENT);
        }

        @Test
        void wellpass_isAbonnement() {
            assertThat(cat("CARTE 25/02/26 WELLPASS FR (GYML CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ABONNEMENT);
        }

        @Test
        void github_isAbonnement() {
            assertThat(cat("CARTE 19/12/25 PAYPAL *GITHUB IN CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ABONNEMENT);
        }

        @Test
        void hetzner_isAbonnement() {
            assertThat(cat("CARTE 22/12/25 PAYPAL *HETZNER CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ABONNEMENT);
        }

        // ALIMENTATION
        @Test
        void lidl_isAlimentation() {
            assertThat(cat("CARTE 06/12/25 LIDL NICE CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        void carrefour_isAlimentation() {
            assertThat(cat("CARTE 09/01/26 CARREFOUR CITY CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        void monoprix_isAlimentation() {
            assertThat(cat("CARTE 16/02/26 MONOPRIX 4 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        void deliverooFood_isAlimentation() {
            assertThat(cat("CARTE 05/12/25 DELIVEROO CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        void pyszne_isAlimentation() {
            assertThat(cat("CARTE 26/12/25 Pyszne.pl CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        void nabulio_isAlimentation() {
            assertThat(cat("CARTE 29/11/25 NABULIO CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        // TRANSPORT
        @Test
        void bolt_isTransport() {
            assertThat(cat("CARTE 28/12/25 BOLT.EU/O/2512281 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        @Test
        void navigo_isTransport() {
            assertThat(cat("CARTE 08/01/26 SERVICE NAVIGO 4 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        @Test
        void sncf_isTransport() {
            assertThat(cat("CARTE 27/01/26 SNCF-VOYAGEURS CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        @Test
        void paybyphone_isTransport() {
            assertThat(cat("CARTE 05/12/25 PAYBYPHONE NICE22 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        @Test
        void pbpNice_isTransport() {
            assertThat(cat("CARTE 13/02/26 PBP_NICE 4 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        @Test
        void autoroutes_isTransport() {
            assertThat(cat("PRLV SEPA AUTOROUTES DU SUD DE LA FRANCE"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        @Test
        void getyourguide_isTransport() {
            assertThat(cat("CARTE 26/12/25 GetYourGuide CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        @Test
        void airFrance_isTransport() {
            assertThat(cat("CARTE 01/01/26 AIR FRANCE YX266 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        // VIREMENT — transfers, ATM, self-transfers
        @Test
        void virInst_isVirement() {
            assertThat(cat("VIR INST MONSIEUR TARSIM WAJIH")).isEqualTo(TransactionEntity.TransactionCategory.VIREMENT);
        }

        @Test
        void virVirementDepuis_isVirement() {
            assertThat(cat("VIR Virement depuis BoursoBank MOALLA OMAR"))
                    .isEqualTo(TransactionEntity.TransactionCategory.VIREMENT);
        }

        @Test
        void retraitDab_isVirement() {
            assertThat(cat("RETRAIT DAB 15/12/25 NICE CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.VIREMENT);
        }

        // Unknown → null (caller uses AUTRE)
        @Test
        void binance_isNull() {
            assertThat(cat("CARTE 08/02/26 BINANCE CB*5134")).isNull();
        }

        @Test
        void unknown_isNull() {
            assertThat(cat("CARTE 20/12/25 YOLO CB*5134")).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Period-as-thousands-separator — BoursoBank 1.040,00 format
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractTransactionsFromText — period-as-thousands BoursoBank amounts")
    class PeriodThousandsSeparator {

        /**
         * BoursoBank uses 1.040,00 and 1.500,00 — confirmed in Dec 2025 and Jan 2026
         * PDFs.
         */
        private static final String BOURSOBANK_TEXT = String.join("\n",
                "SOLDE AU : 28/11/2025 583,77",
                "08/12/2025 VIR INST ALAIN BALTEAU 08/12/2025 1.040,00",
                "08/12/2025 VIR INST MONSIEUR TARSIM WAJIH 08/12/2025 1.500,00",
                "01/12/2025 RETRAIT DAB 29/11/25 NICE CB*5134 01/12/2025 50,00",
                "02/12/2025 CARTE 30/11/25 PAYPAL *RAKUTENFR CB*5134 02/12/2025 549,69");

        private List<BankStatementParserService.ParsedRow> rows;

        @BeforeEach
        void parse() {
            rows = parser.extractTransactionsFromText(BOURSOBANK_TEXT);
        }

        @Test
        @DisplayName("4 transactions parsed (SOLDE header line skipped)")
        void transactionCount() {
            assertThat(rows).hasSize(4);
        }

        @Test
        @DisplayName("1.040,00 parsed as 1040.00, not 1.04")
        void periodThousandsAlainBalteau() {
            BankStatementParserService.ParsedRow alain = rows.stream()
                    .filter(r -> r.label().contains("ALAIN BALTEAU"))
                    .findFirst().orElseThrow();
            assertThat(alain.amount()).isEqualByComparingTo("1040.00");
        }

        @Test
        @DisplayName("1.500,00 parsed as 1500.00, not 1.50")
        void periodThousandsTarsimWajih() {
            BankStatementParserService.ParsedRow tarsim = rows.stream()
                    .filter(r -> r.label().contains("MONSIEUR TARSIM WAJIH"))
                    .findFirst().orElseThrow();
            assertThat(tarsim.amount()).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("VIR INST MONSIEUR → CREDIT; VIR INST ALAIN (no civility) → ambiguous type")
        void virInstDirections() {
            BankStatementParserService.ParsedRow tarsim = rows.stream()
                    .filter(r -> r.label().contains("MONSIEUR TARSIM WAJIH"))
                    .findFirst().orElseThrow();
            assertThat(tarsim.type()).isEqualTo("CREDIT");
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Exchange-rate continuation lines must be filtered out
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractTransactionsFromText — exchange-rate lines are ignored")
    class ExchangeRateLineFilter {

        /** From real Dec 2025 BoursoBank statement — Polish trip transactions. */
        private static final String TEXT_WITH_FX = String.join("\n",
                "29/12/2025 CARTE 24/12/25 www.bilet.interci CB*5134 29/12/2025 32,30",
                "136,00 PLN / 1 euro = 4,210526315",
                "29/12/2025 CARTE 25/12/25 MOLLY MALONES IRI CB*5134 29/12/2025 5,94",
                "25,00 PLN / 1 euro = 4,208754208",
                "29/12/2025 CARTE 27/12/25 IBIS MURANOWSKA CB*5134 29/12/2025 52,37",
                "220,00 PLN / 1 euro = 4,200878365");

        @Test
        @DisplayName("Exchange-rate lines are skipped — exactly 3 transactions extracted")
        void fxLinesAreSkipped() {
            List<BankStatementParserService.ParsedRow> rows = parser.extractTransactionsFromText(TEXT_WITH_FX);
            assertThat(rows).hasSize(3);
            rows.forEach(r -> assertThat(r.amount())
                    .as("FX line should not be a transaction")
                    .isNotEqualByComparingTo("136.00")
                    .isNotEqualByComparingTo("25.00")
                    .isNotEqualByComparingTo("220.00"));
        }

        @Test
        @DisplayName("Correct amounts extracted for foreign-currency card payments")
        void correctAmounts() {
            List<BankStatementParserService.ParsedRow> rows = parser.extractTransactionsFromText(TEXT_WITH_FX);
            assertThat(rows).anySatisfy(r -> assertThat(r.amount()).isEqualByComparingTo("32.30"));
            assertThat(rows).anySatisfy(r -> assertThat(r.amount()).isEqualByComparingTo("5.94"));
            assertThat(rows).anySatisfy(r -> assertThat(r.amount()).isEqualByComparingTo("52.37"));
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Crédit Agricole DD.MM short-date format (no year in table rows)
    // Real data from Crédit Agricole Provence Côte d'Azur statements
    // Nov 2025, Dec 2025, Jan 2026, Feb 2026, March 2026
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractTransactionsFromText — Crédit Agricole DD.MM short-date format")
    class CreditAgricoleShortDate {

        /**
         * Simulates PDFBox output from a real CA Nov 2025 statement.
         * CA format: DD.MM DD.MM Libellé [embedded DD/MM] amount [¨]
         * The "Ancien solde" line provides the context year (2025).
         */
        private static final String CA_NOV_2025 = String.join("\n",
                "Ancien solde créditeur au 21.10.2025 78,38",
                "23.10 23.10 Virement De Monsieur Tarsim Wajih 100,00",
                "27.10 27.10 Prlv Direction Générale Des Finances 253,00",
                "27.10 27.10 Prlv SFR 29,99",
                "04.11 04.11 Virement Incka 566,21",
                "05.11 05.11 Carte X9420 Binance.com Vilnius 05/11 50,00",
                "07.11 07.11 Virement Incka 3 747,37",
                "07.11 07.11 Virement Vir Inst vers Wajih Tarsim 1 920,00",
                "11.11 11.11 Prlv Lolivier Assurance Eui (france) 72,00",
                "13.11 13.11 Carte X9420 Lidl Nice 12/11 36,33",
                "21.11 21.11 Carte X9420 SAS Crossfit Ni Nice 20/11 5,85",
                "Nouveau solde créditeur au 21.11.2025 345,36");

        private List<BankStatementParserService.ParsedRow> rows;

        @BeforeEach
        void parse() {
            rows = parser.extractTransactionsFromText(CA_NOV_2025);
        }

        @Test
        @DisplayName("All CA transactions parsed (not zero)")
        void transactionsParsed() {
            assertThat(rows).isNotEmpty();
        }

        @Test
        @DisplayName("SOLDE header and footer lines are skipped")
        void soldeSkipped() {
            rows.forEach(r -> assertThat(r.label())
                    .as("No SOLDE line should appear as transaction")
                    .doesNotContainIgnoringCase("solde"));
        }

        @Test
        @DisplayName("Dates inferred with year 2025 from context")
        void datesHaveYear2025() {
            rows.forEach(r -> assertThat(r.date().getYear())
                    .as("Year should be 2025 for %s", r.label())
                    .isEqualTo(2025));
        }

        @Test
        @DisplayName("Carte X9420 Lidl Nice → DEBIT, amount 36,33, category ALIMENTATION")
        void carteCarteLidl() {
            BankStatementParserService.ParsedRow r = rows.stream()
                    .filter(t -> t.label().toUpperCase().contains("LIDL"))
                    .findFirst().orElseThrow();
            assertThat(r.type()).isEqualTo("DEBIT");
            assertThat(r.amount()).isEqualByComparingTo("36.33");
            assertThat(BankStatementParserService.inferCategoryFromLabel(r.label()))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        @DisplayName("Virement Incka 3 747,37 → space-thousands parsed correctly as 3747.37")
        void inckaSpaceThousands() {
            BankStatementParserService.ParsedRow r = rows.stream()
                    .filter(t -> t.label().contains("Incka") && t.amount().compareTo(new BigDecimal("3000")) > 0)
                    .findFirst().orElseThrow();
            assertThat(r.amount()).isEqualByComparingTo("3747.37");
        }

        @Test
        @DisplayName("Prlv Lolivier Assurance → DEBIT, category ASSURANCE")
        void prelvLolivier() {
            BankStatementParserService.ParsedRow r = rows.stream()
                    .filter(t -> t.label().toUpperCase().contains("LOLIVIER"))
                    .findFirst().orElseThrow();
            assertThat(r.type()).isEqualTo("DEBIT");
            assertThat(BankStatementParserService.inferCategoryFromLabel(r.label()))
                    .isEqualTo(TransactionEntity.TransactionCategory.ASSURANCE);
        }

        @Test
        @DisplayName("CA card labels have embedded DD/MM date stripped at end")
        void embeddedDateStripped() {
            rows.stream()
                    .filter(r -> r.label().startsWith("Carte"))
                    .forEach(r -> assertThat(r.label())
                            .as("Trailing DD/MM reference date should be stripped from '%s'", r.label())
                            .doesNotMatch(".*\\s\\d{2}/\\d{2}$"));
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Crédit Agricole Jan 2026: cross-year short dates (Dec 2025 entries
    // appear in a Jan 2026 statement — year must flip correctly)
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractTransactionsFromText — CA cross-year short dates (Jan 2026 stmt)")
    class CreditAgricoleCrossYear {

        /**
         * Jan 2026 statement includes Dec 2025 transactions — context is 2025-12-22.
         */
        private static final String CA_JAN_2026 = String.join("\n",
                "Ancien solde débiteur au 22.12.2025 189,17",
                "24.12 24.12 Virement Vir Inst vers Wajih Tarsim 50,00",
                "02.01 02.01 Virement Incka 399,63",
                "05.01 05.01 Carte X9420 Lidl Nice 03/01 7,41",
                "08.01 08.01 Virement Incka 2 921,17",
                "08.01 08.01 Virement Vir Inst vers Wajih Tarsim 1 500,00",
                "Nouveau solde créditeur au 21.01.2026 23,93");

        @Test
        @DisplayName("24.12 resolved to 2025-12-24 (Dec in prev year)")
        void decemberInPreviousYear() {
            List<BankStatementParserService.ParsedRow> rows = parser.extractTransactionsFromText(CA_JAN_2026);
            BankStatementParserService.ParsedRow dec = rows.stream()
                    .filter(r -> r.label().contains("Vir Inst vers") && r.amount().compareTo(new BigDecimal("50")) == 0)
                    .findFirst().orElseThrow();
            assertThat(dec.date().getYear()).isEqualTo(2025);
            assertThat(dec.date().getMonthValue()).isEqualTo(12);
            assertThat(dec.date().getDayOfMonth()).isEqualTo(24);
        }

        @Test
        @DisplayName("02.01 resolved to 2026-01-02 (Jan in new year)")
        void januaryInNewYear() {
            List<BankStatementParserService.ParsedRow> rows = parser.extractTransactionsFromText(CA_JAN_2026);
            // The 399,63 Incka entry (< 500) is on 02.01 which should resolve to 2026-01-02
            BankStatementParserService.ParsedRow jan = rows.stream()
                    .filter(r -> r.label().contains("Incka") && r.amount().compareTo(new BigDecimal("500")) < 0)
                    .findFirst().orElseThrow();
            assertThat(jan.date().getYear()).isEqualTo(2026);
            assertThat(jan.date().getMonthValue()).isEqualTo(1);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // BoursoBank label cleaning: trailing "Valeur" date removed
    // Real data: each BoursoBank row has an extra date column (Valeur)
    // that PDFBox appends to the label when extracting text left-to-right
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractTransactionsFromText — BoursoBank trailing Valeur date stripped")
    class BoursoTrailingDateStrip {

        private static final String BOURSO_JAN_2026 = String.join("\n",
                "SOLDE AU : 31/12/2025 84,75",
                "02/01/2026 VIR INST MME MARIEM SALTI 01/01/2026 120,00",
                "02/01/2026 CARTE 01/01/26 RIVIERA GLACONS CB*5134 02/01/2026 7,25",
                "05/01/2026 CARTE 01/01/26 AIR FRANCE YX266 CB*5134 05/01/2026 118,72",
                "06/01/2026 VIR INST MONSIEUR TARSIM WAJIH 06/01/2026 71,00",
                "07/01/2026 VIR Virement depuis ORD (DE) TARSIM 07/01/2026 281,00",
                "07/01/2026 PRLV SEPA ECHEANCE PRET 07/01/2026 234,27",
                "08/01/2026 VIR INST ALAIN BALTEAU 08/01/2026 1.040,00",
                "08/01/2026 VIR INST MONSIEUR TARSIM WAJIH 08/01/2026 1.500,00");

        private List<BankStatementParserService.ParsedRow> rows;

        @BeforeEach
        void parse() {
            rows = parser.extractTransactionsFromText(BOURSO_JAN_2026);
        }

        @Test
        @DisplayName("No label ends with a date token (valeur date stripped)")
        void noTrailingDate() {
            rows.forEach(r -> assertThat(r.label())
                    .as("Label '%s' should not end with a date", r.label())
                    .doesNotMatch(".*\\d{2}/\\d{2}/\\d{4}$")
                    .doesNotMatch(".*\\d{2}/\\d{2}/\\d{2}$"));
        }

        @Test
        @DisplayName("VIR INST MME MARIEM SALTI: CREDIT, 120,00")
        void virInstMme() {
            BankStatementParserService.ParsedRow r = rows.stream()
                    .filter(t -> t.label().contains("MARIEM SALTI"))
                    .findFirst().orElseThrow();
            assertThat(r.type()).isEqualTo("CREDIT");
            assertThat(r.amount()).isEqualByComparingTo("120.00");
            assertThat(r.label()).isEqualTo("VIR INST MME MARIEM SALTI");
        }

        @Test
        @DisplayName("PRLV SEPA ECHEANCE PRET: DEBIT, category CHARGES_FISCALES")
        void prelvEcheancePret() {
            BankStatementParserService.ParsedRow r = rows.stream()
                    .filter(t -> t.label().contains("ECHEANCE PRET"))
                    .findFirst().orElseThrow();
            assertThat(r.type()).isEqualTo("DEBIT");
            assertThat(BankStatementParserService.inferCategoryFromLabel(r.label()))
                    .isEqualTo(TransactionEntity.TransactionCategory.CHARGES_FISCALES);
        }

        @Test
        @DisplayName("VIR INST MONSIEUR TARSIM WAJIH: CREDIT, 1.500,00 = 1500.00")
        void virInstMonsieur1500() {
            BankStatementParserService.ParsedRow r = rows.stream()
                    .filter(t -> t.label().contains("VIR INST MONSIEUR")
                            && t.amount().compareTo(new BigDecimal("1000")) > 0)
                    .findFirst().orElseThrow();
            assertThat(r.type()).isEqualTo("CREDIT");
            assertThat(r.amount()).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("AIR FRANCE: DEBIT, category TRANSPORT")
        void airFrance() {
            BankStatementParserService.ParsedRow r = rows.stream()
                    .filter(t -> t.label().contains("AIR FRANCE"))
                    .findFirst().orElseThrow();
            assertThat(r.type()).isEqualTo("DEBIT");
            assertThat(BankStatementParserService.inferCategoryFromLabel(r.label()))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        @Test
        @DisplayName("VIR depuis ORD (DE) TARSIM: CREDIT (VIREMENT DEPUIS rule)")
        void virDepuisOrd() {
            BankStatementParserService.ParsedRow r = rows.stream()
                    .filter(t -> t.label().toUpperCase().contains("VIREMENT DEPUIS"))
                    .findFirst().orElseThrow();
            assertThat(r.type()).isEqualTo("CREDIT");
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Crédit Agricole — new inferTypeFromLabel patterns
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("inferTypeFromLabel — Crédit Agricole specific patterns")
    class CreditAgricoleTypeInference {

        @Test
        void virementDe_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Virement De Monsieur Tarsim Wajih"))
                    .isEqualTo("CREDIT");
        }

        @Test
        void virInstVers_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Virement Vir Inst vers Wajih Tarsim"))
                    .isEqualTo("DEBIT");
        }

        @Test
        void virementWeb_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Virement Web Monsieur Tarsim Wajih"))
                    .isEqualTo("DEBIT");
        }

        @Test
        void rejietPrlv_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Rejet Prlv SFR"))
                    .isEqualTo("CREDIT");
        }

        @Test
        void remboursPrel_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Rembours. Prel Predica"))
                    .isEqualTo("CREDIT");
        }

        @Test
        void virInstDe_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Virement Vir Inst de Wajih Tarsim"))
                    .isEqualTo("CREDIT");
        }

        @Test
        void virInstWeroDe_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Virement Vir Inst Wero de Mme Mariem Salt"))
                    .isEqualTo("CREDIT");
        }

        @Test
        void prelvCA_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Prlv SFR SFR Prlvt Sepa"))
                    .isEqualTo("DEBIT");
        }

        @Test
        void carteCA_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Carte X9420 Lidl Nice"))
                    .isEqualTo("DEBIT");
        }

        @Test
        void retDab_isDebit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("Ret DAB X9420 Nice Prefectur"))
                    .isEqualTo("DEBIT");
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // New category rules from real CA & BoursoBank statements
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("inferCategoryFromLabel — new patterns from real statements")
    class NewCategoryPatterns {

        private TransactionEntity.TransactionCategory cat(String label) {
            return BankStatementParserService.inferCategoryFromLabel(label);
        }

        // SALAIRE — Incka (employer expense reimbursement platform)
        @Test
        void incka_isSalaire() {
            assertThat(cat("Virement Incka Note de frais 2045 Novembre 2025"))
                    .isEqualTo(TransactionEntity.TransactionCategory.SALAIRE);
        }

        // CHARGES_FISCALES — CA banking incident fees
        @Test
        void fraisIrreg_isCharges() {
            assertThat(cat("** Frais Irreg.et Incidents 11/2"))
                    .isEqualTo(TransactionEntity.TransactionCategory.CHARGES_FISCALES);
        }

        @Test
        void commissionIntervention_isCharges() {
            assertThat(cat("Commission d'intervention"))
                    .isEqualTo(TransactionEntity.TransactionCategory.CHARGES_FISCALES);
        }

        @Test
        void directionGeneraleFinances_isCharges() {
            assertThat(cat("Prlv Direction Générale Des Finances Solde Impôt Revenus 2024"))
                    .isEqualTo(TransactionEntity.TransactionCategory.CHARGES_FISCALES);
        }

        // ABONNEMENT — CA bank service subscription
        @Test
        void offreEssentiel_isAbonnement() {
            assertThat(cat("** Offre Essentiel"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ABONNEMENT);
        }

        @Test
        void sassCrossfit_isAbonnement() {
            assertThat(cat("Carte X9420 SAS Crossfit Ni Nice"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ABONNEMENT);
        }

        // ASSURANCE — Predica (Crédit Agricole life insurance)
        @Test
        void predica_isAssurance() {
            assertThat(cat("Prlv Predica Crédit Agricole Garantie Décès Echéance 11/2025"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ASSURANCE);
        }

        // ALIMENTATION — new merchants from real data
        @Test
        void superU_isAlimentation() {
            assertThat(cat("Carte X9420 Super U 06 Nice Cauc"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        void liomar_isAlimentation() {
            assertThat(cat("Carte X9420 Liomar Cafe Levallois"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        void leRegal_isAlimentation() {
            assertThat(cat("Carte X9420 Le Regal Nice"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        void boucherie_isAlimentation() {
            assertThat(cat("Carte X9420 Boucherie Paul Monte"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        @Test
        void troisBrasseurs_isAlimentation() {
            assertThat(cat("CARTE 16/12/25 3 BRASSEURS 2 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ALIMENTATION);
        }

        // TRANSPORT — Total (CA fuel station label: "TOTAL 4 CB*5134")
        @Test
        void total4_isTransport() {
            assertThat(cat("CARTE 13/02/26 TOTAL 4 CB*5134"))
                    .isEqualTo(TransactionEntity.TransactionCategory.TRANSPORT);
        }

        // ENERGIE — TotalEnergies utility from CA
        @Test
        void totalenergiesCA_isEnergie() {
            assertThat(cat("Prlv Totalenergies Electricite et Gaz Prelevement"))
                    .isEqualTo(TransactionEntity.TransactionCategory.ENERGIE);
        }
    }
}
