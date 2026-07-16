package com.b201.bibim.domain.room;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DaySessionRepository extends JpaRepository<DaySessionEntity, UUID> {

    Optional<DaySessionEntity> findByRoomIdAndDayNumber(UUID roomId, short dayNumber);
}
