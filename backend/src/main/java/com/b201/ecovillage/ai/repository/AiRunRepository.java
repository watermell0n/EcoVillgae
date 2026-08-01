package com.b201.ecovillage.ai.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.ai.domain.AiRun;

public interface AiRunRepository extends JpaRepository<AiRun, UUID> {

    Optional<AiRun> findByClassroomIdAndId(UUID classroomId, UUID id);
}
