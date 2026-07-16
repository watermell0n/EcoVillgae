package com.b201.bibim.domain.quiz;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<QuizEntity, UUID> {

    Optional<QuizEntity> findByRoomIdAndDayNumber(UUID roomId, short dayNumber);
}
