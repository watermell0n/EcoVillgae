package com.b201.ecovillage.quiz.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.ActivityParticipant;

public interface ActivityParticipantRepository
    extends JpaRepository<ActivityParticipant, UUID> {

    boolean existsByClassroomIdAndActivitySessionIdAndClassroomStudentId(
        UUID classroomId,
        UUID activitySessionId,
        UUID classroomStudentId
    );

    List<ActivityParticipant> findAllByClassroomIdAndActivitySessionId(
        UUID classroomId,
        UUID activitySessionId
    );

    long countByClassroomIdAndActivitySessionId(
        UUID classroomId,
        UUID activitySessionId
    );
}
