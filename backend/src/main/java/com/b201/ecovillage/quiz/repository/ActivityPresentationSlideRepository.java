package com.b201.ecovillage.quiz.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.quiz.domain.ActivityPresentationSlide;

public interface ActivityPresentationSlideRepository
    extends JpaRepository<ActivityPresentationSlide, UUID> {

    List<ActivityPresentationSlide> findAllByActivitySessionIdOrderBySequenceNumber(
        UUID activitySessionId
    );

    Optional<ActivityPresentationSlide> findByActivitySessionIdAndSequenceNumber(
        UUID activitySessionId,
        int sequenceNumber
    );

    int countByActivitySessionId(UUID activitySessionId);

    boolean existsByActivitySessionId(UUID activitySessionId);
}
