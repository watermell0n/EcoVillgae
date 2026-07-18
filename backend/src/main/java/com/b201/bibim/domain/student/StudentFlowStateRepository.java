package com.b201.bibim.domain.student;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentFlowStateRepository extends JpaRepository<StudentFlowStateEntity, UUID> {

    Optional<StudentFlowStateEntity> findByStudentId(UUID studentId);

    List<StudentFlowStateEntity> findAllByRoomIdOrderByStudentId(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from StudentFlowStateEntity state where state.studentId = :studentId")
    Optional<StudentFlowStateEntity> findByStudentIdForUpdate(@Param("studentId") UUID studentId);
}
