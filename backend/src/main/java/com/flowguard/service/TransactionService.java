package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.TransactionDto;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TransactionService {

    @Inject
    TransactionRepository transactionRepository;

    public List<TransactionDto> getByAccountId(UUID accountId) {
        return transactionRepository.findByAccountId(accountId).stream()
                .map(TransactionDto::from)
                .toList();
    }

    public List<TransactionDto> getByAccountIdAndPeriod(UUID accountId, LocalDate from, LocalDate to) {
        return transactionRepository.findByAccountIdAndDateBetween(accountId, from, to).stream()
                .map(TransactionDto::from)
                .toList();
    }

    public List<TransactionDto> getByAccountIdAndCategory(UUID accountId, TransactionEntity.TransactionCategory category) {
        return transactionRepository.findByAccountIdAndCategory(accountId, category).stream()
                .map(TransactionDto::from)
                .toList();
    }

    public List<TransactionDto> getRecurringByAccountId(UUID accountId) {
        return transactionRepository.findRecurringByAccountId(accountId).stream()
                .map(TransactionDto::from)
                .toList();
    }

    /**
     * Vérifie que le compte appartient bien à l'utilisateur.
     */
    public void verifyAccountOwnership(UUID accountId, UUID userId) {
        AccountEntity account = AccountEntity.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Compte introuvable");
        }
        if (!account.getUser().getId().equals(userId)) {
            throw new SecurityException("Accès non autorisé à ce compte");
        }
    }
}
