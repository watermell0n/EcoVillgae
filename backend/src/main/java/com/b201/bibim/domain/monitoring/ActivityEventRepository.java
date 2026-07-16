package com.b201.bibim.domain.monitoring;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityEventRepository extends JpaRepository<ActivityEventEntity, UUID> {

    List<ActivityEventEntity> findAllByRoomIdAndStudentIdOrderByOccurredAtDesc(
            UUID roomId,
            UUID studentId,
            Pageable pageable
    );
}
