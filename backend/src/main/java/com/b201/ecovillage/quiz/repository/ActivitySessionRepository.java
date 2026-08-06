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

    /**
     * 잠금 없이 읽기만 한다.
     *
     * <p>위 메서드는 쓰기 잠금을 걸어 읽기 전용 트랜잭션에서 쓸 수 없다. PostgreSQL이
     * `SELECT FOR NO KEY UPDATE in a read-only transaction`으로 거절하기 때문이다.
     * 조회 경로는 잠글 이유도 없으므로 이 메서드를 쓴다.
     */
    Optional<ActivitySession> findOneByClassroomIdAndId(UUID classroomId, UUID id);

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
