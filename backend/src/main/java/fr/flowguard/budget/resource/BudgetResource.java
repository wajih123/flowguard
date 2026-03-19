package fr.flowguard.budget.resource;

import fr.flowguard.budget.entity.BudgetCategoryEntity;
import fr.flowguard.banking.entity.BankAccountEntity;
import fr.flowguard.banking.entity.TransactionEntity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/budget")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Budget", description = "Gestion du budget previsionnel")
public class BudgetResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/{year}/{month}")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Obtenir le budget du mois")
    public Response get(@PathParam("year") int year, @PathParam("month") int month) {
        String userId = jwt.getSubject();
        List<BudgetCategoryEntity> lines = BudgetCategoryEntity.findByUserAndPeriod(userId, year, month);
        return Response.ok(lines.stream().map(this::toDto).toList()).build();
    }

    @PUT
    @Path("/{year}/{month}/{category}")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Transactional
    @Operation(summary = "Creer ou mettre a jour une ligne de budget")
    public Response upsert(@PathParam("year") int year, @PathParam("month") int month,
                           @PathParam("category") String category, BigDecimal amount) {
        String userId = jwt.getSubject();
        BudgetCategoryEntity line = BudgetCategoryEntity
                .findByUserPeriodCategory(userId, year, month, category)
                .orElseGet(() -> {
                    BudgetCategoryEntity b = new BudgetCategoryEntity();
                    b.userId = userId;
                    b.periodYear = (short) year;
                    b.periodMonth = (short) month;
                    b.category = category;
                    return b;
                });
        line.budgetedAmount = amount != null ? amount : BigDecimal.ZERO;
        line.persist();
        return Response.ok(toDto(line)).build();
    }

    @DELETE
    @Path("/line/{id}")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Transactional
    @Operation(summary = "Supprimer une ligne de budget")
    public Response deleteLine(@PathParam("id") String id) {
        String userId = jwt.getSubject();
        BudgetCategoryEntity line = BudgetCategoryEntity.findById(id);
        if (line == null || !line.userId.equals(userId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Ligne introuvable")).build();
        }
        line.delete();
        return Response.noContent().build();
    }

    @GET
    @Path("/vs-actual/{year}/{month}")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Budget vs depenses reelles")
    public Response vsActual(@PathParam("year") int year, @PathParam("month") int month) {
        String userId = jwt.getSubject();
        List<BudgetCategoryEntity> budgets = BudgetCategoryEntity.findByUserAndPeriod(userId, year, month);

        // Actual spending from transactions for this period
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.plusMonths(1);

        // Get user's account IDs
        List<String> accountIds = BankAccountEntity.<BankAccountEntity>list("userId = ?1 AND isActive = true", userId)
                .stream().map(a -> a.id).toList();

        // Sum debits by category for this period
        Map<String, BigDecimal> actualByCategory = new HashMap<>();
        if (!accountIds.isEmpty()) {
            List<TransactionEntity> txns = TransactionEntity.list(
                    "accountId IN ?1 AND transactionDate >= ?2 AND transactionDate < ?3 AND amount < 0",
                    accountIds, from, to);
            for (TransactionEntity t : txns) {
                String cat = t.category != null ? t.category : "AUTRE";
                actualByCategory.merge(cat, t.amount.abs(), BigDecimal::add);
            }
        }

        // Build lines
        Set<String> allCategories = new HashSet<>();
        budgets.forEach(b -> allCategories.add(b.category));
        actualByCategory.keySet().forEach(allCategories::add);

        List<BudgetVsActualLine> lines = allCategories.stream().map(cat -> {
            BigDecimal budgeted = budgets.stream()
                    .filter(b -> b.category.equals(cat))
                    .findFirst().map(b -> b.budgetedAmount).orElse(BigDecimal.ZERO);
            BigDecimal actual = actualByCategory.getOrDefault(cat, BigDecimal.ZERO);
            BigDecimal variance = budgeted.subtract(actual);
            String status;
            if (budgeted.compareTo(BigDecimal.ZERO) == 0) status = "UNBUDGETED";
            else if (variance.compareTo(BigDecimal.ZERO) < 0) status = "OVER_BUDGET";
            else if (variance.compareTo(budgeted.multiply(new BigDecimal("0.05"))) < 0) status = "ON_TRACK";
            else status = "UNDER_BUDGET";
            return new BudgetVsActualLine(cat, budgeted.doubleValue(), actual.doubleValue(), variance.doubleValue(), status);
        }).sorted(Comparator.comparing(BudgetVsActualLine::category)).toList();

        BigDecimal totalBudgeted = lines.stream().map(l -> BigDecimal.valueOf(l.budgeted())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalActual   = lines.stream().map(l -> BigDecimal.valueOf(l.actual())).reduce(BigDecimal.ZERO, BigDecimal::add);

        return Response.ok(new BudgetVsActual(year, month, lines,
                totalBudgeted.doubleValue(), totalActual.doubleValue(),
                totalBudgeted.subtract(totalActual).doubleValue())).build();
    }

    private BudgetDto toDto(BudgetCategoryEntity b) {
        return new BudgetDto(b.id, b.periodYear, b.periodMonth, b.category, b.budgetedAmount.doubleValue());
    }

    public record BudgetDto(String id, int periodYear, int periodMonth, String category, double budgetedAmount) {}
    public record BudgetVsActualLine(String category, double budgeted, double actual, double variance, String status) {}
    public record BudgetVsActual(int year, int month, List<BudgetVsActualLine> lines,
                                 double totalBudgeted, double totalActual, double netVariance) {}
}