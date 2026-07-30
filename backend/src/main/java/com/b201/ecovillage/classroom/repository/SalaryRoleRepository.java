package com.b201.ecovillage.classroom.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.classroom.domain.SalaryRole;

public interface SalaryRoleRepository extends JpaRepository<SalaryRole, UUID> {

    List<SalaryRole> findAllByClassroomIdOrderBySortOrder(UUID classroomId);

    Optional<SalaryRole> findByClassroomIdAndRoleCode(UUID classroomId, String roleCode);
}
