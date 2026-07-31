package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.AutonomousSchedule;
import com.b201.ecovillage.classroom.domain.AutonomousScheduleModule;
import com.b201.ecovillage.classroom.repository.AutonomousScheduleModuleRepository;
import com.b201.ecovillage.classroom.repository.AutonomousScheduleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.repository.ClassroomRepository;

@Service
public class ClassroomConfigurationService {

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomModuleRepository classroomModuleRepository;
    private final AutonomousScheduleRepository scheduleRepository;
    private final AutonomousScheduleModuleRepository scheduleModuleRepository;
    private final ClassroomPolicyVersionRepository policyVersionRepository;

    public ClassroomConfigurationService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        ClassroomModuleRepository classroomModuleRepository,
        AutonomousScheduleRepository scheduleRepository,
        AutonomousScheduleModuleRepository scheduleModuleRepository,
        ClassroomPolicyVersionRepository policyVersionRepository
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.classroomModuleRepository = classroomModuleRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleModuleRepository = scheduleModuleRepository;
        this.policyVersionRepository = policyVersionRepository;
    }

    @Transactional(readOnly = true)
    public ClassroomConfigurationResult get(String teacherSubject, UUID classroomId) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.READ_CONFIGURATION,
            null
        );
        Classroom classroom = classroomRepository
            .findById(classroomId)
            .orElseThrow(() -> new ClassroomOperationException(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "경제교실을 찾을 수 없습니다."
            ));
        List<String> moduleCodes = classroomModuleRepository
            .findAllByClassroomIdOrderByModuleCode(classroomId)
            .stream()
            .map(module -> module.getModuleCode())
            .toList();
        List<AutonomousSchedule> schedules = scheduleRepository
            .findAllByClassroomIdAndStatusOrderByDayOfWeekAscLocalStartTimeAsc(
                classroomId,
                "ACTIVE"
            );
        Map<UUID, List<String>> scheduleModules = scheduleModuleRepository
            .findAllByAutonomousScheduleIdIn(
                schedules.stream().map(AutonomousSchedule::getId).toList()
            )
            .stream()
            .collect(
                Collectors.groupingBy(
                    AutonomousScheduleModule::getAutonomousScheduleId,
                    Collectors.mapping(
                        AutonomousScheduleModule::getModuleCode,
                        Collectors.toList()
                    )
                )
            );

        List<ClassroomConfigurationResult.ScheduleResult> scheduleResults = schedules
            .stream()
            .map(schedule -> new ClassroomConfigurationResult.ScheduleResult(
                schedule.getId(),
                "%d %s-%s".formatted(
                    schedule.getDayOfWeek(),
                    schedule.getLocalStartTime(),
                    schedule.getLocalEndTime()
                ),
                schedule.getStatus(),
                Math.toIntExact(schedule.getStateVersion()),
                schedule.getStateVersion(),
                scheduleModules.getOrDefault(schedule.getId(), List.of())
            ))
            .toList();
        List<ClassroomConfigurationResult.PolicyVersionResult> policies =
            policyVersionRepository
                .findAllByClassroomIdAndSupersededAtIsNullOrderByPolicyArea(classroomId)
                .stream()
                .map(policy -> new ClassroomConfigurationResult.PolicyVersionResult(
                    policy.getId(),
                    policy.getPolicyArea(),
                    policy.getStatus(),
                    policy.getVersion(),
                    policy.getVersion(),
                    policy.getPolicyArea()
                ))
                .toList();

        return new ClassroomConfigurationResult(
            classroom.getId(),
            classroom.getClassroomCode(),
            classroom.getCurrencyName(),
            moduleCodes,
            scheduleResults,
            policies
        );
    }
}
