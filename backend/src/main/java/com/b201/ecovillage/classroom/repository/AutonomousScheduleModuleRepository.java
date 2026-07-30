package com.b201.ecovillage.classroom.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.classroom.domain.AutonomousScheduleModule;

public interface AutonomousScheduleModuleRepository
    extends JpaRepository<AutonomousScheduleModule, UUID> {

    List<AutonomousScheduleModule> findAllByAutonomousScheduleIdIn(
        List<UUID> autonomousScheduleIds
    );
}
