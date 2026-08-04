package com.b201.ecovillage.realtime.application;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum RealtimeChannel {
    CLASSROOM("WS-CLS-001", "CLASSROOM_LIFECYCLE", Set.of("TCH", "STU"), Scope.CLASSROOM),
    STUDENT_PRIVATE("WS-STU-001", "STUDENT_PRIVATE", Set.of("STU"), Scope.SELF),
    TEACHER_CONTROL("WS-TCH-001", "TEACHER_CONTROL", Set.of("TCH"), Scope.CLASSROOM),
    ECONOMY("WS-ECO-001", "ECONOMY", Set.of("TCH", "STU"), Scope.CLASSROOM),
    ACTIVITY("WS-ACT-001", "ACTIVITY", Set.of("TCH", "STU"), Scope.RESOURCE),
    QUIZ("WS-QZ-001", "QUIZ", Set.of("TCH", "STU"), Scope.RESOURCE),
    VOTE("WS-VOT-001", "VOTE", Set.of("TCH", "STU"), Scope.RESOURCE),
    INVESTMENT("WS-IVT-001", "INVESTMENT_MARKET", Set.of("TCH", "STU"), Scope.CLASSROOM),
    DISPLAY("WS-DSP-001", "PUBLIC_DISPLAY", Set.of(), Scope.RESOURCE),
    OPERATION("WS-OPS-001", "OPERATION", Set.of("TCH", "STU"), Scope.RESOURCE);

    private static final Map<String, RealtimeChannel> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(RealtimeChannel::channelId, Function.identity()));

    private final String channelId;
    private final String streamType;
    private final Set<String> allowedRoles;
    private final Scope scope;

    RealtimeChannel(
        String channelId,
        String streamType,
        Set<String> allowedRoles,
        Scope scope
    ) {
        this.channelId = channelId;
        this.streamType = streamType;
        this.allowedRoles = Set.copyOf(allowedRoles);
        this.scope = scope;
    }

    public static RealtimeChannel require(String channelId) {
        RealtimeChannel channel = BY_ID.get(channelId);
        if (channel == null) {
            throw new RealtimeProtocolException("UNKNOWN_CHANNEL", "Unknown realtime channel");
        }
        return channel;
    }

    public void requireRole(String role) {
        if (!allowedRoles.contains(role)) {
            throw new RealtimeProtocolException(
                "CHANNEL_ROLE_NOT_ALLOWED",
                "The authenticated role cannot subscribe to this channel"
            );
        }
    }

    public String channelId() {
        return channelId;
    }

    public String streamType() {
        return streamType;
    }

    public Scope scope() {
        return scope;
    }

    public enum Scope {
        CLASSROOM,
        SELF,
        RESOURCE
    }
}
