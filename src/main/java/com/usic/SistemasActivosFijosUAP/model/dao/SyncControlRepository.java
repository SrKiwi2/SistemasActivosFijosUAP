package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;

@Repository
public interface SyncControlRepository extends JpaRepository<SyncControl, Long> {
    Optional<SyncControl> findByTablaNombre(String tablaNombre);
}
