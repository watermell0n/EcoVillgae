package com.b201.bibim.domain.room;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleStateRepository extends JpaRepository<ModuleStateEntity, UUID> {

    Optional<ModuleStateEntity> findBySessionIdAndModuleType(UUID sessionId, String moduleType);
}
