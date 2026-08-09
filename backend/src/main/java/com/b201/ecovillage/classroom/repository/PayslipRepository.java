package com.b201.ecovillage.classroom.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.classroom.domain.Payslip;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    boolean existsByClassroomStudentIdInAndPeriodKey(
        Collection<UUID> classroomStudentIds,
        String periodKey
    );

    List<Payslip> findAllByTransactionGroupId(UUID transactionGroupId);

    List<Payslip> findAllByClassroomIdAndClassroomStudentIdOrderByIssuedAtDesc(
        UUID classroomId,
        UUID classroomStudentId,
        Pageable pageable
    );
}
