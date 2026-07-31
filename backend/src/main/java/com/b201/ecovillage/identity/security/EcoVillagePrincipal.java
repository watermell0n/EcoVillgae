package com.b201.ecovillage.identity.security;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

public record EcoVillagePrincipal(
    String name,
    UUID sessionId,
    UUID actorId,
    String role,
    UUID classroomId,
    Instant accessTokenExpiresAt
) implements Principal {

    @Override
    public String getName() {
        return name;
    }

    public boolean isTeacher() {
        return "TCH".equals(role);
    }

    public boolean isStudent() {
        return "STU".equals(role);
    }
}
