package com.b201.ecovillage.audit.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.audit.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findAllByClassroomIdOrderByOccurredAtDescIdDesc(
        UUID classroomId,
        Pageable pageable
    );
}
