package com.b201.ecovillage.quiz.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.RegradingRun;

public interface RegradingRunRepository extends JpaRepository<RegradingRun, UUID> {

    boolean existsByClassroomIdAndQuizSessionIdAndStatus(
        UUID classroomId,
        UUID quizSessionId,
        String status
    );
}
