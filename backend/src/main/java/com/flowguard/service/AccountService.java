package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.AccountDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AccountService {

    @Inject
    AccountRepository accountRepository;

    @Inject
    UserRepository userRepository;

    public List<AccountDto> getAccountsByUserId(UUID userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(AccountDto::from)
                .toList();
    }

    public AccountDto getAccountById(UUID accountId, UUID userId) {
        AccountEntity account = accountRepository.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Compte introuvable");
        }
        if (!account.getUser().getId().equals(userId)) {
            throw new SecurityException("Accès non autorisé à ce compte");
        }
        return AccountDto.from(account);
    }

    public AccountEntity getEntityAndVerifyOwnership(UUID accountId, UUID userId) {
        AccountEntity account = accountRepository.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Compte introuvable");
        }
        if (!account.getUser().getId().equals(userId)) {
            throw new SecurityException("Accès non autorisé à ce compte");
        }
        return account;
    }
}
