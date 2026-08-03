package com.b201.ecovillage.realtime.repository;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.b201.ecovillage.realtime.domain.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findByPublishedAtIsNullOrderByOccurredAtAscEventIdAsc(
        Pageable pageable
    );

    List<OutboxEvent> findTop500ByRealtimeStreamIdAndChannelSequenceGreaterThanOrderByChannelSequenceAsc(
        UUID realtimeStreamId,
        long channelSequence
    );
}
