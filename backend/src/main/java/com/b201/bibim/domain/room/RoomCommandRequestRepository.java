package com.b201.bibim.domain.room;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomCommandRequestRepository
        extends JpaRepository<RoomCommandRequestEntity, UUID> {

    Optional<RoomCommandRequestEntity> findByRoomIdAndIdempotencyKey(
            UUID roomId,
            String idempotencyKey
    );
}
