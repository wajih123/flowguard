package com.flowguard.service;

import com.flowguard.domain.*;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.FlashCreditRepository;
import com.flowguard.repository.TransactionRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@QuarkusTest
class CreditScoringServiceTest {

    @Inject
    CreditScoringService service;

    @InjectMock
    AccountRepository accountRepo;

    @InjectMock
    TransactionRepository transactionRepo;

    @InjectMock
    FlashCreditRepository flashCreditRepo;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private UserEntity testUser;
    private AccountEntity testAccount;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id(userId)
                .email("test@flowguard.fr")
                .firstName("Test")
                .lastName("User")
                .passwordHash("hash")
                .build();

        testAccount = AccountEntity.builder()
                .id(accountId)
                .user(testUser)
                .iban("FR7630006000011234567890189")
                .bic("BNPAFRPP")
                .balance(new BigDecimal("5000.00"))
                .currency("EUR")
                .bankName("BNP Paribas")
                .build();
    }

    // ── Score computation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("User with regular salary and no debt → high score ≥ 60")
    void computeScore_regularSalaryNoDept_highScore() {
        when(accountRepo.findByUserId(userId)).thenReturn(List.of(testAccount));

        // 6 monthly salary transactions used by findByAccountIdAndDateBetween
        List<TransactionEntity> salaries = buildSalaries(6, new BigDecimal("3500"));
        when(transactionRepo.findByAccountIdAndDateBetween(
                eq(accountId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(salaries);

        lenient().when(flashCreditRepo.findActiveByUserId(userId)).thenReturn(List.of());
        when(flashCreditRepo.findByUserId(userId)).thenReturn(List.of());

        int score = service.computeScore(userId);
        // scoreIncome=24 + scoreDebt=25 + scoreRepay=15 + scoreAge=1 + scoreStability≥4
        assertThat(score).isGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("User with no income → low score < 30")
    void computeScore_noIncome_lowScore() {
        when(accountRepo.findByUserId(userId)).thenReturn(List.of(testAccount));
        when(transactionRepo.findByAccountIdAndDateBetween(
                any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(flashCreditRepo.findActiveByUserId(userId)).thenReturn(List.of());
        when(flashCreditRepo.findByUserId(userId)).thenReturn(List.of());

        int score = service.computeScore(userId);
        // 0+0+15(neutral)+1+5(neutral) = 21
        assertThat(score).isLessThan(30);
    }

    @Test
    @DisplayName("User with defaulted flash credit → reduced repayment score")
    void computeScore_defaultedCredit_reducedScore() {
        when(accountRepo.findByUserId(userId)).thenReturn(List.of(testAccount));

        // Some income
        List<TransactionEntity> salaries = buildSalaries(6, new BigDecimal("3500"));
        when(transactionRepo.findByAccountIdAndDateBetween(
                eq(accountId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(salaries);
        lenient().when(flashCreditRepo.findActiveByUserId(userId)).thenReturn(List.of());

        // A defaulted credit — repayment score = 0 instead of neutral 15
        FlashCreditEntity defaulted = FlashCreditEntity.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .amount(new BigDecimal("500"))
                .status(FlashCreditEntity.CreditStatus.OVERDUE)
                .build();
        when(flashCreditRepo.findByUserId(userId)).thenReturn(List.of(defaulted));

        int score = service.computeScore(userId);
        // With 0 repayment score vs 15 neutral: score should be lower than high-score case
        assertThat(score).isLessThan(70);
    }

    // ── Eligibility ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("assertEligible throws when score < 40")
    void assertEligible_lowScore_throws() {
        when(accountRepo.findByUserId(userId)).thenReturn(List.of(testAccount));
        when(transactionRepo.findByAccountIdAndDateBetween(
                any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(flashCreditRepo.findActiveByUserId(userId)).thenReturn(List.of());
        when(flashCreditRepo.findByUserId(userId)).thenReturn(List.of());

        // Score ~21 (no income) — threshold is 40
        assertThatThrownBy(() -> service.assertEligible(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Score de crédit insuffisant");
    }

    @Test
    @DisplayName("assertEligible passes when score ≥ 40")
    void assertEligible_sufficientScore_passes() {
        when(accountRepo.findByUserId(userId)).thenReturn(List.of(testAccount));
        List<TransactionEntity> salaries = buildSalaries(6, new BigDecimal("3500"));
        when(transactionRepo.findByAccountIdAndDateBetween(
                eq(accountId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(salaries);
        lenient().when(flashCreditRepo.findActiveByUserId(userId)).thenReturn(List.of());
        when(flashCreditRepo.findByUserId(userId)).thenReturn(List.of());

        // Score ≥ 60 (well above threshold of 40)
        assertThatCode(() -> service.assertEligible(userId)).doesNotThrowAnyException();
    }

    // ── RecurringDetectionService ──────────────────────────────────────────────

    @Test
    @DisplayName("normaliseLabel removes accents and noise words")
    void normaliseLabel_removesAccentsAndNoise() {
        String label = "VIREMENT DE SALAIRE réf: 20240101 DE L'EMPLOYEUR";
        String normalised = RecurringDetectionService.normaliseLabel(label);

        assertThat(normalised).doesNotContain("é", "È", "  "); // no accents, no double spaces
        assertThat(normalised).doesNotContain("ref"); // noise word removed
        assertThat(normalised).contains("salaire");
    }

    @Test
    @DisplayName("normaliseLabel produces stable output for semantically identical labels")
    void normaliseLabel_stable() {
        String a = "Prélèvement NETFLIX.COM Réf: 123456789";
        String b = "Prélèvement NETFLIX.COM Réf: 987654321";
        assertThat(RecurringDetectionService.normaliseLabel(a))
                .isEqualTo(RecurringDetectionService.normaliseLabel(b));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<TransactionEntity> buildSalaries(int count, BigDecimal amount) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i ->
            TransactionEntity.builder()
                    .id(UUID.randomUUID())
                    .account(testAccount)
                    .amount(amount)
                    .type(TransactionEntity.TransactionType.CREDIT)
                    .label("VIREMENT SALAIRE")
                    .category(TransactionEntity.TransactionCategory.SALAIRE)
                    .date(LocalDate.now().minusMonths(count - i))
                    .build()
        ).toList();
    }
}
