package com.b201.ecovillage.quiz.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.b201.ecovillage.quiz.domain.ActivitySession;

public interface ActivitySessionRepository
    extends JpaRepository<ActivitySession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ActivitySession> findByClassroomIdAndId(UUID classroomId, UUID id);
}
