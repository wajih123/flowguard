package fr.flowguard.accountant.resource;

import fr.flowguard.accountant.entity.AccountantGrantEntity;
import fr.flowguard.banking.entity.BankAccountEntity;
import fr.flowguard.banking.entity.TransactionEntity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Tag(name = "FEC Export", description = "Export comptable FEC")
public class FecExportResource {

    private static final DateTimeFormatter FEC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SEP = "|";
    private static final String CRLF = "\r\n";

    @Inject
    JsonWebToken jwt;

    /**
     * Token-authenticated FEC portal endpoint for accountants.
     * No JWT required — authenticated via X-Accountant-Token header.
     */
    @GET
    @Path("/api/accountant/portal/fec")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Telechargement FEC via jeton comptable")
    public Response portalFec(@QueryParam("year") int year,
                               @HeaderParam("X-Accountant-Token") String token) {
        if (token == null || token.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("X-Accountant-Token header is required").build();
        }
        AccountantGrantEntity grant = AccountantGrantEntity.findByToken(token);
        if (grant == null || grant.isExpired()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Invalid or expired accountant token").build();
        }
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        String fec = buildFec(grant.userId, from, to);
        return Response.ok(fec)
                .header("Content-Disposition", "attachment; filename=\"FEC_" + year + ".txt\"")
                .build();
    }

    /**
     * Owner-authenticated FEC export via JWT.
     */
    @GET
    @Path("/api/export/fec")
    @Produces(MediaType.TEXT_PLAIN)
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Export FEC (proprietaire)")
    public Response ownerFec(@QueryParam("from") String fromStr,
                              @QueryParam("to") String toStr) {
        String userId = jwt.getSubject();
        LocalDate from = fromStr != null ? LocalDate.parse(fromStr) : LocalDate.now().withDayOfYear(1);
        LocalDate to = toStr != null ? LocalDate.parse(toStr) : LocalDate.now();
        String fec = buildFec(userId, from, to);
        return Response.ok(fec)
                .header("Content-Disposition", "attachment; filename=\"FEC_" + from + "_" + to + ".txt\"")
                .build();
    }

    private String buildFec(String userId, LocalDate from, LocalDate to) {
        List<BankAccountEntity> accounts = BankAccountEntity.findActiveByUserId(userId);
        StringBuilder sb = new StringBuilder();
        // FEC header
        sb.append("JournalCode").append(SEP)
          .append("JournalLib").append(SEP)
          .append("EcritureNum").append(SEP)
          .append("EcritureDate").append(SEP)
          .append("CompteNum").append(SEP)
          .append("CompteLib").append(SEP)
          .append("CompAuxNum").append(SEP)
          .append("CompAuxLib").append(SEP)
          .append("PieceRef").append(SEP)
          .append("PieceDate").append(SEP)
          .append("EcritureLib").append(SEP)
          .append("Debit").append(SEP)
          .append("Credit").append(SEP)
          .append("EcritureLet").append(SEP)
          .append("DateLet").append(SEP)
          .append("ValidDate").append(SEP)
          .append("Montantdevise").append(SEP)
          .append("Idevise")
          .append(CRLF);

        AtomicInteger lineNum = new AtomicInteger(1);
        for (BankAccountEntity account : accounts) {
            List<TransactionEntity> txns = TransactionEntity
                    .find("accountId = ?1 AND transactionDate >= ?2 AND transactionDate <= ?3 ORDER BY transactionDate ASC",
                            account.id, from, to)
                    .list();
            for (TransactionEntity tx : txns) {
                String journalCode = resolveJournalCode(tx.category);
                String journalLib = resolveJournalLib(tx.category);
                String ecritureNum = String.format("E%08d", lineNum.getAndIncrement());
                String ecritureDate = tx.transactionDate.format(FEC_DATE);
                String compteNum = resolveCompteNum(tx.amount, tx.category);
                String compteLib = resolveCompteLib(tx.amount, tx.category);
                String label = tx.label != null ? tx.label.replace(SEP, " ") : "";
                String pieceRef = tx.externalId != null ? tx.externalId : ecritureNum;

                BigDecimal abs = tx.amount.abs();
                String debit = tx.amount.compareTo(BigDecimal.ZERO) < 0 ? formatAmount(abs) : "0.00";
                String credit = tx.amount.compareTo(BigDecimal.ZERO) >= 0 ? formatAmount(abs) : "0.00";

                sb.append(journalCode).append(SEP)
                  .append(journalLib).append(SEP)
                  .append(ecritureNum).append(SEP)
                  .append(ecritureDate).append(SEP)
                  .append(compteNum).append(SEP)
                  .append(compteLib).append(SEP)
                  .append("").append(SEP)  // CompAuxNum
                  .append("").append(SEP)  // CompAuxLib
                  .append(pieceRef).append(SEP)
                  .append(ecritureDate).append(SEP)
                  .append(label).append(SEP)
                  .append(debit).append(SEP)
                  .append(credit).append(SEP)
                  .append("").append(SEP)  // EcritureLet
                  .append("").append(SEP)  // DateLet
                  .append("").append(SEP)  // ValidDate
                  .append(formatAmount(abs)).append(SEP)
                  .append(tx.currency != null ? tx.currency : "EUR")
                  .append(CRLF);
            }
        }
        return sb.toString();
    }

    private String resolveJournalCode(String category) {
        if (category == null) return "BQ";
        return switch (category.toUpperCase()) {
            case "SALES", "REVENUE" -> "VT";
            case "PURCHASE", "SUPPLIER" -> "AC";
            case "SALARY", "HR" -> "SA";
            default -> "BQ";
        };
    }

    private String resolveJournalLib(String category) {
        if (category == null) return "Banque";
        return switch (category.toUpperCase()) {
            case "SALES", "REVENUE" -> "Ventes";
            case "PURCHASE", "SUPPLIER" -> "Achats";
            case "SALARY", "HR" -> "Salaires";
            default -> "Banque";
        };
    }

    private String resolveCompteNum(BigDecimal amount, String category) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) >= 0) {
            return "7";
        }
        return "6";
    }

    private String resolveCompteLib(BigDecimal amount, String category) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) >= 0) {
            return "Produits";
        }
        return "Charges";
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}