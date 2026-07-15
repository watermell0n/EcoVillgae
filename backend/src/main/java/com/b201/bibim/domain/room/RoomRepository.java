package com.b201.bibim.domain.room;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {

    List<RoomEntity> findAllByTeacherIdOrderByUpdatedAtDesc(UUID teacherId);
}
