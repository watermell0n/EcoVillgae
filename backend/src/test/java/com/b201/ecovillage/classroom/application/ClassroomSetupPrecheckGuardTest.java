package com.b201.ecovillage.classroom.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.repository.AiRunRepository;

class ClassroomSetupPrecheckGuardTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void requiresPreflightRunBeforeFirstOpening() {
        ClassroomSetupPrecheckGuard guard = new ClassroomSetupPrecheckGuard(
            mock(AiRunRepository.class)
        );

        ClassroomOperationException error = assertThrows(
            ClassroomOperationException.class,
            () -> guard.verify(UUID.randomUUID(), null, List.of())
        );

        assertEquals("PREFLIGHT_CHECK_REQUIRED", error.getCode());
    }

    @Test
    void acceptsPassingClassroomSetupPreflight() {
        UUID classroomId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AiRunRepository runs = mock(AiRunRepository.class);
        when(runs.findByClassroomIdAndId(classroomId, runId)).thenReturn(Optional.of(
            run(runId, classroomId, "PASS", List.of())
        ));
        ClassroomSetupPrecheckGuard guard = new ClassroomSetupPrecheckGuard(runs);

        assertDoesNotThrow(() -> guard.verify(classroomId, runId, List.of()));
    }

    @Test
    void requiresEveryWarningAcknowledgement() {
        UUID classroomId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AiRunRepository runs = mock(AiRunRepository.class);
        when(runs.findByClassroomIdAndId(classroomId, runId)).thenReturn(Optional.of(
            run(runId, classroomId, "WARN", List.of(
                check("duration-warning", "WARN"),
                check("currency-ok", "PASS")
            ))
        ));
        ClassroomSetupPrecheckGuard guard = new ClassroomSetupPrecheckGuard(runs);

        ClassroomOperationException error = assertThrows(
            ClassroomOperationException.class,
            () -> guard.verify(classroomId, runId, List.of())
        );
        assertEquals("PREFLIGHT_WARNING_ACKNOWLEDGEMENT_REQUIRED", error.getCode());
        assertDoesNotThrow(() -> guard.verify(
            classroomId,
            runId,
            List.of("duration-warning")
        ));
    }

    @Test
    void neverAllowsBlockingPreflight() {
        UUID classroomId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AiRunRepository runs = mock(AiRunRepository.class);
        when(runs.findByClassroomIdAndId(classroomId, runId)).thenReturn(Optional.of(
            run(runId, classroomId, "BLOCK", List.of(check("invalid-period", "BLOCK")))
        ));
        ClassroomSetupPrecheckGuard guard = new ClassroomSetupPrecheckGuard(runs);

        ClassroomOperationException error = assertThrows(
            ClassroomOperationException.class,
            () -> guard.verify(classroomId, runId, List.of("invalid-period"))
        );

        assertEquals("PREFLIGHT_BLOCKED", error.getCode());
    }

    private static AiRun run(
        UUID runId,
        UUID classroomId,
        String severity,
        List<Map<String, Object>> checks
    ) {
        AiRun run = AiRun.generating(
            runId,
            classroomId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "SETUP_PRECHECK",
            "TEACHER_REQUEST",
            "setup-precheck-v1",
            Map.of(
                "context", Map.of("context_type", "CLASSROOM_SETUP"),
                "checks", checks
            ),
            NOW
        );
        run.awaitTeacher(
            "FAKE",
            "deterministic-v1",
            Map.of("overall_severity", severity),
            NOW
        );
        return run;
    }

    private static Map<String, Object> check(String id, String severity) {
        return Map.of("check_id", id, "severity", severity);
    }
}
