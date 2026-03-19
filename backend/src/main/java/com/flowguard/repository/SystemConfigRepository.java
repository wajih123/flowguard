package com.flowguard.repository;

import com.flowguard.domain.SystemConfigEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class SystemConfigRepository implements PanacheRepository<SystemConfigEntity> {
    public Optional<SystemConfigEntity> findByKey(String configKey) {
        return find("configKey", configKey).firstResultOptional();
    }
}
