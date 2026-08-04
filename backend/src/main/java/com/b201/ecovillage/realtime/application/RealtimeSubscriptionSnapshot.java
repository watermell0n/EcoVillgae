package com.b201.ecovillage.realtime.application;

import java.util.List;

public record RealtimeSubscriptionSnapshot(
    long currentSequence,
    long retentionFromSequence,
    List<RealtimeEventEnvelope> events,
    boolean recoveryRequired,
    String snapshotUrl
) {

    public RealtimeSubscriptionSnapshot {
        events = List.copyOf(events);
    }
}
