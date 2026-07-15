package com.b201.bibim.domain.student;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<StudentEntity, UUID> {

    Optional<StudentEntity> findByParticipationCode(String participationCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from StudentEntity student where student.participationCode = :participationCode")
    Optional<StudentEntity> findByParticipationCodeForUpdate(
            @Param("participationCode") String participationCode
    );

    long countByRoomIdAndOnlineTrue(UUID roomId);
}
