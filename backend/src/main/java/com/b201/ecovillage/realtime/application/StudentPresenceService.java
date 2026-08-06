package com.b201.ecovillage.realtime.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

@Service
public class StudentPresenceService {

    private static final Duration CONNECTION_STALE_AFTER = Duration.ofSeconds(75);

    private final ConcurrentMap<StudentKey, StudentEntry> students = new ConcurrentHashMap<>();

    public StudentPresenceSnapshot connect(
        UUID classroomId,
        UUID studentId,
        String connectionId,
        Instant connectedAt
    ) {
        return update(classroomId, studentId, connectionId, "ONLINE", null, connectedAt);
    }

    public StudentPresenceSnapshot update(
        UUID classroomId,
        UUID studentId,
        String connectionId,
        String status,
        String currentScreen,
        Instant seenAt
    ) {
        requireStatus(status);
        StudentEntry entry = students.computeIfAbsent(
            new StudentKey(classroomId, studentId),
            ignored -> new StudentEntry()
        );
        synchronized (entry) {
            String normalizedScreen = normalizeScreen(currentScreen);
            entry.connections.put(
                connectionId,
                new ConnectionState(status, normalizedScreen, seenAt)
            );
            entry.everSeen = true;
            entry.lastSeenAt = seenAt;
            if (normalizedScreen != null) entry.lastScreen = normalizedScreen;
            return snapshot(classroomId, studentId, entry, seenAt);
        }
    }

    public void touch(
        UUID classroomId,
        UUID studentId,
        String connectionId,
        Instant seenAt
    ) {
        StudentEntry entry = students.get(new StudentKey(classroomId, studentId));
        if (entry == null) return;
        synchronized (entry) {
            ConnectionState current = entry.connections.get(connectionId);
            if (current == null) return;
            entry.connections.put(
                connectionId,
                new ConnectionState(current.status(), current.currentScreen(), seenAt)
            );
            entry.lastSeenAt = seenAt;
        }
    }

    public StudentPresenceSnapshot disconnect(
        UUID classroomId,
        UUID studentId,
        String connectionId,
        Instant disconnectedAt
    ) {
        StudentEntry entry = students.computeIfAbsent(
            new StudentKey(classroomId, studentId),
            ignored -> new StudentEntry()
        );
        synchronized (entry) {
            ConnectionState removed = entry.connections.remove(connectionId);
            entry.everSeen = entry.everSeen || removed != null;
            entry.lastSeenAt = disconnectedAt;
            if (removed != null && removed.currentScreen() != null) {
                entry.lastScreen = removed.currentScreen();
            }
            return snapshot(classroomId, studentId, entry, disconnectedAt);
        }
    }

    public StudentPresenceSnapshot get(
        UUID classroomId,
        UUID studentId,
        Instant now
    ) {
        StudentEntry entry = students.get(new StudentKey(classroomId, studentId));
        if (entry == null) {
            return new StudentPresenceSnapshot(
                classroomId,
                studentId,
                "UNKNOWN",
                null,
                null
            );
        }
        synchronized (entry) {
            return snapshot(classroomId, studentId, entry, now);
        }
    }

    public List<StudentPresenceSnapshot> expireStale(Instant now) {
        List<StudentPresenceSnapshot> changed = new ArrayList<>();
        students.forEach((key, entry) -> {
            synchronized (entry) {
                String before = status(entry);
                StudentPresenceSnapshot snapshot = snapshot(
                    key.classroomId(),
                    key.studentId(),
                    entry,
                    now
                );
                if (!before.equals(snapshot.status())) changed.add(snapshot);
            }
        });
        return List.copyOf(changed);
    }

    private StudentPresenceSnapshot snapshot(
        UUID classroomId,
        UUID studentId,
        StudentEntry entry,
        Instant now
    ) {
        Instant staleBefore = now.minus(CONNECTION_STALE_AFTER);
        entry.connections.entrySet().removeIf(
            connection -> connection.getValue().lastSeenAt().isBefore(staleBefore)
        );
        ConnectionState latestOnline = latest(entry, "ONLINE");
        ConnectionState latestAway = latest(entry, "AWAY");
        ConnectionState selected = latestOnline != null ? latestOnline : latestAway;
        String status = status(entry);
        String screen = selected != null && selected.currentScreen() != null
            ? selected.currentScreen()
            : entry.lastScreen;
        Instant lastSeen = selected != null ? selected.lastSeenAt() : entry.lastSeenAt;
        return new StudentPresenceSnapshot(
            classroomId,
            studentId,
            status,
            screen,
            lastSeen
        );
    }

    private static ConnectionState latest(StudentEntry entry, String status) {
        return entry.connections.values().stream()
            .filter(connection -> status.equals(connection.status()))
            .max(Comparator.comparing(ConnectionState::lastSeenAt))
            .orElse(null);
    }

    private static String status(StudentEntry entry) {
        if (latest(entry, "ONLINE") != null) return "ONLINE";
        if (latest(entry, "AWAY") != null) return "AWAY";
        return entry.everSeen ? "OFFLINE" : "UNKNOWN";
    }

    private static void requireStatus(String status) {
        if (!"ONLINE".equals(status) && !"AWAY".equals(status)) {
            throw new RealtimeProtocolException(
                "INVALID_PRESENCE_STATUS",
                "Presence status must be ONLINE or AWAY"
            );
        }
    }

    private static String normalizeScreen(String currentScreen) {
        if (currentScreen == null || currentScreen.isBlank()) return null;
        String normalized = currentScreen.trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private record StudentKey(UUID classroomId, UUID studentId) {
    }

    private record ConnectionState(
        String status,
        String currentScreen,
        Instant lastSeenAt
    ) {
    }

    private static final class StudentEntry {
        private final Map<String, ConnectionState> connections = new HashMap<>();
        private boolean everSeen;
        private String lastScreen;
        private Instant lastSeenAt;
    }

    public record StudentPresenceSnapshot(
        UUID classroomId,
        UUID studentId,
        String status,
        String currentScreen,
        Instant lastSeenAt
    ) {
    }
}
