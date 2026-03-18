package com.flowguard.service;

import com.flowguard.domain.BudgetCategoryEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.BudgetCategoryDto;
import com.flowguard.dto.BudgetVsActualDto;
import com.flowguard.repository.BudgetCategoryRepository;
import com.flowguard.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class BudgetService {

    @Inject BudgetCategoryRepository budgetRepo;
    @Inject UserRepository userRepository;
    @Inject SpendingAnalysisService spendingAnalysisService;

    public List<BudgetCategoryDto> getBudgetForPeriod(UUID userId, int year, int month) {
        return budgetRepo.findByUserAndPeriod(userId, year, month)
                .stream().map(BudgetCategoryDto::from).toList();
    }

    @Transactional
    public BudgetCategoryDto upsert(UUID userId, int year, int month, String category, BigDecimal amount) {
        UserEntity user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Optional<BudgetCategoryEntity> existing =
                budgetRepo.findByUserPeriodCategory(userId, year, month, category);
        BudgetCategoryEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setBudgetedAmount(amount);
        } else {
            entity = BudgetCategoryEntity.builder()
                    .user(user).periodYear(year).periodMonth(month)
                    .category(category).budgetedAmount(amount).build();
            budgetRepo.persist(entity);
        }
        return BudgetCategoryDto.from(entity);
    }

    @Transactional
    public void deleteBudgetLine(UUID userId, UUID budgetId) {
        BudgetCategoryEntity entity = budgetRepo.findByIdOptional(budgetId)
                .orElseThrow(() -> new NotFoundException("Budget line not found"));
        if (!entity.getUser().getId().equals(userId)) {
            throw new jakarta.ws.rs.ForbiddenException("Access denied");
        }
        budgetRepo.delete(entity);
    }

    /**
     * Build Budget vs Actual report for a given month.
     * Actual spend comes from SpendingAnalysisService (transaction aggregation).
     */
    public BudgetVsActualDto getBudgetVsActual(UUID userId, int year, int month) {
        List<BudgetCategoryEntity> budgetLines = budgetRepo.findByUserAndPeriod(userId, year, month);

        // Get actual spend per category from spending analysis
        Map<String, BigDecimal> actualByCategory = spendingAnalysisService
                .getSpendingByCategory(userId, year, month);

        Set<String> allCategories = new LinkedHashSet<>();
        budgetLines.forEach(b -> allCategories.add(b.getCategory()));
        allCategories.addAll(actualByCategory.keySet());

        Map<String, BigDecimal> budgetedByCategory = budgetLines.stream()
                .collect(Collectors.toMap(BudgetCategoryEntity::getCategory, BudgetCategoryEntity::getBudgetedAmount));

        List<BudgetVsActualDto.CategoryLine> lines = allCategories.stream().map(cat -> {
            BigDecimal budgeted = budgetedByCategory.getOrDefault(cat, BigDecimal.ZERO);
            BigDecimal actual = actualByCategory.getOrDefault(cat, BigDecimal.ZERO);
            BigDecimal variance = budgeted.subtract(actual);
            String status;
            if (budgeted.compareTo(BigDecimal.ZERO) == 0) {
                status = "UNBUDGETED";
            } else if (actual.compareTo(budgeted) > 0) {
                status = "OVER_BUDGET";
            } else if (actual.compareTo(budgeted.multiply(new BigDecimal("0.9"))) >= 0) {
                status = "ON_TRACK";
            } else {
                status = "UNDER_BUDGET";
            }
            return new BudgetVsActualDto.CategoryLine(cat, budgeted, actual, variance, status);
        }).toList();

        BigDecimal totalBudgeted = lines.stream().map(BudgetVsActualDto.CategoryLine::budgeted)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalActual = lines.stream().map(BudgetVsActualDto.CategoryLine::actual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BudgetVsActualDto(year, month, lines, totalBudgeted, totalActual,
                totalBudgeted.subtract(totalActual));
    }
}
