package com.b201.ecovillage.classroom.application;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.AutonomousSchedule;
import com.b201.ecovillage.classroom.domain.AutonomousScheduleModule;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ModuleCatalog;
import com.b201.ecovillage.classroom.repository.AutonomousScheduleModuleRepository;
import com.b201.ecovillage.classroom.repository.AutonomousScheduleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;

@Service
public class ClassroomCreationService {

    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final Pattern CURRENCY_NAME =
        Pattern.compile("^[가-힣A-Za-z0-9]{1,10}$");
    private static final Pattern MODULE_CODE =
        Pattern.compile("^[A-Z][A-Z0-9_]{0,39}$");
    private static final Set<String> FORBIDDEN_CURRENCY_FRAGMENTS = Set.of(
        "관리자",
        "운영자",
        "이코빌리지",
        "admin",
        "administrator",
        "official",
        "ecovillage"
    );

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomTeacherRepository classroomTeacherRepository;
    private final ClassroomModuleRepository classroomModuleRepository;
    private final AutonomousScheduleRepository scheduleRepository;
    private final AutonomousScheduleModuleRepository scheduleModuleRepository;
    private final ClassroomCodeGenerator classroomCodeGenerator;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public ClassroomCreationService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        ClassroomTeacherRepository classroomTeacherRepository,
        ClassroomModuleRepository classroomModuleRepository,
        AutonomousScheduleRepository scheduleRepository,
        AutonomousScheduleModuleRepository scheduleModuleRepository,
        ClassroomCodeGenerator classroomCodeGenerator,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.classroomTeacherRepository = classroomTeacherRepository;
        this.classroomModuleRepository = classroomModuleRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleModuleRepository = scheduleModuleRepository;
        this.classroomCodeGenerator = classroomCodeGenerator;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public CreateClassroomResult create(CreateClassroomCommand command) {
        ValidatedCreation validated = validate(command);
        UserAccount owner = authorizationService.requireTeacherInSchool(
            command.teacherSubject(),
            command.schoolId()
        );
        Instant now = clock.instant();
        UUID classroomId = uuidGenerator.generate();
        String classroomCode = uniqueClassroomCode();

        Classroom classroom = Classroom.create(
            classroomId,
            command.schoolId(),
            classroomCode,
            validated.currencyName(),
            command.grade(),
            command.courseSize(),
            command.studentCount(),
            command.startsOn(),
            command.endsOn(),
            command.timeZone(),
            now
        );
        classroom.updateDisplaySettings(
            command.displayName(),
            command.classNumber(),
            command.description(),
            now
        );
        classroomRepository.save(classroom);
        classroomTeacherRepository.save(
            ClassroomTeacher.assign(
                uuidGenerator.generate(),
                classroomId,
                owner.getId(),
                ClassroomTeacherRole.OWNER,
                now
            )
        );
        List<ClassroomModule> modules = validated.moduleCodes().stream()
            .map(code -> createRequestedModule(classroomId, code, now))
            .toList();
        classroomModuleRepository.saveAll(modules);
        saveSchedules(
            classroomId,
            command.timeZone(),
            validated.moduleCodes(),
            command.autonomousSchedules(),
            now
        );
        classroomRepository.flush();

        return new CreateClassroomResult(
            classroom.getId(),
            classroom.getClassroomCode(),
            classroom.getCurrencyName(),
            classroom.getStatus().name(),
            classroom.getStateVersion()
        );
    }

    private ClassroomModule createRequestedModule(UUID classroomId, String code, Instant now) {
        ModuleCatalog.Definition definition = ModuleCatalog.require(code);
        // Investment has no rate policy, but the teacher must choose its operating mode
        // before students can use it. Treat that choice exactly like an initial policy.
        boolean requiresInitialConfiguration = definition.initialPolicyRequired()
            || "INVESTMENT".equals(definition.code());
        ClassroomModule module = requiresInitialConfiguration
            ? ClassroomModule.pendingConfiguration(uuidGenerator.generate(), classroomId, code, now)
            : ClassroomModule.disabled(uuidGenerator.generate(), classroomId, code, now);
        if (!requiresInitialConfiguration && definition.prerequisites().isEmpty()) {
            module.activate(Map.of("activation_source", "CLASSROOM_CREATION"), now);
        }
        return module;
    }

    private ValidatedCreation validate(CreateClassroomCommand command) {
        if (command.displayName() != null && command.displayName().isBlank()) {
            throw validation("display_name은 빈 값일 수 없습니다.");
        }
        if (command.grade() < 4 || command.grade() > 6) {
            throw validation("grade는 4~6만 허용합니다.");
        }
        if (command.studentCount() <= 0) {
            throw validation("student_count는 1 이상이어야 합니다.");
        }
        if (command.startsOn() == null || command.endsOn() == null) {
            throw validation("운영 시작일과 종료일은 필수입니다.");
        }
        if (command.endsOn().isBefore(command.startsOn())) {
            throw validation("종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (command.expectedStateVersion() != null && command.expectedStateVersion() != 0) {
            throw new ClassroomOperationException(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "경제교실 생성의 예상 상태 버전은 0이어야 합니다."
            );
        }
        try {
            ZoneId.of(command.timeZone());
        } catch (ZoneRulesException | NullPointerException exception) {
            throw validation("유효한 IANA 시간대를 입력해 주세요.");
        }

        String currencyName = normalizeCurrencyName(command.currencyName());
        if (command.salaryPolicy() != null && !command.salaryPolicy().isEmpty()) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "Initial salary policy must be confirmed when its module is activated."
            );
        }
        List<String> moduleCodes = normalizeModuleCodes(command.moduleCodes());
        if (moduleCodes.contains("SAVINGS") && command.courseSize() != CourseSize.SEMESTER) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "MODULE_NOT_AVAILABLE_FOR_COURSE_SIZE",
                "정기적금 모듈은 한 학기 운영에서만 사용할 수 있습니다."
            );
        }
        if (command.autonomousSchedules() == null) {
            return new ValidatedCreation(currencyName, moduleCodes);
        }
        for (AutonomousScheduleCommand schedule : command.autonomousSchedules()) {
            validateSchedule(schedule, moduleCodes);
        }
        return new ValidatedCreation(currencyName, moduleCodes);
    }

    private static String normalizeCurrencyName(String value) {
        if (value == null) {
            throw invalidCurrencyName();
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean forbidden = FORBIDDEN_CURRENCY_FRAGMENTS.stream().anyMatch(lower::contains);
        if (!CURRENCY_NAME.matcher(normalized).matches() || forbidden) {
            throw invalidCurrencyName();
        }
        return normalized;
    }

    private static List<String> normalizeModuleCodes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> requested = values.stream()
            .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
            .toList();
        if (
            requested.stream().anyMatch(value -> !MODULE_CODE.matcher(value).matches())
        ) {
            throw validation("모듈 코드는 형식에 맞고 중복되지 않아야 합니다.");
        }
        List<String> normalized = requested.stream()
            .map(code -> ModuleCatalog.find(code).orElseThrow(() -> new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNSUPPORTED_MODULE_CODE",
                "지원하지 않는 모듈 코드입니다: " + code
            )).code())
            .toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw validation("모듈 코드는 형식에 맞고 중복되지 않아야 합니다.");
        }
        return normalized;
    }

    private static void validateSchedule(
        AutonomousScheduleCommand schedule,
        List<String> selectedModuleCodes
    ) {
        if (
            schedule == null
                || schedule.dayOfWeek() < 1
                || schedule.dayOfWeek() > 7
                || schedule.localStartTime() == null
                || schedule.localEndTime() == null
                || !schedule.localEndTime().isAfter(schedule.localStartTime())
        ) {
            throw validation("자율시간 일정의 요일과 시간을 확인해 주세요.");
        }
        List<String> codes = schedule.moduleCodes() == null
            ? List.of()
            : schedule.moduleCodes().stream()
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .map(code -> ModuleCatalog.find(code).orElseThrow(() -> new ClassroomOperationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNSUPPORTED_MODULE_CODE",
                    "지원하지 않는 모듈 코드입니다: " + code
                )).code())
                .toList();
        if (new HashSet<>(codes).size() != codes.size() || !selectedModuleCodes.containsAll(codes)) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "자율시간에는 선택된 모듈만 연결할 수 있습니다."
            );
        }
    }

    private String uniqueClassroomCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String candidate = classroomCodeGenerator.generate();
            if (!classroomRepository.existsByClassroomCode(candidate)) {
                return candidate;
            }
        }
        throw new ClassroomOperationException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "CLASSROOM_CODE_GENERATION_FAILED",
            "학급 코드를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."
        );
    }

    private void saveSchedules(
        UUID classroomId,
        String timeZone,
        List<String> selectedModuleCodes,
        List<AutonomousScheduleCommand> commands,
        Instant now
    ) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        List<AutonomousSchedule> schedules = new ArrayList<>();
        List<AutonomousScheduleModule> links = new ArrayList<>();
        for (AutonomousScheduleCommand command : commands) {
            UUID scheduleId = uuidGenerator.generate();
            schedules.add(
                AutonomousSchedule.create(
                    scheduleId,
                    classroomId,
                    command.dayOfWeek(),
                    command.localStartTime(),
                    command.localEndTime(),
                    timeZone,
                    now
                )
            );
            List<String> moduleCodes = command.moduleCodes() == null
                ? selectedModuleCodes
                : command.moduleCodes();
            for (String moduleCode : moduleCodes) {
                links.add(
                    AutonomousScheduleModule.connect(
                        uuidGenerator.generate(),
                        classroomId,
                        scheduleId,
                        moduleCode.toUpperCase(Locale.ROOT),
                        now
                    )
                );
            }
        }
        scheduleRepository.saveAll(schedules);
        scheduleModuleRepository.saveAll(links);
    }

    private static ClassroomOperationException invalidCurrencyName() {
        return new ClassroomOperationException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "INVALID_CURRENCY_NAME",
            "화폐 단위명은 허용된 한글·영문·숫자 1~10자로 입력해 주세요."
        );
    }

    private static ClassroomOperationException validation(String message) {
        return new ClassroomOperationException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            message
        );
    }

    private record ValidatedCreation(String currencyName, List<String> moduleCodes) {
    }
}
