package com.b201.bibim.domain.room;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRulesRepository extends JpaRepository<RoomRulesEntity, UUID> {

    Optional<RoomRulesEntity> findByRoomId(UUID roomId);
}
