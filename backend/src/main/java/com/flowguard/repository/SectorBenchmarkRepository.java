package com.flowguard.repository;

import com.flowguard.domain.SectorBenchmarkEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SectorBenchmarkRepository implements PanacheRepositoryBase<SectorBenchmarkEntity, UUID> {

    public List<SectorBenchmarkEntity> findBySectorAndSize(String sector, String companySize) {
        return list("sector = ?1 AND companySize = ?2", sector, companySize);
    }

    public List<String> findDistinctSectors() {
        return getEntityManager()
                .createQuery("SELECT DISTINCT b.sector FROM SectorBenchmarkEntity b ORDER BY b.sector", String.class)
                .getResultList();
    }
}
