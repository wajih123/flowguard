package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.InvoiceEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.DecisionSummaryDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.InvoiceRepository;
import com.flowguard.repository.TransactionRepository;
import com.flowguard.repository.UserRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class FinancialReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DeviceRgb ACCENT = new DeviceRgb(0x6C, 0x63, 0xFF);
    private static final DeviceRgb HEADER_BG = new DeviceRgb(0x1E, 0x1E, 0x2E);
    private static final DeviceRgb ROW_ALT = new DeviceRgb(0xF5, 0xF5, 0xF8);

    @Inject UserRepository userRepository;
    @Inject AccountRepository accountRepository;
    @Inject TransactionRepository transactionRepository;
    @Inject InvoiceRepository invoiceRepository;
    @Inject DecisionEngineService decisionEngineService;

    @Transactional
    public byte[] generateReport(UUID userId) {
        UserEntity user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        DecisionSummaryDto summary = decisionEngineService.getSummary(userId);

        List<AccountEntity> accounts = accountRepository.findActiveByUserId(userId);
        BigDecimal totalBalance = accounts.stream()
                .map(AccountEntity::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate from = LocalDate.now().minusMonths(3);
        LocalDate to = LocalDate.now();
        List<TransactionEntity> transactions = transactionRepository.findByUserIdAndDateBetween(userId, from, to);

        List<InvoiceEntity> invoices = invoiceRepository.findByUserId(userId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc);

        try {
            // ── Header ────────────────────────────────────────────────────────
            doc.add(new Paragraph("RAPPORT FINANCIER FLOWGUARD")
                    .setFontColor(ACCENT)
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            String displayName = (user.getFirstName() != null ? user.getFirstName() + " " : "")
                    + (user.getLastName() != null ? user.getLastName() : "");
            if (user.getCompanyName() != null) displayName = user.getCompanyName();

            doc.add(new Paragraph(displayName)
                    .setFontSize(13)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.DARK_GRAY));

            doc.add(new Paragraph("Généré le " + LocalDate.now().format(DATE_FMT))
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY)
                    .setMarginBottom(16));

            // ── Balance summary ───────────────────────────────────────────────
            addSectionTitle(doc, "1. Situation de Trésorerie");
            Table balanceTable = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            addTableRow(balanceTable, "Solde total (comptes actifs)", formatEur(totalBalance), true);
            addTableRow(balanceTable, "Runway estimé", summary.runwayDays() + " jours", false);
            addTableRow(balanceTable, "Niveau de risque", summary.riskLevel(), true);
            addTableRow(balanceTable, "Solde minimum projeté", formatEur(summary.minProjectedBalance()), false);
            if (summary.minProjectedDate() != null) {
                addTableRow(balanceTable, "Date du minimum prévu", summary.minProjectedDate().format(DATE_FMT), true);
            }
            doc.add(balanceTable);
            doc.add(new Paragraph(" "));

            // ── Spending by category (last 3 months) ─────────────────────────
            addSectionTitle(doc, "2. Dépenses par Catégorie (3 derniers mois)");
            Map<String, BigDecimal> spendingByCategory = transactions.stream()
                    .filter(tx -> tx.getType() == TransactionEntity.TransactionType.DEBIT)
                    .collect(Collectors.groupingBy(
                            tx -> tx.getCategory() != null ? tx.getCategory().name() : "AUTRE",
                            Collectors.mapping(TransactionEntity::getAmount,
                                    Collectors.reducing(BigDecimal.ZERO,
                                            (a, b) -> a.add(b.abs())))
                    ));

            BigDecimal totalSpend = spendingByCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            Table spendTable = new Table(UnitValue.createPercentArray(new float[]{50, 30, 20})).useAllAvailableWidth();
            addTableHeader(spendTable, "Catégorie", "Montant", "% du total");
            spendingByCategory.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(entry -> {
                        double pct = totalSpend.compareTo(BigDecimal.ZERO) > 0
                                ? entry.getValue().divide(totalSpend, 4, RoundingMode.HALF_UP).doubleValue() * 100
                                : 0;
                        spendTable.addCell(entry.getKey());
                        spendTable.addCell(new Cell().add(new Paragraph(formatEur(entry.getValue())).setTextAlignment(TextAlignment.RIGHT)));
                        spendTable.addCell(new Cell().add(new Paragraph(String.format("%.1f%%", pct)).setTextAlignment(TextAlignment.RIGHT)));
                    });
            doc.add(spendTable);
            doc.add(new Paragraph(" "));

            // ── Invoices summary ──────────────────────────────────────────────
            addSectionTitle(doc, "3. Récapitulatif Factures");
            long overdueCount = invoices.stream().filter(i -> i.getStatus() == InvoiceEntity.InvoiceStatus.OVERDUE).count();
            long sentCount = invoices.stream().filter(i -> i.getStatus() == InvoiceEntity.InvoiceStatus.SENT).count();
            long paidCount = invoices.stream().filter(i -> i.getStatus() == InvoiceEntity.InvoiceStatus.PAID).count();
            BigDecimal outstanding = invoiceRepository.sumOutstandingByUserId(userId);

            Table invTable = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            addTableRow(invTable, "Factures en retard", String.valueOf(overdueCount), true);
            addTableRow(invTable, "Factures envoyées (en attente)", String.valueOf(sentCount), false);
            addTableRow(invTable, "Factures payées", String.valueOf(paidCount), true);
            addTableRow(invTable, "Encours total (TTC)", formatEur(outstanding), false);
            doc.add(invTable);
            doc.add(new Paragraph(" "));

            // ── Recommendations ───────────────────────────────────────────────
            if (summary.actions() != null && !summary.actions().isEmpty()) {
                addSectionTitle(doc, "4. Recommandations");
                summary.actions().stream().limit(5).forEach(action -> {
                    doc.add(new Paragraph("• " + action.description())
                            .setFontSize(10)
                            .setMarginLeft(16));
                    if (action.estimatedImpact() != null) {
                        doc.add(new Paragraph("  Impact estimé : " + formatEur(action.estimatedImpact()))
                                .setFontSize(9)
                                .setFontColor(ColorConstants.GRAY)
                                .setMarginLeft(24));
                    }
                });
                doc.add(new Paragraph(" "));
            }

            // ── Footer ────────────────────────────────────────────────────────
            doc.add(new Paragraph("Ce rapport a été généré automatiquement par FlowGuard. Les projections sont basées sur les données disponibles.")
                    .setFontSize(8)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
        } finally {
            doc.close();
        }

        return out.toByteArray();
    }

    private void addSectionTitle(Document doc, String title) {
        doc.add(new Paragraph(title)
                .setFontSize(13)
                .setBold()
                .setFontColor(ACCENT)
                .setMarginTop(8)
                .setMarginBottom(6));
    }

    private void addTableHeader(Table table, String... headers) {
        for (String header : headers) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(header).setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(HEADER_BG));
        }
    }

    private void addTableRow(Table table, String label, String value, boolean alt) {
        DeviceRgb bg = alt ? ROW_ALT : ColorConstants.WHITE;
        table.addCell(new Cell().add(new Paragraph(label)).setBackgroundColor(bg));
        table.addCell(new Cell().add(new Paragraph(value).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(bg));
    }

    private String formatEur(BigDecimal amount) {
        if (amount == null) return "—";
        return String.format("%,.2f €", amount.doubleValue()).replace(',', '\u00A0').replace('.', ',');
    }
}
