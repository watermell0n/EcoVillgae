package com.b201.bibim.domain.character;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentCharacterRepository extends JpaRepository<StudentCharacterEntity, UUID> {

    Optional<StudentCharacterEntity> findByRoomIdAndStudentId(UUID roomId, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select character
            from StudentCharacterEntity character
            where character.roomId = :roomId
              and character.studentId = :studentId
            """)
    Optional<StudentCharacterEntity> findByRoomIdAndStudentIdForUpdate(
            @Param("roomId") UUID roomId,
            @Param("studentId") UUID studentId
    );
}
