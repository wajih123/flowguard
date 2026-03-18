package com.flowguard.repository;

import com.flowguard.domain.AccountantAccessEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AccountantAccessRepository implements PanacheRepositoryBase<AccountantAccessEntity, UUID> {

    public List<AccountantAccessEntity> findByOwnerId(UUID ownerId) {
        return list("owner.id = ?1 ORDER BY createdAt DESC", ownerId);
    }

    public Optional<AccountantAccessEntity> findByAccessToken(String token) {
        return find("accessToken = ?1", token).firstResultOptional();
    }

    public Optional<AccountantAccessEntity> findByOwnerAndEmail(UUID ownerId, String email) {
        return find("owner.id = ?1 AND accountantEmail = ?2", ownerId, email).firstResultOptional();
    }
}
