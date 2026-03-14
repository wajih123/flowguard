package com.flowguard.service;

import com.flowguard.domain.*;
import com.flowguard.dto.GdprExportDto;
import com.flowguard.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service RGPD — Conformité au Règlement Général sur la Protection des Données.
 *
 * Droits implémentés :
 * - Art. 7  : Consentement (enregistrement et révocation)
 * - Art. 15 : Droit d'accès
 * - Art. 17 : Droit à l'effacement ("droit à l'oubli")
 * - Art. 20 : Droit à la portabilité
 */
@ApplicationScoped
public class GdprService {

    private static final Logger LOG = Logger.getLogger(GdprService.class);

    @Inject
    UserRepository userRepository;

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    AlertRepository alertRepository;

    @Inject
    FlashCreditRepository flashCreditRepository;

    /**
     * Enregistre le consentement RGPD de l'utilisateur (art. 7).
     */
    @Transactional
    public void recordConsent(UUID userId) {
        UserEntity user = findUserOrFail(userId);
        user.setGdprConsentAt(Instant.now());
        LOG.infof("RGPD consent recorded for user %s", userId);
    }

    /**
     * Export complet des données personnelles (art. 15 + art. 20).
     * Retourne toutes les données associées à l'utilisateur dans un format structuré.
     */
    public GdprExportDto exportUserData(UUID userId) {
        UserEntity user = findUserOrFail(userId);

        GdprExportDto.UserInfo userInfo = new GdprExportDto.UserInfo(
                user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getCompanyName(),
                user.getUserType().name(), user.getKycStatus().name(),
                user.getGdprConsentAt(), user.getCreatedAt()
        );

        List<AccountEntity> accounts = accountRepository.findByUserId(userId);

        List<GdprExportDto.AccountInfo> accountInfos = accounts.stream()
                .map(a -> new GdprExportDto.AccountInfo(
                        a.getId(), a.getIban(), a.getBic(),
                        a.getBalance().toPlainString(), a.getCurrency(),
                        a.getBankName(), a.getStatus().name()))
                .toList();

        List<GdprExportDto.TransactionInfo> transactionInfos = new ArrayList<>();
        for (AccountEntity account : accounts) {
            transactionRepository.findByAccountId(account.getId()).forEach(t ->
                    transactionInfos.add(new GdprExportDto.TransactionInfo(
                            t.getId(), t.getAccount().getId(),
                            t.getAmount().toPlainString(), t.getType().name(),
                            t.getLabel(), t.getCategory().name(),
                            t.getDate().toString(), t.isRecurring()))
            );
        }

        List<GdprExportDto.CreditInfo> creditInfos = flashCreditRepository.findByUserId(userId).stream()
                .map(c -> new GdprExportDto.CreditInfo(
                        c.getId(), c.getAmount().toPlainString(),
                        c.getFee().toPlainString(), c.getTotalRepayment().toPlainString(),
                        c.getTaegPercent() != null ? c.getTaegPercent().toPlainString() : null,
                        c.getPurpose(), c.getStatus().name(),
                        c.getDueDate(), c.getCreatedAt()))
                .toList();

        List<GdprExportDto.AlertInfo> alertInfos = alertRepository.findByUserId(userId).stream()
                .map(a -> new GdprExportDto.AlertInfo(
                        a.getId(), a.getType().name(), a.getSeverity().name(),
                        a.getMessage(), a.isRead(), a.getCreatedAt()))
                .toList();

        LOG.infof("RGPD data export generated for user %s", userId);

        return new GdprExportDto(userInfo, accountInfos, transactionInfos,
                creditInfos, alertInfos, Instant.now());
    }

    /**
     * Demande de suppression des données (art. 17 — droit à l'oubli).
     * Anonymise les données personnelles tout en conservant les traces comptables
     * (obligation légale de conservation comptable : 10 ans, art. L123-22 C.com).
     */
    @Transactional
    public void requestDataDeletion(UUID userId) {
        UserEntity user = findUserOrFail(userId);

        // Check for active credits — cannot delete if outstanding debt
        long activeCredits = flashCreditRepository.countActiveByUserId(userId);
        if (activeCredits > 0) {
            throw new IllegalStateException(
                    "Suppression impossible : vous avez " + activeCredits + " crédit(s) actif(s). "
                    + "Remboursez vos crédits avant de demander la suppression.");
        }

        // Anonymize personal data (keep financial records for legal retention)
        user.setFirstName("SUPPRIMÉ");
        user.setLastName("SUPPRIMÉ");
        user.setEmail("deleted-" + userId + "@flowguard.fr");
        user.setPasswordHash("DELETED");
        user.setCompanyName("SUPPRIMÉ");
        user.setSwanOnboardingId(null);
        user.setSwanAccountId(null);
        user.setNordigenRequisitionId(null);
        user.setDataDeletionRequestedAt(Instant.now());

        LOG.infof("RGPD data deletion processed for user %s", userId);
    }

    private UserEntity findUserOrFail(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("Utilisateur introuvable");
        }
        return user;
    }
}
