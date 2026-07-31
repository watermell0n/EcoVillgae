package com.b201.ecovillage.realtime.application;

import java.util.Optional;
import java.util.UUID;

public interface DisplaySessionStore {

    void save(String tokenHash, DisplaySessionRecord record);

    Optional<DisplaySessionRecord> find(UUID displaySessionId, String tokenHash);

    boolean revoke(UUID displaySessionId);

    void revokeAllForActivity(UUID activityId);
}
