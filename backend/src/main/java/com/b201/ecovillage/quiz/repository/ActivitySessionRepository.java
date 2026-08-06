package com.b201.ecovillage.quiz.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.b201.ecovillage.quiz.domain.ActivitySession;

public interface ActivitySessionRepository
    extends JpaRepository<ActivitySession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ActivitySession> findByClassroomIdAndId(UUID classroomId, UUID id);

    Optional<ActivitySession> findFirstByClassroomIdAndStatus(
        UUID classroomId,
        String status
    );

    Optional<ActivitySession> findFirstByClassroomIdAndStatusAndActivityType(
        UUID classroomId,
        String status,
        String activityType
    );

    List<ActivitySession> findAllByClassroomIdAndStatus(UUID classroomId, String status);

    boolean existsByClassroomIdAndStatusIn(UUID classroomId, List<String> statuses);

    boolean existsByClassroomIdAndStatus(UUID classroomId, String status);
}
