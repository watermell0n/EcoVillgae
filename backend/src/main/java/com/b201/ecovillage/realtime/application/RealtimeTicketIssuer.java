package com.b201.ecovillage.realtime.application;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.security.EcoVillagePrincipal;
import com.b201.ecovillage.realtime.config.RealtimeProperties;

@Service
public class RealtimeTicketIssuer {

    private final RealtimeTicketStore ticketStore;
    private final ClassroomRepository classroomRepository;
    private final ClassroomAuthorizationService authorizationService;
    private final RealtimeProperties properties;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public RealtimeTicketIssuer(
        RealtimeTicketStore ticketStore,
        ClassroomRepository classroomRepository,
        ClassroomAuthorizationService authorizationService,
        RealtimeProperties properties,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.ticketStore = ticketStore;
        this.classroomRepository = classroomRepository;
        this.authorizationService = authorizationService;
        this.properties = properties;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public IssuedRealtimeTicket issue(
        EcoVillagePrincipal principal,
        Set<String> requestedChannelIds,
        Set<UUID> requestedResourceIds
    ) {
        Set<String> channelIds = new LinkedHashSet<>(requestedChannelIds);
        if (channelIds.isEmpty()) {
            throw invalid("CHANNELS_REQUIRED", "At least one realtime channel is required");
        }
        channelIds.forEach(channelId -> RealtimeChannel.require(channelId).requireRole(principal.role()));

        Set<UUID> resourceIds = requestedResourceIds == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(requestedResourceIds);
        UUID classroomId = resolveClassroom(principal, resourceIds);
        authorizationService.authorize(
            principal.getName(),
            classroomId,
            ClassroomPermission.READ_MODULES_AND_POLICIES,
            principal.isStudent() ? principal.actorId() : null
        );
        resourceIds.add(classroomId);
        if (principal.isStudent()) {
            resourceIds.add(principal.actorId());
        }

        RealtimeTicket ticket = new RealtimeTicket(
            uuidGenerator.generate(),
            principal.sessionId(),
            principal.getName(),
            principal.actorId(),
            principal.role(),
            classroomId,
            channelIds,
            resourceIds,
            clock.instant().plus(properties.ticketTtl())
        );
        return ticketStore.issue(ticket);
    }

    private UUID resolveClassroom(
        EcoVillagePrincipal principal,
        Set<UUID> resourceIds
    ) {
        if (principal.isStudent()) {
            return principal.classroomId();
        }
        List<UUID> classroomIds = classroomRepository.findAllById(resourceIds)
            .stream()
            .map(Classroom::getId)
            .toList();
        if (classroomIds.size() != 1) {
            throw invalid(
                "CLASSROOM_RESOURCE_REQUIRED",
                "A teacher realtime ticket must contain exactly one classroom resource"
            );
        }
        return classroomIds.getFirst();
    }

    private static ClassroomOperationException invalid(String code, String message) {
        return new ClassroomOperationException(HttpStatus.BAD_REQUEST, code, message);
    }
}
