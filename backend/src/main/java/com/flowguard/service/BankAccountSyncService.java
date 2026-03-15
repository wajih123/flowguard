package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Processes one Bridge account (upsert + transactions) inside its own
 * {@code REQUIRES_NEW} transaction so that a constraint violation on one
 * account cannot abort the surrounding transaction and block all others.
 */
@ApplicationScoped
public class BankAccountSyncService {

    @Inject
    EntityManager em;

    @Inject
    BridgeService bridgeService;

    /**
     * Upserts one Bridge account and imports its last 6 months of transactions.
     * Runs in a brand-new transaction regardless of the caller's context.
     * Accepts {@code userId} (not a managed entity) so it can be safely called
     * from a context with no surrounding transaction.
     *
     * @return {@code true} if the account was processed successfully
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean syncAccount(UUID userId, String userToken, BridgeService.BridgeAccount ba) {
        String extId = String.valueOf(ba.id());

        // ── 1. Upsert account ────────────────────────────────────────
        AccountEntity account = AccountEntity
                .<AccountEntity>find("externalAccountId", extId)
                .firstResult();

        if (account == null) {
            UserEntity user = UserEntity.findById(userId);
            if (user == null) return false;
            account = new AccountEntity();
            account.setUser(user);
            account.setExternalAccountId(extId);
            String iban = ba.iban() != null && !ba.iban().isBlank()
                    ? ba.iban() : "BRIDGE-" + extId;
            account.setIban(iban);
            account.setBic("BRIDGEAPI");
        }

        account.setAccountName(ba.name());
        account.setBankName(ba.providerName() != null ? ba.providerName() : "");
        account.setBalance(ba.balance() != null ? ba.balance() : BigDecimal.ZERO);
        account.setCurrency(ba.currencyCode() != null ? ba.currencyCode() : "EUR");
        account.setAccountType(ba.accountType());
        account.setSyncStatus(AccountEntity.SyncStatus.OK);
        account.setLastSyncAt(Instant.now());

        if (account.getId() == null) {
            account.persist();
        }

        // Flush immediately so any constraint violation is thrown here, inside
        // this REQUIRES_NEW transaction, rather than later during a query flush.
        em.flush();

        // ── 2. Import transactions (last 6 months) ───────────────────
        try {
            List<BridgeService.BridgeTransaction> txs =
                    bridgeService.listTransactions(userToken, ba.id(), LocalDate.now().minusMonths(6));

            for (BridgeService.BridgeTransaction btx : txs) {
                if (btx.deleted()) continue;
                upsertTransaction(account, btx);
            }
        } catch (Exception e) {
            // Transaction import failure must not prevent the account itself
            // from being saved; mark the account status and commit anyway.
            account.setSyncStatus(AccountEntity.SyncStatus.ERROR);
        }

        return true;
    }

    private void upsertTransaction(AccountEntity account, BridgeService.BridgeTransaction btx) {
        String extId = String.valueOf(btx.id());
        boolean exists = TransactionEntity.count("externalTransactionId", extId) > 0;
        if (exists) return;

        TransactionEntity tx = new TransactionEntity();
        tx.setAccount(account);
        tx.setAmount(btx.amount());
        tx.setType(btx.amount().signum() < 0
                ? TransactionEntity.TransactionType.DEBIT
                : TransactionEntity.TransactionType.CREDIT);
        String desc = btx.description() != null ? btx.description() : "";
        tx.setLabel(desc.isBlank() ? "(sans libellé)" : desc);
        tx.setCategory(parseCategory(bridgeService.categorize(desc)));
        tx.setDate(btx.date() != null ? btx.date() : LocalDate.now());
        tx.setExternalTransactionId(extId);
        tx.setRecurring(false);
        tx.persist();
    }

    private static TransactionEntity.TransactionCategory parseCategory(String cat) {
        try {
            return TransactionEntity.TransactionCategory.valueOf(cat);
        } catch (IllegalArgumentException e) {
            return TransactionEntity.TransactionCategory.AUTRE;
        }
    }
}
