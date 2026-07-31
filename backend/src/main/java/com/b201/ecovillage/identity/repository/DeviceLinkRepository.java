package com.b201.ecovillage.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.identity.domain.DeviceLink;

public interface DeviceLinkRepository extends JpaRepository<DeviceLink, UUID> {

    Optional<DeviceLink> findByClassroomStudentIdAndDeviceFingerprintHash(
        UUID classroomStudentId,
        String deviceFingerprintHash
    );
}
