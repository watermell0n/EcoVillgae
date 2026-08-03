package com.b201.ecovillage.classroom.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.classroom.domain.ModuleStateTransition;

public interface ModuleStateTransitionRepository
    extends JpaRepository<ModuleStateTransition, UUID> {
}
