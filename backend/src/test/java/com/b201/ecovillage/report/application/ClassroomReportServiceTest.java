package com.b201.ecovillage.report.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.report.repository.ClassroomReportProjectionRepository;

@ExtendWith(MockitoExtension.class)
class ClassroomReportServiceTest {

    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant GENERATED_AT =
        Instant.parse("2026-08-01T01:00:00Z");

    @Mock
    private ClassroomAuthorizationService authorizationService;

    @Mock
    private ClassroomReportProjectionRepository repository;

    @Test
    void returnsOnlyClassAggregateFinancialAndQuizMetrics() {
        UUID classroomId = UUID.randomUUID();
        when(repository.aggregate(classroomId, FROM, TO)).thenReturn(
            new ClassroomReportAggregate(900, 320, 600, 250, 3, 2, 5)
        );
        ClassroomReportService service = new ClassroomReportService(
            authorizationService,
            repository,
            Clock.fixed(GENERATED_AT, ZoneOffset.UTC)
        );

        ClassroomReportView result = service.get(
            "teacher-subject",
            classroomId,
            FROM,
            TO
        );

        assertEquals(1_500, result.totalCurrencyAmount());
        assertEquals(900, result.walletBalanceAmount());
        assertEquals(300, result.averageWalletBalance());
        assertEquals(320, result.spendingAmount());
        assertEquals(600, result.savingsPrincipalAmount());
        assertEquals(250, result.publicFundBalance());
        assertEquals(3, result.activeStudentCount());
        assertEquals(2, result.quizParticipantCount());
        assertEquals(5, result.quizSubmissionCount());
        assertEquals(GENERATED_AT, result.generatedAt());
        verify(authorizationService).authorize(
            "teacher-subject",
            classroomId,
            ClassroomPermission.READ_CLASSROOM_REPORT,
            null
        );
    }

    @Test
    void rejectsAnEmptyOrReversedPeriodBeforeQuerying() {
        ClassroomReportService service = new ClassroomReportService(
            authorizationService,
            repository,
            Clock.fixed(GENERATED_AT, ZoneOffset.UTC)
        );

        assertThrows(
            ClassroomOperationException.class,
            () -> service.get(
                "teacher-subject",
                UUID.randomUUID(),
                TO,
                FROM
            )
        );
    }
}
