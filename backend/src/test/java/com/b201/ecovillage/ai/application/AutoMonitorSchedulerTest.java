package com.b201.ecovillage.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.b201.ecovillage.ai.infrastructure.AiMonitorProperties;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.repository.ClassroomRepository;

class AutoMonitorSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");

    @Test
    void schedulesTheLatestCompletedSevenDayLocalPeriodThroughTheApplicationService() {
        ClassroomRepository classrooms = mock(ClassroomRepository.class);
        OperationalAnalysisService service = mock(OperationalAnalysisService.class);
        AiMonitorProperties properties = new AiMonitorProperties();
        Classroom classroom = classroom();
        when(classrooms.findAll()).thenReturn(List.of(classroom));
        AutoMonitorScheduler scheduler = new AutoMonitorScheduler(
            classrooms,
            service,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        scheduler.monitorCompletedSevenDayPeriods();

        ArgumentCaptor<OperationalAnalysisCommand> captor =
            ArgumentCaptor.forClass(OperationalAnalysisCommand.class);
        verify(service).executeSystem(captor.capture());
        assertEquals(Instant.parse("2026-07-24T15:00:00Z"), captor.getValue().periodFrom());
        assertEquals(Instant.parse("2026-07-31T15:00:00Z"), captor.getValue().periodTo());
    }

    @Test
    void disabledMonitorDoesNotScheduleAnything() {
        ClassroomRepository classrooms = mock(ClassroomRepository.class);
        OperationalAnalysisService service = mock(OperationalAnalysisService.class);
        AiMonitorProperties properties = new AiMonitorProperties();
        properties.setEnabled(false);
        AutoMonitorScheduler scheduler = new AutoMonitorScheduler(
            classrooms,
            service,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        scheduler.monitorCompletedSevenDayPeriods();

        verify(service, never()).executeSystem(any());
    }

    private Classroom classroom() {
        return Classroom.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "MONITOR1",
            "ECO",
            5,
            CourseSize.ONE_MONTH,
            20,
            LocalDate.parse("2026-07-04"),
            LocalDate.parse("2026-08-31"),
            "Asia/Seoul",
            NOW
        );
    }
}
