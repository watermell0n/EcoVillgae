package com.b201.ecovillage.classroom.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.classroom.domain.AutonomousSchedule;

public interface AutonomousScheduleRepository extends JpaRepository<AutonomousSchedule, UUID> {

    List<AutonomousSchedule> findAllByClassroomIdAndStatusOrderByDayOfWeekAscLocalStartTimeAsc(
        UUID classroomId,
        String status
    );
}
