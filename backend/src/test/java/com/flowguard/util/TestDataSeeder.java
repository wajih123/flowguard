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

    @Inject
    FeatureFlagRepository flagRepository;

    @Inject
    SystemConfigRepository configRepository;

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

        // Seed feature flags (will be used by /config/flags endpoint)
        seedFeatureFlags();

        // Seed system config (will be used by /config/system endpoint)
        seedSystemConfig();
    }

    private void seedFeatureFlags() {
        String[] flags = {
            "TRADING_ACCOUNTS", "BUDGET_FORECASTING", "TAX_ESTIMATION",
            "ACCOUNTANT_PORTAL", "FLASH_CREDIT", "SANCTIONS_CHECK",
            "TRACFIN_REPORTING", "MFA_ENFORCED", "ADVANCED_ANALYTICS", "API_RATE_LIMITING"
        };

        for (String flagKey : flags) {
            if (!flagRepository.findByKey(flagKey).isPresent()) {
                FeatureFlagEntity flag = new FeatureFlagEntity();
                flag.flagKey = flagKey;
                flag.enabled = !flagKey.equals("MFA_ENFORCED"); // Most flags on, MFA off by default
                flag.description = "Feature flag for " + flagKey;
                flag.updatedAt = Instant.now();
                flagRepository.persistAndFlush(flag);
            }
        }
    }

    private void seedSystemConfig() {
        Object[][] configs = {
            {"SUPPORT_EMAIL", "support@flowguard.fr", "STRING", "Support team email"},
            {"DPO_EMAIL", "dpo@flowguard.fr", "STRING", "Data Protection Officer"},
            {"COMPLIANCE_OFFICER_EMAIL", "compliance@flowguard.fr", "STRING", "Compliance officer"},
            {"MAX_LOGIN_ATTEMPTS", "5", "INTEGER", "Max failed attempts"},
            {"LOGIN_LOCKOUT_MINUTES", "15", "INTEGER", "Lockout duration"},
            {"API_RATE_LIMIT_PER_HOUR", "1000", "INTEGER", "API rate limit"},
            {"MIN_PASSWORD_LENGTH", "12", "INTEGER", "Minimum password length"},
            {"SESSION_TIMEOUT_MINUTES", "30", "INTEGER", "Session timeout"},
            {"TAX_YEAR", "2025", "INTEGER", "Current tax year"},
            {"CURRENCY_DEFAULT", "EUR", "STRING", "Default currency"},
            {"MAINTENANCE_MODE", "false", "BOOLEAN", "Maintenance mode"},
            {"ALERT_EMAIL_ENABLED", "true", "BOOLEAN", "Enable email alerts"}
        };

        for (Object[] config : configs) {
            String key = (String) config[0];
            if (!configRepository.findByKey(key).isPresent()) {
                SystemConfigEntity entity = new SystemConfigEntity();
                entity.configKey = key;
                entity.configValue = (String) config[1];
                entity.valueType = (String) config[2];
                entity.description = (String) config[3];
                entity.updatedAt = Instant.now();
                configRepository.persistAndFlush(entity);
            }
        }
    }
}

