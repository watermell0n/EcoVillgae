package com.b201.bibim.domain.monitoring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentPresenceRepository extends JpaRepository<StudentPresenceEntity, UUID> {

    Optional<StudentPresenceEntity> findByStudentId(UUID studentId);

    List<StudentPresenceEntity> findAllByRoomId(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select presence from StudentPresenceEntity presence where presence.studentId = :studentId")
    Optional<StudentPresenceEntity> findByStudentIdForUpdate(@Param("studentId") UUID studentId);
}
