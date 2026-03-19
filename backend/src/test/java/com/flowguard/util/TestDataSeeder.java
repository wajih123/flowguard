package com.flowguard.util;

import com.flowguard.domain.*;
import com.flowguard.repository.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Populates test database with seed data before tests run.
 */
@ApplicationScoped
public class TestDataSeeder {

    public static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String TEST_USER_EMAIL = "e2e-user@flowguard.local";

    @Inject
    UserRepository userRepository;

    @Inject
    AccountRepository accountRepository;

    @Inject
    BudgetCategoryRepository budgetCategoryRepository;

    /**
     * Creates test user and related data for E2E tests.
     * User email is used to find existing user, which works across test sessions.
     */
    @Transactional
    public void seedTestData() {
        // Try to find by ID first, then by email
        UserEntity testUser = null;
        try {
            testUser = userRepository.findById(TEST_USER_ID);
        } catch (Exception e) {
            // Not found
        }
        
        if (testUser == null) {
            Optional<UserEntity> byEmail = userRepository.findByEmail(TEST_USER_EMAIL);
            if (byEmail.isPresent()) {
                testUser = byEmail.get();
            } else {
                // Create new user with fixed ID - use merge to handle ID assignment
                UserEntity newUser = new UserEntity();
                newUser.setId(TEST_USER_ID);
                newUser.setEmail(TEST_USER_EMAIL);
                newUser.setFirstName("E2E");
                newUser.setLastName("User");
                newUser.setPasswordHash("$2b$10$3Q1Rt6Ha5XOonNiyMVodouex5JD9u9hQvIIqNdQjNAxNlWQA2L/ye");
                newUser.setCompanyName("E2E Test Company");
                newUser.setUserType(UserEntity.UserType.TPE);
                newUser.setKycStatus(UserEntity.KycStatus.APPROVED);
                newUser.setRole("ROLE_USER");
                newUser.setDisabled(false);
                newUser.setEmailVerified(true);
                
                // Use merge() instead of persist() to handle pre-assigned IDs
                testUser = userRepository.getEntityManager().merge(newUser);
                userRepository.flush();
            }
        }

        // Create test account if doesn't exist  
        long accountCount = accountRepository.count("user.id", testUser.getId());
        if (accountCount == 0) {
            AccountEntity testAccount = new AccountEntity();
            testAccount.setUser(testUser);
            testAccount.setIban("FR1234567890123456789012345");
            testAccount.setBic("ABCDEFGH");
            testAccount.setAccountName("E2E Test Account");
            testAccount.setBankName("Test Bank");
            testAccount.setBalance(new BigDecimal("5000.00"));
            testAccount.setCurrency("EUR");
            testAccount.setExternalAccountId("ext-123456-test");
            testAccount.setStatus(AccountEntity.AccountStatus.ACTIVE);
            
            accountRepository.persistAndFlush(testAccount);
        }

        // Create budget categories if they don't exist
        LocalDate today = LocalDate.now();
        String[] categories = {"SERVICES", "EQUIPMENT", "TRAVEL", "OTHER"};
        
        for (String category : categories) {
            long existing = budgetCategoryRepository.count(
                "user.id = ?1 AND periodYear = ?2 AND periodMonth = ?3 AND category = ?4",
                testUser.getId(), today.getYear(), today.getMonthValue(), category
            );
            
            if (existing == 0) {
                BudgetCategoryEntity budgetCategory = new BudgetCategoryEntity();
                budgetCategory.setUser(testUser);
                budgetCategory.setPeriodYear(today.getYear());
                budgetCategory.setPeriodMonth(today.getMonthValue());
                budgetCategory.setCategory(category);
                budgetCategory.setBudgetedAmount(new BigDecimal("5000.00"));
                
                budgetCategoryRepository.persistAndFlush(budgetCategory);
            }
        }
    }
}

