package com.b201.ecovillage.ai.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.contract.TeacherChatContract;
import com.b201.ecovillage.ai.domain.AiArtifact;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.TeacherChatAiClient;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * 교사용 AI 챗봇 실행을 담당한다. 지표 집계는 기존 규칙 기반 집계기를 재사용하고
 * AI에는 집계된 학급 사실만 넘긴다. 학생 실명·개인 잔액·개인 대화는 입력에 넣지 않는다.
 */
@Service
public class TeacherChatService {

    static final String RUN_TYPE = "TEACHER_CHAT";
    static final int DAILY_LIMIT = 30;
    private static final Logger log = LoggerFactory.getLogger(TeacherChatService.class);
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(20);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final EconomicMetricAggregationService aggregationService;
    private final EconomicMetricEvaluator evaluator;
    private final TeacherChatAiClient aiClient;
    private final TeacherChatFallbackFactory fallbackFactory;
    private final ClassroomAiSettings aiSettings;
    private final AiRunRepository runRepository;
    private final AiArtifactRepository artifactRepository;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TeacherChatService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomModuleRepository moduleRepository,
        ClassroomPolicyVersionRepository policyRepository,
        EconomicMetricAggregationService aggregationService,
        EconomicMetricEvaluator evaluator,
        TeacherChatAiClient aiClient,
        TeacherChatFallbackFactory fallbackFactory,
        ClassroomAiSettings aiSettings,
        AiRunRepository runRepository,
        AiArtifactRepository artifactRepository,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.moduleRepository = moduleRepository;
        this.policyRepository = policyRepository;
        this.aggregationService = aggregationService;
        this.evaluator = evaluator;
        this.aiClient = aiClient;
        this.fallbackFactory = fallbackFactory;
        this.aiSettings = aiSettings;
        this.runRepository = runRepository;
        this.artifactRepository = artifactRepository;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public TeacherChatResult ask(TeacherChatCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.REQUEST_AI_ANALYSIS,
            null
        );
        UserAccount teacher = userRepository.findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A teacher session is required."
            ));
        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom was not found."
            ));

        Instant now = clock.instant();
        requireDailyQuota(teacher.getId(), now);

        // AI 계약은 from <= to <= data_as_of를 요구하고 data_as_of는 서버 시각(now)입니다.
        // 화면은 브라우저 시각으로 to를 만들어 보내므로 두 시각이 밀리초 단위로 엇갈리면
        // to가 now보다 뒤여서 요청이 거절됩니다. 서버 시각으로 잘라 이 경합을 없앱니다.
        Instant periodTo = command.periodTo().isAfter(now) ? now : command.periodTo();
        Instant periodFrom = command.periodFrom().isAfter(periodTo)
            ? periodTo
            : command.periodFrom();
        Set<BalancingContract.MetricCode> metricCodes = command.metricCodes();
        Duration window = Duration.between(periodFrom, periodTo);
        EconomicMetricSnapshot current = aggregationService.aggregate(
            classroom.getId(),
            periodFrom,
            periodTo,
            now,
            metricCodes
        );
        EconomicMetricSnapshot previous = aggregationService.aggregate(
            classroom.getId(),
            periodFrom.minus(window),
            periodFrom,
            now,
            metricCodes
        );
        EconomicMetricEvaluation evaluation = evaluator.evaluate(
            periodFrom,
            periodTo,
            now,
            current,
            previous,
            metricCodes
        );

        List<TeacherChatContract.Fact> facts = facts(classroom, evaluation);
        UUID aiRunId = uuidGenerator.generate();
        TeacherChatContract.Request request = new TeacherChatContract.Request(
            aiRunId,
            command.requestId(),
            command.correlationId(),
            TeacherChatContract.SCHEMA_VERSION,
            TeacherChatContract.PROMPT_VERSION,
            1,
            now.plus(CHAT_TIMEOUT),
            TeacherChatContract.LOCALE,
            classroom.getGrade(),
            new TeacherChatContract.Payload(
                command.question(),
                new TeacherChatContract.Period(periodFrom, periodTo, now),
                facts,
                policyChanges(command, now)
            )
        );

        AiRun draft = AiRun.generating(
            aiRunId,
            classroom.getId(),
            null,
            teacher.getId(),
            RUN_TYPE,
            "TEACHER_REQUEST",
            TeacherChatContract.PROMPT_VERSION,
            Map.of("fact_count", facts.size(), "question_length", command.question().length()),
            now
        );
        draft.setAnalysisPeriod(periodFrom, periodTo);
        // AiRun의 @Version이 primitive라 save()가 merge로 동작한다.
        // 이후 상태 변경이 반영되도록 반환된 관리 인스턴스를 사용한다.
        AiRun run = runRepository.save(draft);

        boolean fallbackUsed = false;
        String fallbackCode = null;
        TeacherChatContract.Response response;
        if (!aiSettings.enabled(classroom.getId())) {
            fallbackUsed = true;
            fallbackCode = "AI_DISABLED_FOR_CLASSROOM";
            response = fallbackFactory.disabled(request);
        } else {
            try {
                response = aiClient.answer(request);
            } catch (AiServiceCallException exception) {
                fallbackUsed = true;
                fallbackCode = exception.getCode();
                // 교사에게는 대체 답변만 보이므로, 원인은 로그로 남겨야 추적할 수 있습니다.
                // AI_HTTP_400은 요청 계약 위반, AI_HTTP_422는 모델 답변이 응답 계약을 어긴 경우입니다.
                log.warn(
                    "Teacher chat fell back to the deterministic answer. ai_run_id={} code={} message={}",
                    aiRunId,
                    fallbackCode,
                    exception.getMessage()
                );
                response = fallbackFactory.unavailable(request, fallbackCode);
            }
        }

        Instant completedAt = clock.instant();
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("fact_count", facts.size());
        validation.put("fallback_used", fallbackUsed);
        validation.put("fallback_code", fallbackCode);
        validation.put("response_mode", response.output().responseMode().name());
        validation.put("limitation_codes", response.output().limitationCodes().stream()
            .map(Enum::name)
            .toList());
        validation.put("validation_codes", response.validationCodes());
        run.completeWithModel(
            response.provider(),
            response.modelId(),
            validation,
            completedAt
        );
        artifactRepository.save(AiArtifact.create(
            uuidGenerator.generate(),
            classroom.getId(),
            aiRunId,
            "TEACHER_CHAT_ANSWER",
            toMap(response),
            response.schemaVersion(),
            response.safetyStatus(),
            completedAt
        ));

        return result(aiRunId, request, response, facts, fallbackUsed, fallbackCode);
    }

    /**
     * 계약상 evidence는 최소 1건이 필요하다. AI가 근거로 인용한 사실이 있으면 그것만,
     * 제한 답변처럼 인용이 없으면 입력으로 넘긴 사실 전체를 반환한다.
     */
    private TeacherChatResult result(
        UUID aiRunId,
        TeacherChatContract.Request request,
        TeacherChatContract.Response response,
        List<TeacherChatContract.Fact> facts,
        boolean fallbackUsed,
        String fallbackCode
    ) {
        TeacherChatContract.Output output = response.output();
        Set<String> citedIds = Set.copyOf(output.evidenceFactIds());
        List<TeacherChatContract.Fact> cited = citedIds.isEmpty()
            ? facts
            : facts.stream().filter(fact -> citedIds.contains(fact.factId())).toList();
        List<TeacherChatContract.Fact> evidenceSource = cited.isEmpty() ? facts : cited;
        List<TeacherChatResult.Evidence> evidence = evidenceSource.stream()
            .map(fact -> new TeacherChatResult.Evidence(
                fact.factId(),
                fact.label(),
                fact.value(),
                fact.unit().name()
            ))
            .toList();
        List<TeacherChatResult.Recommendation> recommendations = output.recommendations().stream()
            .map(item -> new TeacherChatResult.Recommendation(item.text(), item.sideEffects()))
            .toList();
        return new TeacherChatResult(
            aiRunId,
            output.answer(),
            request.payload().period().from(),
            request.payload().period().to(),
            output.dataAsOf(),
            evidence,
            recommendations,
            fallbackUsed,
            fallbackCode
        );
    }

    private void requireDailyQuota(UUID teacherId, Instant now) {
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        long used = runRepository.countByRequestedByUserIdAndRunTypeAndRequestedAtGreaterThanEqual(
            teacherId,
            RUN_TYPE,
            dayStart
        );
        if (used >= DAILY_LIMIT) {
            throw operation(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI_CHAT_DAILY_LIMIT_EXCEEDED",
                "The daily teacher chat limit was reached."
            );
        }
    }

    /**
     * 집계된 학급 사실만 만든다. 학생 실명·개인 잔액·개인 대화는 포함하지 않는다.
     */
    private List<TeacherChatContract.Fact> facts(
        Classroom classroom,
        EconomicMetricEvaluation evaluation
    ) {
        List<TeacherChatContract.Fact> facts = new ArrayList<>();
        facts.add(new TeacherChatContract.Fact(
            "CLASSROOM_CURRENCY",
            "학급 화폐 단위",
            classroom.getCurrencyName(),
            TeacherChatContract.FactUnit.TEXT
        ));
        facts.add(new TeacherChatContract.Fact(
            "CLASSROOM_ACTIVE_STUDENTS",
            "분석 구간 활동 학생 수",
            (long) evaluation.sample().activeStudents(),
            TeacherChatContract.FactUnit.COUNT
        ));
        facts.add(new TeacherChatContract.Fact(
            "CLASSROOM_RELATED_TRANSACTIONS",
            "분석 구간 관련 거래 수",
            (long) evaluation.sample().relatedTransactions(),
            TeacherChatContract.FactUnit.COUNT
        ));
        List<ClassroomModule> activeModules = moduleRepository
            .findAllByClassroomIdAndStatusOrderByModuleCode(classroom.getId(), "ACTIVE");
        facts.add(new TeacherChatContract.Fact(
            "CLASSROOM_ACTIVE_MODULES",
            "활성 모듈",
            activeModules.isEmpty()
                ? "없음"
                : String.join(
                    ", ",
                    activeModules.stream().map(ClassroomModule::getModuleCode).toList()
                ),
            TeacherChatContract.FactUnit.TEXT
        ));
        for (BalancingContract.Metric metric : evaluation.metrics()) {
            if (metric.availability() != BalancingContract.Availability.AVAILABLE
                || metric.value() == null) {
                continue;
            }
            facts.add(new TeacherChatContract.Fact(
                "METRIC_" + metric.code().name(),
                metric.subjectLabel() == null ? metric.code().name() : metric.subjectLabel(),
                metric.value(),
                factUnit(metric.unit())
            ));
        }
        return facts;
    }

    private List<TeacherChatContract.PolicyChange> policyChanges(
        TeacherChatCommand command,
        Instant dataAsOf
    ) {
        if (!command.includePolicyHistory()) {
            return List.of();
        }
        Instant to = command.periodTo().isBefore(dataAsOf) ? command.periodTo() : dataAsOf;
        List<ClassroomPolicyVersion> policies = policyRepository
            .findAllByClassroomIdAndEffectiveFromBetweenOrderByEffectiveFromAsc(
                command.classroomId(),
                command.periodFrom(),
                to
            );
        int firstIncluded = Math.max(0, policies.size() - 20);
        return policies.subList(firstIncluded, policies.size())
            .stream()
            .map(TeacherChatService::policyChange)
            .toList();
    }

    private static TeacherChatContract.PolicyChange policyChange(ClassroomPolicyVersion policy) {
        String area = policy.getPolicyArea();
        String safeArea = area.replaceAll("[^A-Z0-9_]", "_");
        return new TeacherChatContract.PolicyChange(
            "POLICY_CHANGE_" + safeArea + "_V" + policy.getVersion(),
            policy.getEffectiveFrom(),
            area + " 정책이 " + policy.getVersion() + "버전으로 변경되었습니다."
        );
    }

    private static TeacherChatContract.FactUnit factUnit(BalancingContract.ValueUnit unit) {
        if (unit == null) {
            return TeacherChatContract.FactUnit.TEXT;
        }
        return switch (unit) {
            case AMOUNT -> TeacherChatContract.FactUnit.AMOUNT;
            case COUNT -> TeacherChatContract.FactUnit.COUNT;
            case RATE_BPS -> TeacherChatContract.FactUnit.RATE_BPS;
        };
    }

    private void validate(TeacherChatCommand command) {
        if (command.teacherSubject() == null || command.teacherSubject().isBlank()) {
            throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "A teacher session is required.");
        }
        if (command.question() == null || command.question().isBlank()) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "question must not be blank.");
        }
        if (command.question().length() > 1000) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "question must be 1000 characters or fewer."
            );
        }
        if (command.periodFrom() == null
            || command.periodTo() == null
            || !command.periodFrom().isBefore(command.periodTo())) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "period.from must be before period.to."
            );
        }
        if (command.metricCodes().isEmpty()) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "metric_scopes must not be empty."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
