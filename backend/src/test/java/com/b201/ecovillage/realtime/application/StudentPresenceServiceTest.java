package com.b201.ecovillage.realtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StudentPresenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void remainsOnlineWhileAnyStudentConnectionIsOnline() {
        StudentPresenceService service = new StudentPresenceService();
        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        service.connect(classroomId, studentId, "socket-1", NOW);
        service.connect(classroomId, studentId, "socket-2", NOW.plusSeconds(1));
        service.update(
            classroomId,
            studentId,
            "socket-1",
            "AWAY",
            "내 지갑",
            NOW.plusSeconds(2)
        );

        assertEquals(
            "ONLINE",
            service.get(classroomId, studentId, NOW.plusSeconds(3)).status()
        );
        assertEquals(
            "ONLINE",
            service.disconnect(
                classroomId,
                studentId,
                "socket-1",
                NOW.plusSeconds(4)
            ).status()
        );
        assertEquals(
            "OFFLINE",
            service.disconnect(
                classroomId,
                studentId,
                "socket-2",
                NOW.plusSeconds(5)
            ).status()
        );
    }

    @Test
    void staleConnectionBecomesOffline() {
        StudentPresenceService service = new StudentPresenceService();
        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        service.update(
            classroomId,
            studentId,
            "socket-1",
            "ONLINE",
            "마을 광장",
            NOW
        );

        StudentPresenceService.StudentPresenceSnapshot snapshot = service
            .expireStale(NOW.plusSeconds(76))
            .getFirst();
        assertEquals("OFFLINE", snapshot.status());
        assertEquals("마을 광장", snapshot.currentScreen());
        assertEquals(0, service.expireStale(NOW.plusSeconds(90)).size());
    }
}
