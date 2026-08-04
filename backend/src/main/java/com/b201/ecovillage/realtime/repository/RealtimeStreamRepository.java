package com.b201.ecovillage.realtime.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.realtime.domain.RealtimeStream;

public interface RealtimeStreamRepository extends JpaRepository<RealtimeStream, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RealtimeStream> findByClassroomIdAndStreamTypeAndResourceId(
        UUID classroomId,
        String streamType,
        UUID resourceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RealtimeStream> findByClassroomIdAndStreamTypeAndResourceIdIsNull(
        UUID classroomId,
        String streamType
    );

    @Query("""
        select stream from RealtimeStream stream
        where stream.classroomId = :classroomId
          and stream.streamType = :streamType
          and stream.resourceId = :resourceId
        """)
    Optional<RealtimeStream> findForDelivery(
        @Param("classroomId") UUID classroomId,
        @Param("streamType") String streamType,
        @Param("resourceId") UUID resourceId
    );

    @Query("""
        select stream from RealtimeStream stream
        where stream.classroomId = :classroomId
          and stream.streamType = :streamType
          and stream.resourceId is null
        """)
    Optional<RealtimeStream> findForDeliveryWithoutResource(
        @Param("classroomId") UUID classroomId,
        @Param("streamType") String streamType
    );
}
