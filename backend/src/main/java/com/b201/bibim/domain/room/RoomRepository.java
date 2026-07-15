package com.b201.bibim.domain.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {

    List<RoomEntity> findAllByTeacherIdOrderByUpdatedAtDesc(UUID teacherId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from RoomEntity room where room.id = :roomId")
    Optional<RoomEntity> findByIdForUpdate(@Param("roomId") UUID roomId);
}
