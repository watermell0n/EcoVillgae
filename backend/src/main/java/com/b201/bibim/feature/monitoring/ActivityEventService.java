package com.b201.bibim.feature.monitoring;

import java.util.Map;
import java.util.UUID;

import com.b201.bibim.domain.monitoring.ActivityEventEntity;
import com.b201.bibim.domain.monitoring.ActivityEventRepository;

import org.springframework.stereotype.Service;

@Service
public class ActivityEventService {

    private final ActivityEventRepository activityEventRepository;

    public ActivityEventService(ActivityEventRepository activityEventRepository) {
        this.activityEventRepository = activityEventRepository;
    }

    public void record(
            UUID roomId,
            UUID studentId,
            String actorType,
            UUID actorId,
            String eventType,
            String stage,
            String module,
            String page,
            Map<String, Object> metadata
    ) {
        activityEventRepository.save(ActivityEventEntity.create(
                roomId,
                studentId,
                actorType,
                actorId,
                eventType,
                stage,
                module,
                page,
                null,
                metadata
        ));
    }
}
