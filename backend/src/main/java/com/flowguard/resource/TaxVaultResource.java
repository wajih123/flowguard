package com.flowguard.resource;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.TaxVaultDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.TransactionRepository;
import com.flowguard.repository.UserRepository;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Path("/tax-vault")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class TaxVaultResource {

    // TVA régime normal (20%), URSSAF auto-entrepreneur (22.1% BIC services),
    // IS réduit PME plafonné à 42 500€ (15%)
    private static final BigDecimal TVA_RATE        = new BigDecimal("0.20");
    private static final BigDecimal URSSAF_RATE     = new BigDecimal("0.221");
    private static final BigDecimal IS_RATE         = new BigDecimal("0.15");

    @Inject TransactionRepository transactionRepository;
    @Inject AccountRepository     accountRepository;
    @Inject UserRepository        userRepository;
    @Inject JsonWebToken          jwt;

    @GET
    @RunOnVirtualThread
    public TaxVaultDto get() {
        UUID userId = UUID.fromString(jwt.getSubject());
        LocalDate today   = LocalDate.now();
        LocalDate from30  = today.minusDays(30);

        UserEntity user = userRepository.findById(userId);
        String businessType = user != null && user.getUserType() != null
                ? user.getUserType().name()
                : "AUTO_ENTREPRENEUR";

        // Sum all client-payment credits in last 30 days
        List<AccountEntity> accounts = accountRepository.findActiveByUserId(userId);
        BigDecimal grossIncome = BigDecimal.ZERO;
        BigDecimal currentBalance = BigDecimal.ZERO;

        for (AccountEntity acc : accounts) {
            currentBalance = currentBalance.add(acc.getBalance());
            List<TransactionEntity> txs = transactionRepository
                    .findByAccountIdAndDateBetween(acc.getId(), from30, today);
            for (TransactionEntity tx : txs) {
                if (tx.getType() == TransactionEntity.TransactionType.CREDIT
                        && tx.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    grossIncome = grossIncome.add(tx.getAmount());
                }
            }
        }

        // Compute reserves
        BigDecimal tvaReserve    = grossIncome.multiply(TVA_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal urssafReserve = grossIncome.multiply(URSSAF_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal isReserve     = grossIncome.multiply(IS_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalReserved = tvaReserve.add(urssafReserve).add(isReserve);
        BigDecimal spendable     = currentBalance.subtract(totalReserved)
                                                 .max(BigDecimal.ZERO);

        return new TaxVaultDto(
                grossIncome,
                tvaReserve,
                urssafReserve,
                isReserve,
                totalReserved,
                currentBalance,
                spendable,
                businessType
        );
    }
}
