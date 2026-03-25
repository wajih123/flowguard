package com.flowguard.service;

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

        @Test
        void virIn_isCredit() {
            assertThat(BankStatementParserService.inferTypeFromLabel("VIR INST MONSIEUR TARSIM WAJIH"))
                    .isEqualTo("CREDIT");
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
}
