package com.b201.bibim.domain.finance;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByRoomIdAndStudentIdAndAccountType(
            UUID roomId,
            UUID studentId,
            String accountType
    );

    Optional<AccountEntity> findByRoomIdAndStudentIdIsNullAndAccountType(
            UUID roomId,
            String accountType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account from AccountEntity account
            where account.roomId = :roomId
              and account.studentId = :studentId
              and account.accountType = :accountType
            """)
    Optional<AccountEntity> findStudentAccountForUpdate(
            @Param("roomId") UUID roomId,
            @Param("studentId") UUID studentId,
            @Param("accountType") String accountType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account from AccountEntity account
            where account.roomId = :roomId
              and account.studentId is null
              and account.accountType = :accountType
            """)
    Optional<AccountEntity> findRoomAccountForUpdate(
            @Param("roomId") UUID roomId,
            @Param("accountType") String accountType
    );
}
