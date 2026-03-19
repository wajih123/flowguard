package com.flowguard.repository;

import com.flowguard.domain.FeatureFlagEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class FeatureFlagRepository implements PanacheRepository<FeatureFlagEntity> {
    public Optional<FeatureFlagEntity> findByKey(String flagKey) {
        return find("flagKey", flagKey).firstResultOptional();
    }
}
