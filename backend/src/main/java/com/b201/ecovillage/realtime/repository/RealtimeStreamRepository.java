package com.b201.ecovillage.realtime.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.b201.ecovillage.realtime.domain.RealtimeStream;

public interface RealtimeStreamRepository extends JpaRepository<RealtimeStream, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RealtimeStream> findByClassroomIdAndStreamTypeAndResourceId(
        UUID classroomId,
        String streamType,
        UUID resourceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RealtimeStream> findByClassroomIdAndStreamTypeAndResourceIdIsNull(
        UUID classroomId,
        String streamType
    );
}
