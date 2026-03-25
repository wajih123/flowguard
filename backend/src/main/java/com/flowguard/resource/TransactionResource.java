package com.flowguard.resource;

import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.TransactionDto;
import com.flowguard.service.TransactionService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/accounts/{accountId}/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class TransactionResource {

    @Inject
    TransactionService transactionService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public Response getTransactions(
            @PathParam("accountId") UUID accountId,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("category") String category) {

        UUID userId = UUID.fromString(jwt.getSubject());
        transactionService.verifyAccountOwnership(accountId, userId);

        List<TransactionDto> transactions;

        if (from != null && to != null) {
            transactions = transactionService.getByAccountIdAndPeriod(
                    accountId, LocalDate.parse(from), LocalDate.parse(to));
        } else if (category != null) {
            TransactionEntity.TransactionCategory cat =
                    TransactionEntity.TransactionCategory.valueOf(category);
            transactions = transactionService.getByAccountIdAndCategory(accountId, cat);
        } else {
            transactions = transactionService.getByAccountId(accountId);
        }

        return Response.ok(transactions).build();
    }

    @GET
    @Path("/recurring")
    @RunOnVirtualThread
    public Response getRecurring(@PathParam("accountId") UUID accountId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        transactionService.verifyAccountOwnership(accountId, userId);

        List<TransactionDto> recurring = transactionService.getRecurringByAccountId(accountId);
        return Response.ok(recurring).build();
    }

    /**
     * Import transactions from a CSV file.
     * Expected CSV format (with header row):
     *   date,label,amount,type
     *   2025-01-15,Loyer,-1200.00,DEBIT
     *   2025-01-20,Paiement client,4500.00,CREDIT
     */
    @POST
    @Path("/import-csv")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RunOnVirtualThread
    public Response importCsv(
            @PathParam("accountId") UUID accountId,
            @RestForm("file") FileUpload csvFile) {
        if (csvFile == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Fichier CSV manquant")).build();
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        transactionService.verifyAccountOwnership(accountId, userId);

        Map<String, Integer> result = transactionService.importFromCsv(accountId, csvFile.uploadedFile().toFile());
        return Response.ok(result).build();
    }

    /**
     * Import transactions from a bank statement file in any supported format.
     * <p>
     * Accepted formats: PDF, OFX/QFX, QIF, MT940, CFONB, XLSX, XLS, CSV.
     * The format is auto-detected from the uploaded filename extension and content.
     * Duplicate transactions (same date + label + amount) are automatically ignored.
     */
    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RunOnVirtualThread
    public Response importStatement(
            @PathParam("accountId") UUID accountId,
            @RestForm("file") FileUpload statementFile) {
        if (statementFile == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Fichier manquant")).build();
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        transactionService.verifyAccountOwnership(accountId, userId);

        // Validate file size (max 20 MB — PDFs from some banks can be large)
        java.io.File uploaded = statementFile.uploadedFile().toFile();
        if (uploaded.length() > 20L * 1024 * 1024) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Fichier trop volumineux (max 20 Mo)")).build();
        }

        String filename = statementFile.fileName() != null
                ? statementFile.fileName()
                : statementFile.name();

        try (java.io.InputStream is = new java.io.FileInputStream(uploaded)) {
            Map<String, Object> result = transactionService.importFromStatement(accountId, is, filename);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erreur lors de l'import : " + e.getMessage())).build();
        }
    }
}
