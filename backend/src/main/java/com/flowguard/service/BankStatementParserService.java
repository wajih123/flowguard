package com.flowguard.service;

import com.flowguard.domain.TransactionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.jboss.logging.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

/**
 * Parses bank statements in multiple formats into a common list of parsed rows.
 * <p>
 * Supported formats:
 * <ul>
 * <li>CSV – generic comma/semicolon-separated, FlowGuard template or
 * bank-native</li>
 * <li>OFX – Open Financial Exchange (used by BNP, Crédit Agricole, Société
 * Générale, La Banque Postale…)</li>
 * <li>QIF – Quicken Interchange Format (CIC, Crédit Mutuel, LCL…)</li>
 * <li>MT940 – SWIFT messaging format (corporate / business accounts)</li>
 * <li>CFONB – French banking standard 120-char records (Banque de France, some
 * credit unions)</li>
 * <li>XLSX / XLS – Excel exports (Qonto, Shine, N26, Revolut…)</li>
 * <li>PDF – Text-extractable PDFs from major French banks (heuristic)</li>
 * </ul>
 */
@ApplicationScoped
public class BankStatementParserService {

    private static final Logger LOG = Logger.getLogger(BankStatementParserService.class);

    @jakarta.inject.Inject
    AiBankNormalizerService aiNormalizer;

    // === Date formatters used across banks ===
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"));

    /**
     * Common output record.
     * {@code balanceAfter} is the bank-certified running balance immediately after
     * this transaction, as printed on the statement (PDF col 2 / OFX AVAILBAL).
     * It is {@code null} for formats that do not include a running balance column.
     */
    public record ParsedRow(LocalDate date, String label, BigDecimal amount, String type, BigDecimal balanceAfter) {
    }

    // ──────────────────────────────────────────────────────────────────
    // Public dispatcher
    // ──────────────────────────────────────────────────────────────────

    /**
     * Returns the detected format name for display/logging purposes.
     */
    public String detectFormat(String filename, byte[] firstBytes) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".ofx") || lower.endsWith(".qfx"))
            return "OFX";
        if (lower.endsWith(".qif"))
            return "QIF";
        if (lower.endsWith(".mt940") || lower.endsWith(".sta") || lower.endsWith(".mt9"))
            return "MT940";
        if (lower.endsWith(".c120") || lower.endsWith(".cfonb"))
            return "CFONB";
        if (lower.endsWith(".xlsx"))
            return "XLSX";
        if (lower.endsWith(".xls"))
            return "XLS";
        if (lower.endsWith(".pdf"))
            return "PDF";

        // Sniff content
        String head = new String(firstBytes, StandardCharsets.UTF_8).trim();
        if (head.startsWith("<OFX") || head.contains("OFXHEADER"))
            return "OFX";
        if (head.startsWith("!Type:") || head.startsWith("!Account"))
            return "QIF";
        if (head.startsWith(":20:") || head.contains(":60F:"))
            return "MT940";
        if (firstBytes.length >= 4 && firstBytes[0] == 0x25 && firstBytes[1] == 0x50
                && firstBytes[2] == 0x44 && firstBytes[3] == 0x46)
            return "PDF"; // %PDF
        if (firstBytes.length >= 4 && firstBytes[0] == 0x50 && firstBytes[1] == 0x4B)
            return "XLSX"; // PK zip
        return "CSV";
    }

    /**
     * Main entry point. Parses any supported format and returns a list of rows.
     */
    public List<ParsedRow> parse(String filename, InputStream inputStream) throws IOException {
        // Buffer entire file so we can sniff and replay
        byte[] bytes = inputStream.readAllBytes();
        String format = detectFormat(filename, bytes.length > 512 ? Arrays.copyOf(bytes, 512) : bytes);

        LOG.infof("Parsing bank statement '%s' as format %s (%d bytes)", filename, format, bytes.length);

        return switch (format) {
            case "OFX" -> parseOFX(bytes);
            case "QIF" -> parseQIF(bytes);
            case "MT940" -> parseMT940(bytes);
            case "CFONB" -> parseCFONB(bytes);
            case "XLSX" -> parseXLSX(bytes, false);
            case "XLS" -> parseXLSX(bytes, true);
            case "PDF" -> parsePDF(bytes);
            default -> parseCSV(bytes);
        };
    }

    // ──────────────────────────────────────────────────────────────────
    // CSV parser — AI column detection + heuristic fallback
    // ──────────────────────────────────────────────────────────────────

    List<ParsedRow> parseCSV(byte[] bytes) throws IOException {
        // Try UTF-8 first, fall back to ISO-8859-1 (French banks often use Latin-1)
        String content;
        try {
            content = new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            content = new String(bytes, Charset.forName("ISO-8859-1"));
        }

        List<ParsedRow> rows = new ArrayList<>();
        String[] rawLines = content.split("\r?\n");
        char delimiter = guessDelimiter(rawLines);

        // Collect non-blank split lines
        List<String[]> lines = new ArrayList<>();
        for (String l : rawLines) {
            if (!l.trim().isBlank())
                lines.add(splitCSV(l.trim(), delimiter));
        }
        if (lines.isEmpty())
            return rows;

        // Extract headers (first non-blank line)
        List<String> headers = new ArrayList<>();
        for (String h : lines.get(0))
            headers.add(h.replaceAll("[\"']", "").trim());

        // Collect up to 5 sample data rows for AI
        List<List<String>> sampleRows = new ArrayList<>();
        for (int i = 1; i <= Math.min(5, lines.size() - 1); i++) {
            List<String> row = new ArrayList<>();
            for (String c : lines.get(i))
                row.add(c.replaceAll("[\"']", "").trim());
            sampleRows.add(row);
        }

        // === Column mapping: AI first, heuristic fallback ===
        int dateCol = -1, labelCol = -1, amountCol = -1, typeCol = -1;
        int debitCol = -1, creditCol = -1;

        if (!sampleRows.isEmpty()) {
            Map<Integer, String> mapping = aiNormalizer.detectColumnMapping(headers, sampleRows);
            dateCol = colByRole(mapping, "date");
            labelCol = colByRole(mapping, "label");
            amountCol = colByRole(mapping, "amount");
            typeCol = colByRole(mapping, "type");
            debitCol = colByRole(mapping, "debit");
            creditCol = colByRole(mapping, "credit");
        }

        if (dateCol < 0) { // AI unavailable or returned no useful mapping
            for (int c = 0; c < headers.size(); c++) {
                String h = headers.get(c).toLowerCase();
                if (h.matches("date.*"))
                    dateCol = c;
                else if (h.matches("(libellé|label|description|opération|operation|motif|intitulé).*"))
                    labelCol = c;
                else if (h.matches("(montant|amount|valeur).*") && debitCol < 0)
                    amountCol = c;
                else if (h.matches("(type|sens).*"))
                    typeCol = c;
                else if (h.matches("(débit|debit|retrait|sortie).*"))
                    debitCol = c;
                else if (h.matches("(crédit|credit|versement|entrée).*"))
                    creditCol = c;
            }
            if (dateCol < 0 && headers.size() >= 4) {
                dateCol = 0;
                labelCol = 1;
                amountCol = 2;
                typeCol = 3;
            }
        }
        if (dateCol < 0)
            return rows;

        // === Parse data rows (skip header at index 0) ===
        for (int i = 1; i < lines.size(); i++) {
            String[] cols = lines.get(i);
            int maxCol = Math.max(dateCol, Math.max(
                    labelCol < 0 ? 0 : labelCol, Math.max(
                            amountCol < 0 ? 0 : amountCol, Math.max(
                                    debitCol < 0 ? 0 : debitCol,
                                    creditCol < 0 ? 0 : creditCol))));
            if (cols.length <= maxCol)
                continue;

            try {
                LocalDate date = parseDate(cols[dateCol].replaceAll("[\"']", "").trim());
                if (date == null)
                    continue;
                String label = labelCol >= 0 ? cols[labelCol].replaceAll("[\"']", "").trim() : "Opération";
                if (label.isBlank())
                    label = "Opération";

                BigDecimal amount;
                String type;

                if (debitCol >= 0 || creditCol >= 0) {
                    String debitStr = debitCol >= 0 && debitCol < cols.length
                            ? cols[debitCol].replaceAll("[\"'\\s€]", "").replace(",", ".").trim()
                            : "";
                    String creditStr = creditCol >= 0 && creditCol < cols.length
                            ? cols[creditCol].replaceAll("[\"'\\s€]", "").replace(",", ".").trim()
                            : "";
                    if (!creditStr.isBlank() && !creditStr.equals("0") && !creditStr.equals("0.00")) {
                        amount = new BigDecimal(creditStr).abs();
                        type = "CREDIT";
                    } else if (!debitStr.isBlank() && !debitStr.equals("0") && !debitStr.equals("0.00")) {
                        amount = new BigDecimal(debitStr).abs();
                        type = "DEBIT";
                    } else
                        continue;
                } else if (amountCol >= 0 && amountCol < cols.length) {
                    String rawAmount = cols[amountCol].replaceAll("[\"'\\s€]", "").replace(",", ".").trim();
                    if (rawAmount.isBlank())
                        continue;
                    BigDecimal raw = new BigDecimal(rawAmount);
                    amount = raw.abs();
                    if (typeCol >= 0 && typeCol < cols.length) {
                        String t = cols[typeCol].replaceAll("[\"']", "").trim().toUpperCase();
                        type = t.contains("CREDIT") || t.contains("CR") ? "CREDIT" : "DEBIT";
                    } else {
                        type = raw.signum() >= 0 ? "CREDIT" : "DEBIT";
                    }
                } else
                    continue;

                rows.add(new ParsedRow(date, label, amount, type, null));
            } catch (Exception e) {
                LOG.debugf("CSV skip line %d: %s", i, e.getMessage());
            }
        }
        return rows;
    }

    // ──────────────────────────────────────────────────────────────────
    // OFX parser (Open Financial Exchange — XML variant)
    // Used by: BNP Paribas, Crédit Agricole, Société Générale, La Banque Postale,
    // Boursorama, LCL
    // ──────────────────────────────────────────────────────────────────

    List<ParsedRow> parseOFX(byte[] bytes) {
        List<ParsedRow> rows = new ArrayList<>();
        String content = new String(bytes, StandardCharsets.UTF_8);

        // OFX can be SGML (no closing tags) or XML. We handle both with regex.
        Pattern stmtTrn = Pattern.compile(
                "<STMTTRN>.*?</STMTTRN>|<STMTTRN>(.*?)(?=<STMTTRN>|</BANKTRANLIST>)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern trntype = Pattern.compile("<TRNTYPE>(.*?)</TRNTYPE>|<TRNTYPE>(\\S+)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern dtposted = Pattern.compile("<DTPOSTED>(.*?)</DTPOSTED>|<DTPOSTED>(\\S+)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern trnamt = Pattern.compile("<TRNAMT>(.*?)</TRNAMT>|<TRNAMT>([-\\d.]+)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern memo = Pattern.compile("<MEMO>(.*?)</MEMO>|<MEMO>(.*?)(?=<|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern name = Pattern.compile("<NAME>(.*?)</NAME>|<NAME>(.*?)(?=<|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher m = stmtTrn.matcher(content);
        while (m.find()) {
            String block = m.group();
            try {
                String type = extractFirst(trntype, block, "DEBIT").trim().toUpperCase();
                String rawDate = extractFirst(dtposted, block, "").trim();
                String rawAmt = extractFirst(trnamt, block, "0").trim();
                String memoStr = extractFirst(memo, block, "");
                String nameStr = extractFirst(name, block, "");
                String label = (!memoStr.isBlank() ? memoStr : nameStr).trim();
                if (label.isBlank())
                    label = "Opération";

                // OFX dates: YYYYMMDDHHmmss[.xxx][+offset] — take first 8 chars
                if (rawDate.length() >= 8)
                    rawDate = rawDate.substring(0, 8);
                LocalDate date = parseDate(rawDate);
                if (date == null)
                    continue;

                BigDecimal rawAmount = new BigDecimal(rawAmt.replace(",", "."));
                BigDecimal amount = rawAmount.abs();
                // OFX TRNTYPE: DEBIT, CREDIT, INT, DIV, FEE, SRVCHG, DEP, ATM, POS, XFER,
                // CHECK, PAYMENT, CASH, DIRECTDEP, DIRECTDEBIT
                String txType;
                if (type.equals("CREDIT") || type.equals("INT") || type.equals("DIV")
                        || type.equals("DEP") || type.equals("DIRECTDEP")) {
                    txType = "CREDIT";
                } else if (rawAmount.signum() > 0) {
                    txType = "CREDIT";
                } else {
                    txType = "DEBIT";
                }
                rows.add(new ParsedRow(date, label, amount, txType, null));
            } catch (Exception e) {
                LOG.debugf("OFX skip transaction: %s", e.getMessage());
            }
        }
        return rows;
    }

    // ──────────────────────────────────────────────────────────────────
    // QIF parser (Quicken Interchange Format)
    // Used by: CIC, Crédit Mutuel, LCL, Banque Populaire
    // ──────────────────────────────────────────────────────────────────

    List<ParsedRow> parseQIF(byte[] bytes) {
        List<ParsedRow> rows = new ArrayList<>();
        String content = new String(bytes, Charset.forName("ISO-8859-1")); // QIF is usually Latin-1

        String[] lines = content.split("\r?\n");
        LocalDate date = null;
        BigDecimal amount = null;
        String label = null;

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("!"))
                continue;
            char tag = line.charAt(0);
            String val = line.substring(1).trim();

            switch (tag) {
                case 'D' -> date = parseDate(val);
                case 'T' -> {
                    // Amount: may use ',' as decimal separator or '-' for debit
                    try {
                        amount = new BigDecimal(val.replace(",", ".").replace(" ", ""));
                    } catch (NumberFormatException ex) {
                        amount = null;
                    }
                }
                case 'P' -> label = val;
                case 'M' -> {
                    if (label == null || label.isBlank())
                        label = val;
                } // memo fallback
                case '^' -> { // end of transaction record
                    if (date != null && amount != null) {
                        String type = amount.signum() >= 0 ? "CREDIT" : "DEBIT";
                        rows.add(new ParsedRow(date, label != null ? label : "Opération", amount.abs(), type, null));
                    }
                    date = null;
                    amount = null;
                    label = null;
                }
            }
        }
        return rows;
    }

    // ──────────────────────────────────────────────────────────────────
    // MT940 parser (SWIFT Customer Account Statement)
    // Used by: corporate accounts, Société Générale Business, BNP Paribas Business
    // ──────────────────────────────────────────────────────────────────

    List<ParsedRow> parseMT940(byte[] bytes) {
        List<ParsedRow> rows = new ArrayList<>();
        String content = new String(bytes, StandardCharsets.ISO_8859_1);

        // Field :61: — Statement Line
        // Format: :61:YYMMDD[MMDD]<D/C><Amount>N<TransactionCode><Reference>
        Pattern f61 = Pattern.compile(":61:(\\d{6})(\\d{4})?([CDRScdrs])([A-Z]?)(\\d+[,.]\\d*)(N[A-Z0-9]+)(.+)?");
        // Field :86: — Information to Account Owner (narrative)
        Pattern f86 = Pattern.compile(":86:(.+?)(?=:6[01]:|$)", Pattern.DOTALL);

        String[] blocks = content.split("(?=:20:)");
        for (String block : blocks) {
            String[] lines = block.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher m61 = f61.matcher(lines[i]);
                if (!m61.find())
                    continue;

                String dateStr = "20" + m61.group(1); // YYMMDD → YYYYMMDD
                if (dateStr.length() == 8) {
                    // Use the optional MMDD (value date) if present, else booking date
                }
                LocalDate date = parseDate(dateStr.substring(0, 8));
                if (date == null)
                    continue;

                String dc = m61.group(3).toUpperCase();
                String rawAmt = m61.group(5).replace(",", ".");
                BigDecimal amount;
                try {
                    amount = new BigDecimal(rawAmt).abs();
                } catch (NumberFormatException e) {
                    continue;
                }

                // Look ahead for :86: narrative
                StringBuilder narrative = new StringBuilder();
                for (int j = i + 1; j < lines.length && !lines[j].startsWith(":"); j++) {
                    narrative.append(lines[j].trim()).append(" ");
                }
                // Inside :86: extract /name/ or whole text
                // Strip SWIFT field numbers like /BENM//<text>//REMI//<text>
                String label = narrative.toString()
                        .replaceAll("/[A-Z]+//", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                if (label.isBlank())
                    label = "Virement";

                String type = (dc.startsWith("C")) ? "CREDIT" : "DEBIT";
                rows.add(new ParsedRow(date, label.substring(0, Math.min(label.length(), 200)), amount, type, null));
            }
        }
        return rows;
    }

    // ──────────────────────────────────────────────────────────────────
    // CFONB-120 parser (French banking standard)
    // Used by: Banque de France, Caisse d'Épargne, some credit unions
    // ──────────────────────────────────────────────────────────────────

    List<ParsedRow> parseCFONB(byte[] bytes) {
        List<ParsedRow> rows = new ArrayList<>();
        String content = new String(bytes, Charset.forName("ISO-8859-1"));

        for (String line : content.split("\r?\n")) {
            if (line.length() < 120)
                continue;
            String recordType = line.substring(0, 2).trim();
            if (!recordType.equals("04"))
                continue; // 04 = transaction record

            try {
                // CFONB 120: positions are 1-indexed
                // Date opération: cols 6–11 (DDMMYY)
                String rawDate = line.substring(5, 11).trim();
                LocalDate date = parseCFONBDate(rawDate);
                if (date == null)
                    continue;

                // Sens (D/C): col 20
                char dc = line.charAt(19);
                String type = (dc == 'C') ? "CREDIT" : "DEBIT";

                // Montant: cols 34–45 (12 chars, right-justified, 2 implied decimals)
                String rawAmt = line.substring(33, 45).trim().replaceAll("^0+", "");
                if (rawAmt.isBlank())
                    rawAmt = "0";
                BigDecimal amount = new BigDecimal(rawAmt).movePointLeft(2).abs();

                // Libellé: cols 49–79 (31 chars)
                String label = line.substring(48, Math.min(79, line.length())).trim();
                if (label.isBlank())
                    label = "Opération";

                rows.add(new ParsedRow(date, label, amount, type, null));
            } catch (Exception e) {
                LOG.debugf("CFONB skip line: %s", e.getMessage());
            }
        }
        return rows;
    }

    // ──────────────────────────────────────────────────────────────────
    // Excel parser (XLSX / XLS) — AI column detection + heuristic fallback
    // Used by: Qonto, Shine, N26, Revolut, many online banks
    // ──────────────────────────────────────────────────────────────────

    List<ParsedRow> parseXLSX(byte[] bytes, boolean legacy) throws IOException {
        List<ParsedRow> rows = new ArrayList<>();
        Workbook wb = legacy
                ? new HSSFWorkbook(new ByteArrayInputStream(bytes))
                : new XSSFWorkbook(new ByteArrayInputStream(bytes));

        Sheet sheet = wb.getSheetAt(0);
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

        // === Find header row ===
        int headerRowIdx = 0;
        List<String> headers = new ArrayList<>();

        for (Row row : sheet) {
            if (row == null)
                continue;
            List<String> rowVals = new ArrayList<>();
            boolean looksLikeHeader = false;
            for (Cell cell : row) {
                String val = getCellString(cell, evaluator);
                rowVals.add(val.trim());
                String h = val.toLowerCase().trim();
                if (h.matches(
                        "date.*|libellé.*|label.*|description.*|montant.*|amount.*|débit.*|debit.*|crédit.*|credit.*|opération.*|operation.*")) {
                    looksLikeHeader = true;
                }
            }
            if (looksLikeHeader) {
                headerRowIdx = row.getRowNum();
                headers = rowVals;
                break;
            }
        }
        if (headers.isEmpty() && sheet.getPhysicalNumberOfRows() > 0) {
            Row r0 = sheet.getRow(0);
            if (r0 != null)
                for (Cell c : r0)
                    headers.add(getCellString(c, evaluator).trim());
        }

        // Collect up to 5 sample data rows for AI
        List<List<String>> sampleRows = new ArrayList<>();
        int dataStart = headerRowIdx + 1;
        for (int r = dataStart; r <= Math.min(dataStart + 4, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;
            List<String> rowData = new ArrayList<>();
            for (Cell cell : row)
                rowData.add(getCellString(cell, evaluator).trim());
            if (!rowData.isEmpty())
                sampleRows.add(rowData);
        }

        // === Column mapping: AI first, heuristic fallback ===
        int dateCol = -1, labelCol = -1, amountCol = -1, typeCol = -1;
        int debitCol = -1, creditCol = -1;

        if (!headers.isEmpty() && !sampleRows.isEmpty()) {
            Map<Integer, String> mapping = aiNormalizer.detectColumnMapping(headers, sampleRows);
            dateCol = colByRole(mapping, "date");
            labelCol = colByRole(mapping, "label");
            amountCol = colByRole(mapping, "amount");
            typeCol = colByRole(mapping, "type");
            debitCol = colByRole(mapping, "debit");
            creditCol = colByRole(mapping, "credit");
        }

        if (dateCol < 0) {
            for (int c = 0; c < headers.size(); c++) {
                String h = headers.get(c).toLowerCase();
                if (h.matches("date.*"))
                    dateCol = c;
                else if (h.matches("(libellé|label|description|opération|operation|motif).*"))
                    labelCol = c;
                else if (h.matches("(montant|amount|valeur).*") && debitCol < 0)
                    amountCol = c;
                else if (h.matches("(type|sens).*"))
                    typeCol = c;
                else if (h.matches("(débit|debit|retrait|sortie).*"))
                    debitCol = c;
                else if (h.matches("(crédit|credit|versement|entrée).*"))
                    creditCol = c;
            }
            if (dateCol < 0 && headers.size() >= 2) {
                dateCol = 0;
                labelCol = 1;
                amountCol = 2;
            }
        }
        if (dateCol < 0) {
            wb.close();
            return rows;
        }

        // === Parse all data rows ===
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;
            try {
                Cell dateCell = row.getCell(dateCol);
                if (dateCell == null)
                    continue;
                LocalDate date = parseDate(getCellString(dateCell, evaluator).trim());
                if (date == null)
                    continue;

                String label = labelCol >= 0 && row.getCell(labelCol) != null
                        ? getCellString(row.getCell(labelCol), evaluator).trim()
                        : "Opération";
                if (label.isBlank())
                    label = "Opération";

                BigDecimal amount;
                String type;

                if (debitCol >= 0 || creditCol >= 0) {
                    double d = debitCol >= 0 ? getCellNumeric(row.getCell(debitCol), evaluator) : 0;
                    double cr = creditCol >= 0 ? getCellNumeric(row.getCell(creditCol), evaluator) : 0;
                    if (cr != 0) {
                        amount = BigDecimal.valueOf(Math.abs(cr));
                        type = "CREDIT";
                    } else if (d != 0) {
                        amount = BigDecimal.valueOf(Math.abs(d));
                        type = "DEBIT";
                    } else
                        continue;
                } else {
                    double raw = amountCol >= 0 ? getCellNumeric(row.getCell(amountCol), evaluator) : 0;
                    if (raw == 0)
                        continue;
                    amount = BigDecimal.valueOf(Math.abs(raw));
                    if (typeCol >= 0 && row.getCell(typeCol) != null) {
                        String t = getCellString(row.getCell(typeCol), evaluator).trim().toUpperCase();
                        type = t.contains("CREDIT") || t.contains("CR") ? "CREDIT" : "DEBIT";
                    } else {
                        type = raw >= 0 ? "CREDIT" : "DEBIT";
                    }
                }

                rows.add(new ParsedRow(date, label, amount, type, null));
            } catch (Exception e) {
                LOG.debugf("XLSX skip row %d: %s", r, e.getMessage());
            }
        }
        wb.close();
        return rows;
    }

    // ──────────────────────────────────────────────────────────────────
    // PDF parser — AI primary, regex fallback
    // Covers: any French bank PDF with selectable text
    // ──────────────────────────────────────────────────────────────────

    List<ParsedRow> parsePDF(byte[] bytes) throws IOException {
        String text;
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        } catch (Exception e) {
            LOG.warnf("PDF text extraction error: %s", e.getMessage());
            return List.of();
        }

        // AI extraction understands any bank layout without rigid patterns
        if (aiNormalizer.isEnabled()) {
            List<ParsedRow> aiRows = aiNormalizer.extractFromText(text);
            if (!aiRows.isEmpty()) {
                LOG.infof("PDF parsed via AI: %d transactions", aiRows.size());
                return aiRows;
            }
            LOG.info("AI returned no transactions for PDF, trying regex fallback");
        }

        return extractTransactionsFromText(text);
    }

    /**
     * Heuristic line-by-line extraction from raw PDF text.
     * <p>
     * Pattern: line contains a date expression AND a monetary amount expression.
     * We handle French number formatting: 1 234,56 → 1234.56
     * </p>
     * Package-private for unit testing.
     */
    List<ParsedRow> extractTransactionsFromText(String text) {
        List<ParsedRow> rows = new ArrayList<>();

        // French date pattern: dd/MM/yyyy or dd.MM.yyyy or dd-MM-yyyy
        Pattern datePat = Pattern.compile("\\b(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})\\b");
        // French amount pattern — decimal separator is COMMA, thousands separator can
        // be space, non-breaking space, or PERIOD (BoursoBank uses period: 1.040,00).
        // Group 1 = sign, group 2 = integer part (with separators), group 3 = 2 decimal
        // digits, group 4 = optional D/C tag.
        Pattern amtPat = Pattern.compile("(-?)(\\d{1,3}(?:[.\\s\\u00A0]\\d{3})*)[,](\\d{2})\\s*([DCdc])?");

        String[] lines = text.split("\n");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.length() < 8)
                continue;

            Matcher dm = datePat.matcher(line);
            if (!dm.find())
                continue;

            LocalDate date = parseDate(dm.group(1));
            if (date == null)
                continue;

            // Find all amounts on this line
            List<BigDecimal> amounts = new ArrayList<>();
            List<String> amtTypes = new ArrayList<>();
            Matcher am = amtPat.matcher(line);
            while (am.find()) {
                try {
                    // Strip all thousands separators (space, NBSP, period) from integer part
                    String whole = am.group(2).replaceAll("[.\\s\\u00A0]", "");
                    String frac = am.group(3);
                    String sign = am.group(1);
                    String dcTag = am.group(4);

                    BigDecimal v = new BigDecimal(whole + "." + frac);
                    if (!sign.isEmpty())
                        v = v.negate();

                    String t;
                    if (dcTag != null) {
                        t = dcTag.equalsIgnoreCase("C") ? "CREDIT" : "DEBIT";
                    } else {
                        t = v.signum() >= 0 ? "CREDIT" : "DEBIT";
                    }
                    amounts.add(v.abs());
                    amtTypes.add(t);
                } catch (NumberFormatException ignored) {
                }
            }

            if (amounts.isEmpty())
                continue;

            // In French bank statement PDFs each line has: [transaction_amount]
            // [running_balance]
            // The FIRST amount is always the transaction; the last is the running balance.
            // Exception: single amount on the line → that IS the transaction.
            BigDecimal amount = amounts.get(0);
            String type = amtTypes.get(0);

            // Label: everything between the date match end and the first amount.
            // Guard: amtIdx may be < dm.end() when the amount appears inside/before the
            // date token.
            Matcher firstAmt = amtPat.matcher(line);
            int amtIdx = firstAmt.find() ? firstAmt.start() : line.length();
            int labelStart = dm.end();
            int labelEnd = Math.min(amtIdx, line.length());
            String label = (labelEnd > labelStart)
                    ? line.substring(labelStart, labelEnd).trim()
                    : "";
            label = label.replaceAll("\\s+", " ").replaceAll("[|#*]", "").trim();
            if (label.isBlank())
                label = "Opération PDF";
            if (label.length() > 200)
                label = label.substring(0, 200);

            // Skip obviously non-transaction lines (balance totals, bank header text).
            // Check both the extracted label AND the full line: a "Solde precedent" line
            // has
            // the keyword before the matched date, so it would be absent from the label
            // slice.
            String lineLow = line.toLowerCase();
            if (label.toLowerCase().matches(".*(solde|balance|total|report|page|iban|bic|intitulé).*")
                    || lineLow.matches(".*(solde|balance|total|report|page|iban|bic|intitulé).*"))
                continue;
            // Skip currency-exchange rate continuation lines, e.g.:
            // "136,00 PLN / 1 euro = 4,210526315" — these appear below foreign CARTE rows
            if (lineLow.matches(".*\\d+[,.]\\d+\\s+[a-z]{2,4}\\s*/\\s*1\\s*euro.*"))
                continue;

            // Keyword-based type override: French banking labels carry reliable
            // DEBIT/CREDIT signals.
            // This corrects cases where amounts are always positive (no sign in the PDF
            // column).
            String keywordType = inferTypeFromLabel(label);
            if (keywordType != null)
                type = keywordType;

            // Gap 2: capture the second (running-balance) amount printed on PDF lines.
            // BoursoBank and most French banks print: [tx_amount] … [label] … [balance]
            BigDecimal balanceAfter = amounts.size() > 1 ? amounts.get(amounts.size() - 1) : null;
            rows.add(new ParsedRow(date, label, amount, type, balanceAfter));
        }
        return rows;
    }

    // ──────────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────────

    /**
     * Infers DEBIT or CREDIT from common French banking label prefixes/keywords.
     * Returns {@code null} when the label gives no strong signal (caller keeps
     * the sign-based or D/C-tag value).
     * Package-private for testing.
     */
    static String inferTypeFromLabel(String label) {
        String l = label.toUpperCase();
        // ── Strong DEBIT signals ───────────────────────────────────────
        if (l.startsWith("PRLV") || l.startsWith("PRELEVEMENT") || l.startsWith("PRÉLÈVEMENT")
                || l.startsWith("CARTE ") || l.startsWith("CB ")
                || l.contains("RETRAIT") || l.contains("DAB")
                || l.startsWith("FRAIS ") || l.startsWith("CHQ ")
                || l.startsWith("CHEQUE") || l.startsWith("CHÈQUE")
                || l.contains("VIREMENT EMIS") || l.contains("VIR EMIS")
                || l.contains("PAIEMENT CB") || l.contains("PAIEMENT PAR CARTE")) {
            return "DEBIT";
        }
        // ── Strong CREDIT signals ──────────────────────────────────────
        // NOTE: "VIR IN" / "VIR INST" alone is NOT a reliable CREDIT signal —
        // BoursoBank uses "VIR INST ALAIN BALTEAU" for outgoing transfers (DEBIT)
        // and "VIR INST MONSIEUR TARSIM WAJIH" for self incoming transfers (CREDIT).
        // Rule: BoursoBank adds civility prefix (MONSIEUR/MME/M ) to the SENDER,
        // so VIR INST + civility = CREDIT; VIR INST + bare name = ambiguous.
        if (l.contains("AVOIR") || l.contains("REMBOURSEMENT") || l.contains("REMISE")
                || l.contains("VIR RECU") || l.contains("VIR REÇU")
                || l.contains("VIREMENT DEPUIS") || l.contains("VIR DEPUIS")
                || l.contains("VIR INST MONSIEUR") || l.contains("VIR INST MME")
                || l.contains("VIR INST M ") // abbreviated civility
                || l.contains("SALAIRE") || l.contains("DEPOT") || l.contains("DÉPÔT")
                || l.contains("REMUNERATION") || l.contains("RÉMUNÉRATION")
                || l.startsWith("PRIME ")) { // Prime Parrainage, Prime de remboursement…
            return "CREDIT";
        }
        return null; // no strong signal (VIR INST without civility = ambiguous)
    }

    /**
     * Infers a FlowGuard transaction category from French banking label keywords.
     * Calibrated against real BoursoBank statements (Dec 2025 – Feb 2026).
     * Returns {@code null} when no category can be reliably inferred (callers use
     * AUTRE).
     * Package-private for testing.
     */
    static TransactionEntity.TransactionCategory inferCategoryFromLabel(String label) {
        if (label == null || label.isBlank())
            return null;
        String l = label.toUpperCase();

        // ── CHARGES_FISCALES — loan repayments, taxes, overdue fees ─────
        // Check first: "ECHEANCE PRET" would otherwise partially match other rules.
        if (l.contains("ECHEANCE PRET") || l.contains("ECHEANCE CREDIT")
                || l.contains("MENSUALITE PRET") || l.contains("REMBOURSEMENT PRET")
                || l.contains("IMPAYE CB") // ONEY overdue payment
                || (l.contains("ONEY") && (l.contains("3X") || l.contains("4X") || l.contains("FINANCEMT")))
                || l.contains("URSSAF") || l.contains(" RSI ") || l.contains("CIPAV")
                || l.contains("IMPOT") || l.contains("IMPÔT") || l.contains("DGFIP")
                || l.contains("TRESOR PUBLIC") || l.contains("TRÉSOR PUBLIC")
                || l.contains("TAXE") || l.contains("TVA") || l.contains("AMENDE")) {
            return TransactionEntity.TransactionCategory.CHARGES_FISCALES;
        }

        // ── ENERGIE — utility bills ──────────────────────────────────────
        if (l.contains("TOTALENERGIES") || l.contains("TOTAL ENERGIE")
                || l.contains("EDF") || l.contains("ENGIE")
                || l.contains("DIRECT ENERGIE") || l.contains("ELECTRICITE")
                || (l.contains("GAZ") && l.contains("FRANCE"))) {
            return TransactionEntity.TransactionCategory.ENERGIE;
        }

        // ── ASSURANCE — insurance policies ──────────────────────────────
        if (l.contains("AVANSSUR") || l.contains("DIRECT ASSURANC")
                || l.contains("LOLIVIER") || l.contains("MAIF") || l.contains("MATMUT")
                || l.contains("AXA") || l.contains("ALLIANZ") || l.contains("GENERALI")
                || l.contains("MACIF") || l.contains("ASSURANCE") || l.contains("MUTUELLE")) {
            return TransactionEntity.TransactionCategory.ASSURANCE;
        }

        // ── TELECOM — phone, internet, hosting ──────────────────────────
        if (l.contains("SFR") || l.contains("ORANGE") || l.contains("BOUYGUES TELECOM")
                || l.contains("FREE MOBILE") || l.contains("FREE SAS") || l.contains("ILIAD")
                || l.contains("SOSH") || l.contains("MAPBOX") || l.contains("GODADDY")
                || l.contains("GANDI") || l.contains("OVH")) {
            return TransactionEntity.TransactionCategory.TELECOM;
        }

        // ── ABONNEMENT — subscriptions ───────────────────────────────────
        if (l.contains("SPOTIFY") || l.contains("NETFLIX") || l.contains("AMAZON PRIME")
                || l.contains("APPLE.COM/") || l.contains("APPLE.COM/BILL")
                || l.contains("DELIVEROO PLUS")
                || l.contains("WODIFY") || l.contains("CROSSF") // CrossFit
                || l.contains("WELLPASS") // gym network
                || l.contains("DAZN") || l.contains("MOLOTOV")
                || l.contains("GITHUB") || l.contains("HETZNER")
                || (l.contains("PAYPAL") && (l.contains("GITHUB") || l.contains("HETZNER")))) {
            return TransactionEntity.TransactionCategory.ABONNEMENT;
        }

        // ── ALIMENTATION — food, groceries, restaurants, food delivery ───
        if (l.contains("LIDL") || l.contains("CARREFOUR") || l.contains("LECLERC")
                || l.contains("MONOPRIX") || l.contains("MONOP") || l.contains("VIVAL")
                || l.contains("INTERMARCHE") || l.contains("AUCHAN") || l.contains("CASINO")
                || l.contains("FRANPRIX") || l.contains("HALLES") || l.contains("BOUCHERIE")
                || l.contains("FOURNIL") || l.contains("BOULANGERIE") || l.contains("EPICERIE")
                || l.contains("PRIMEUR") || l.contains("ALIMENTATION") || l.contains("SUPERMARCHE")
                || l.contains("DELIVEROO") // kept here (not Deliveroo Plus — checked above)
                || l.contains("UBER * EATS") || l.contains("UBER EATS")
                || l.contains("JUST EAT") || l.contains("MCDONALDS")
                || l.contains("PYSZNE") // Polish food delivery
                || l.contains("NABULIO") || l.contains("RESTAURANT") || l.contains("BRASSEUR")
                || l.contains("SUSHI") || l.contains("PIZZA") || l.contains("TRAITEUR")
                || l.contains("UTILE") // Utile supermarket chain
                || l.contains("MAXICOFFEE") // coffee machine vending
                || l.contains("JEAN ROMEO") // local grocery Nice
                || l.contains("SC LA PETITE") // café/snack
                || l.contains("DI PIU") || l.contains("LEONIS") || l.contains("BERCO")
                || l.contains("LDC") || l.contains("MAEL") || l.contains("SOHO")) {
            return TransactionEntity.TransactionCategory.ALIMENTATION;
        }

        // ── TRANSPORT — mobility, parking, fuel, travel ──────────────────
        if (l.contains("BOLT.EU") || l.contains("UBER")
                || l.contains("SERVICE NAVIGO") || l.contains("NAVIGO")
                || l.contains("SNCF") || l.contains("RATP") || l.contains("TRANSILIEN")
                || l.contains("AIR FRANCE") || l.contains("EASYJET") || l.contains("RYANAIR")
                || l.contains("SHELL") || l.contains("ESSO")
                || l.contains("PAYBYPHONE") || l.contains("PBP_") // parking
                || l.contains("INDIGO") // parking operator
                || l.contains("AUTOROUTES") || l.contains("ASF")
                || l.contains("GETYOURGUIDE") // travel tours
                || l.contains("IBIS") || l.contains("NOVOTEL") || l.contains("ACCOR")
                || l.contains("BOOKING") || l.contains("AIRBNB")
                || l.contains("BILET.INTERCI")) { // Polish transport ticketing
            return TransactionEntity.TransactionCategory.TRANSPORT;
        }

        // ── LOYER — rent ─────────────────────────────────────────────────
        if (l.startsWith("LOYER") || l.contains("QUITTANCE") || l.contains("FONCIERE")) {
            return TransactionEntity.TransactionCategory.LOYER;
        }

        // ── SALAIRE — salary / income ─────────────────────────────────────
        if (l.contains("SALAIRE") || l.contains("REMUNERATION") || l.contains("RÉMUNÉRATION")) {
            return TransactionEntity.TransactionCategory.SALAIRE;
        }

        // ── VIREMENT — transfers, ATM withdrawals, refunds ───────────────
        if (l.startsWith("VIR ") || l.startsWith("VIR INST") || l.startsWith("VIREMENT")
                || l.contains("RETRAIT DAB") || l.contains("RETRAIT CB")
                || l.startsWith("AVOIR ")) {
            return TransactionEntity.TransactionCategory.VIREMENT;
        }

        return null; // no reliable match → caller uses AUTRE
    }

    /**
     * Returns column index for the given semantic role from an AI mapping, or -1.
     */
    private int colByRole(Map<Integer, String> mapping, String role) {
        for (Map.Entry<Integer, String> e : mapping.entrySet()) {
            if (role.equalsIgnoreCase(e.getValue()))
                return e.getKey();
        }
        return -1;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        raw = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    /** CFONB dates are DDMMYY. */
    private LocalDate parseCFONBDate(String ddmmyy) {
        if (ddmmyy.length() != 6)
            return null;
        try {
            int day = Integer.parseInt(ddmmyy.substring(0, 2));
            int month = Integer.parseInt(ddmmyy.substring(2, 4));
            int year = 2000 + Integer.parseInt(ddmmyy.substring(4, 6));
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private char guessDelimiter(String[] lines) {
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("!"))
                continue;
            long commas = line.chars().filter(c -> c == ',').count();
            long semicolons = line.chars().filter(c -> c == ';').count();
            return semicolons > commas ? ';' : ',';
        }
        return ',';
    }

    /**
     * Simple CSV split respecting double-quoted fields.
     */
    private String[] splitCSV(String line, char delimiter) {
        List<String> cols = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuote = !inQuote;
            } else if (ch == delimiter && !inQuote) {
                cols.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        cols.add(sb.toString());
        return cols.toArray(new String[0]);
    }

    private String extractFirst(Pattern p, String text, String defaultVal) {
        Matcher m = p.matcher(text);
        if (!m.find())
            return defaultVal;
        for (int i = 1; i <= m.groupCount(); i++) {
            String g = m.group(i);
            if (g != null && !g.isBlank())
                return g.trim();
        }
        return defaultVal;
    }

    private String getCellString(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null)
            return "";
        CellValue cv = evaluator.evaluate(cell);
        if (cv == null)
            return cell.toString();
        return switch (cv.getCellType()) {
            case STRING -> cv.getStringValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate d = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield d.toString();
                }
                double n = cv.getNumberValue();
                yield n == Math.floor(n) ? String.valueOf((long) n) : String.valueOf(n);
            }
            case BOOLEAN -> String.valueOf(cv.getBooleanValue());
            default -> "";
        };
    }

    private double getCellNumeric(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null)
            return 0;
        try {
            CellValue cv = evaluator.evaluate(cell);
            if (cv == null)
                return 0;
            if (cv.getCellType() == CellType.NUMERIC)
                return cv.getNumberValue();
            String s = cv.getStringValue().replaceAll("[\\s€]", "").replace(",", ".");
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
