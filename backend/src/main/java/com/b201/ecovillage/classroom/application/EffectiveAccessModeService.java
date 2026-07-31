package com.b201.ecovillage.classroom.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.AutonomousSchedule;
import com.b201.ecovillage.classroom.domain.AutonomousScheduleModule;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.AutonomousScheduleModuleRepository;
import com.b201.ecovillage.classroom.repository.AutonomousScheduleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

@Service
public class EffectiveAccessModeService {

    private static final String ACCESS_POLICY = "ACCESS_MODE";

    private final ClassroomRepository classroomRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final AutonomousScheduleRepository scheduleRepository;
    private final AutonomousScheduleModuleRepository scheduleModuleRepository;
    private final ActivitySessionRepository activityRepository;
    private final Clock clock;

    public EffectiveAccessModeService(
        ClassroomRepository classroomRepository,
        ClassroomPolicyVersionRepository policyRepository,
        ClassroomModuleRepository moduleRepository,
        AutonomousScheduleRepository scheduleRepository,
        AutonomousScheduleModuleRepository scheduleModuleRepository,
        ActivitySessionRepository activityRepository,
        Clock clock
    ) {
        this.classroomRepository = classroomRepository;
        this.policyRepository = policyRepository;
        this.moduleRepository = moduleRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleModuleRepository = scheduleModuleRepository;
        this.activityRepository = activityRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccessModeResult get(UUID classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId)
            .orElseThrow(() -> new ClassroomOperationException(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        Instant now = clock.instant();
        if (activityRepository.existsByClassroomIdAndStatus(classroomId, "LIVE")) {
            return new AccessModeResult(
                AppAccessMode.GUIDED,
                "GUIDED_ACTIVITY",
                null,
                List.of(),
                classroom.getStateVersion()
            );
        }

        ClassroomPolicyVersion manualPolicy = policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                classroomId,
                ACCESS_POLICY
            )
            .orElse(null);
        AccessModeResult manual = manual(classroom, manualPolicy, now);
        if (manual != null) {
            return manual;
        }

        List<AutonomousSchedule> schedules = scheduleRepository
            .findAllByClassroomIdAndStatusOrderByDayOfWeekAscLocalStartTimeAsc(
                classroomId,
                "ACTIVE"
            );
        AutonomousSchedule active = schedules.stream()
            .filter(AutonomousSchedule::isEnabled)
            .filter(schedule -> includes(schedule, now))
            .findFirst()
            .orElse(null);
        if (active == null) {
            return new AccessModeResult(
                AppAccessMode.CLOSED,
                "DEFAULT",
                null,
                List.of(),
                classroom.getStateVersion()
            );
        }

        Set<String> activeModules = new HashSet<>(
            moduleRepository.findAllByClassroomIdAndStatusOrderByModuleCode(
                classroomId,
                "ACTIVE"
            ).stream().map(module -> module.getModuleCode()).toList()
        );
        List<String> scheduledModules = scheduleModuleRepository
            .findAllByAutonomousScheduleIdIn(List.of(active.getId()))
            .stream()
            .map(AutonomousScheduleModule::getModuleCode)
            .filter(activeModules::contains)
            .sorted()
            .toList();
        ZonedDateTime local = now.atZone(ZoneId.of(active.getTimeZone()));
        Instant expiresAt = local
            .with(active.getLocalEndTime())
            .toInstant();
        return new AccessModeResult(
            AppAccessMode.AUTONOMOUS,
            "RECURRING_SCHEDULE",
            expiresAt,
            scheduledModules,
            classroom.getStateVersion()
        );
    }

    private AccessModeResult manual(
        Classroom classroom,
        ClassroomPolicyVersion policy,
        Instant now
    ) {
        if (policy == null || classroom.getAppModeOverride() == null) {
            return null;
        }
        if ("CLOSED".equals(classroom.getAppModeOverride())) {
            return new AccessModeResult(
                AppAccessMode.CLOSED,
                "MANUAL",
                null,
                List.of(),
                classroom.getStateVersion()
            );
        }
        Instant expiresAt = expiresAt(policy.getPolicyValues());
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return null;
        }
        List<String> moduleCodes = moduleRepository
            .findAllByClassroomIdAndStatusOrderByModuleCode(classroom.getId(), "ACTIVE")
            .stream()
            .map(module -> module.getModuleCode())
            .toList();
        return new AccessModeResult(
            AppAccessMode.AUTONOMOUS,
            "MANUAL",
            expiresAt,
            moduleCodes,
            classroom.getStateVersion()
        );
    }

    private static boolean includes(AutonomousSchedule schedule, Instant now) {
        ZonedDateTime local = now.atZone(ZoneId.of(schedule.getTimeZone()));
        LocalTime time = local.toLocalTime();
        return local.getDayOfWeek().getValue() == schedule.getDayOfWeek()
            && !time.isBefore(schedule.getLocalStartTime())
            && time.isBefore(schedule.getLocalEndTime());
    }

    static Instant expiresAt(Map<String, Object> values) {
        Object value = values.get("expires_at");
        return value == null ? null : Instant.parse(value.toString());
    }
}
