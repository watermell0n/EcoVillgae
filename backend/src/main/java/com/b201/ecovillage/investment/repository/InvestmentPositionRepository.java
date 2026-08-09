package com.b201.ecovillage.investment.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.investment.domain.InvestmentPosition;

public interface InvestmentPositionRepository extends JpaRepository<InvestmentPosition, UUID> {

    Optional<InvestmentPosition> findByClassroomIdAndClassroomStudentIdAndInvestmentAssetId(
        UUID classroomId,
        UUID classroomStudentId,
        UUID investmentAssetId
    );

    /** 정산 대상은 그 종목을 실제로 들고 있는 학생뿐이다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select position from InvestmentPosition position
         where position.classroomId = :classroomId
           and position.investmentAssetId = :assetId
           and position.quantity > 0
        """)
    java.util.List<InvestmentPosition> findAllHoldersForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("assetId") UUID assetId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select position from InvestmentPosition position
         where position.classroomId = :classroomId
           and position.classroomStudentId = :studentId
           and position.investmentAssetId = :assetId
        """)
    Optional<InvestmentPosition> findForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("studentId") UUID studentId,
        @Param("assetId") UUID assetId
    );
}
