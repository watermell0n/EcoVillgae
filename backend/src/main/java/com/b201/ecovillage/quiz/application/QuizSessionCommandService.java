package com.b201.ecovillage.quiz.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.application.EffectiveAccessModeService;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.domain.ActivityParticipant;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizOption;
import com.b201.ecovillage.quiz.domain.QuizQuestionStatus;
import com.b201.ecovillage.quiz.domain.QuizQuestionVersion;
import com.b201.ecovillage.quiz.domain.QuizSession;
import com.b201.ecovillage.quiz.domain.QuizSessionQuestion;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.quiz.repository.QuizOptionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionVersionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
import com.b201.ecovillage.quiz.repository.QuizSubmissionRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

@Service
public class QuizSessionCommandService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/quiz-sessions/{resource_id}/commands";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final ActivitySessionRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final QuizSessionRepository sessionRepository;
    private final QuizSessionQuestionRepository sessionQuestionRepository;
    private final QuizSubmissionRepository submissionRepository;
    private final QuizQuestionVersionRepository versionRepository;
    private final QuizOptionRepository optionRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final EffectiveAccessModeService accessModeService;
    private final RealtimeEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;
    private final EntityManager entityManager;
    private final Clock clock;

    public QuizSessionCommandService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository,
        ActivitySessionRepository activityRepository,
        ActivityParticipantRepository participantRepository,
        QuizSessionRepository sessionRepository,
        QuizSessionQuestionRepository sessionQuestionRepository,
        QuizSubmissionRepository submissionRepository,
        QuizQuestionVersionRepository versionRepository,
        QuizOptionRepository optionRepository,
        IdempotencyRecordRepository idempotencyRepository,
        ClassroomModuleRepository moduleRepository,
        EffectiveAccessModeService accessModeService,
        RealtimeEventPublisher eventPublisher,
        UuidV7Generator uuidGenerator,
        EntityManager entityManager,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.sessionQuestionRepository = sessionQuestionRepository;
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.optionRepository = optionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.moduleRepository = moduleRepository;
        this.accessModeService = accessModeService;
        this.eventPublisher = eventPublisher;
        this.uuidGenerator = uuidGenerator;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public QuizResourceResult execute(QuizSessionCommand command) {
        validateBase(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_QUIZ_SESSIONS,
            null
        );
        UserAccount teacher = userRepository
            .findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "A quiz can start only in an active classroom."
            );
        }
        String requestHash = hash(command);
        QuizResourceResult replay = replay(command, teacher.getId(), requestHash);
        if (replay != null) {
            return replay;
        }

        Instant now = clock.instant();
        QuizSession session = switch (command.command()) {
            case START -> start(command, teacher.getId(), classroom, now);
            case UPDATE_REWARD -> updateReward(command, now);
            case CLOSE_SUBMISSIONS -> close(command, now);
        };
        sessionRepository.flush();
        QuizResourceResult result = new QuizResourceResult(
            session.getId(),
            session.getStatus().name(),
            session.getStateVersion()
        );
        remember(command, teacher.getId(), requestHash, result, now);
        publishRealtime(command, session, now);
        return result;
    }

    private void publishRealtime(
        QuizSessionCommand command,
        QuizSession session,
        Instant now
    ) {
        if (command.command() == QuizSessionCommandType.START) {
            eventPublisher.publish(
                command.classroomId(),
                "QUIZ",
                command.sessionId(),
                "quiz.session.started",
                "QUIZ",
                command.sessionId(),
                session.getStateVersion(),
                Map.of(
                    "mode", session.getMode().name(),
                    "content_type", command.contentType().name(),
                    "question_count", command.questionVersionIds().size(),
                    "reward_policy_public", session.getRewardPolicyPublic()
                ),
                true
            );
            eventPublisher.publish(
                command.classroomId(),
                "CLASSROOM_LIFECYCLE",
                null,
                "classroom.app_mode.changed",
                "QUIZ",
                command.sessionId(),
                session.getStateVersion(),
                Map.of(
                    "mode", "GUIDED",
                    "source", "GUIDED_ACTIVITY",
                    "starts_at", now.toString(),
                    "ends_at", now.toString(),
                    "active_activity_id", command.sessionId().toString(),
                    "activity_type", "QUIZ"
                ),
                true
            );
            return;
        }
        if (command.command() == QuizSessionCommandType.CLOSE_SUBMISSIONS) {
            long submitted = submissionRepository
                .findAllByClassroomIdAndQuizSessionId(
                    command.classroomId(),
                    command.sessionId()
                )
                .stream()
                .filter(submission -> "SUBMITTED".equals(submission.getStatus()))
                .map(submission -> submission.getClassroomStudentId())
                .distinct()
                .count();
            long total = participantRepository.countByClassroomIdAndActivitySessionId(
                command.classroomId(),
                command.sessionId()
            );
            eventPublisher.publish(
                command.classroomId(),
                "QUIZ",
                command.sessionId(),
                "quiz.submissions.closed",
                "QUIZ",
                command.sessionId(),
                session.getStateVersion(),
                Map.of(
                    "closed_at", now.toString(),
                    "submitted_count", submitted,
                    "total_count", total
                ),
                true
            );
            String nextMode = accessModeService.get(command.classroomId()).mode().name();
            eventPublisher.publish(
                command.classroomId(),
                "CLASSROOM_LIFECYCLE",
                null,
                "classroom.app_mode.changed",
                "QUIZ",
                command.sessionId(),
                session.getStateVersion(),
                Map.of(
                    "mode", nextMode,
                    "source", "GUIDED_ACTIVITY",
                    "starts_at", now.toString(),
                    "ends_at", now.toString()
                ),
                true
            );
        }
    }

    private QuizSession start(
        QuizSessionCommand command,
        UUID teacherId,
        Classroom classroom,
        Instant now
    ) {
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != classroom.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state has changed."
            );
        }
        if (sessionRepository.existsById(command.sessionId())) {
            throw operation(
                HttpStatus.CONFLICT,
                "QUIZ_SESSION_ALREADY_EXISTS",
                "The quiz session already exists."
            );
        }
        validateStart(command);
        // 학생 앱은 활성 퀴즈 모듈의 config에서 세션 id를 찾는다.
        // 모듈이 꺼져 있으면 세션을 만들어도 학생 화면에 열리지 않으므로 먼저 막는다.
        ClassroomModule quizModule = requireActiveQuizModule(
            command.classroomId(),
            command.mode()
        );
        List<QuizQuestionVersion> versions = versionRepository
            .findAllByClassroomIdAndIdIn(
                command.classroomId(),
                command.questionVersionIds()
            );
        if (versions.size() != command.questionVersionIds().size()) {
            throw operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "One or more quiz question versions could not be found."
            );
        }
        Map<UUID, QuizQuestionVersion> byId = new HashMap<>();
        for (QuizQuestionVersion version : versions) {
            if (
                version.getStatus() != QuizQuestionStatus.PUBLISHED
                    || version.getContentType() != command.contentType()
            ) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "QUESTION_NOT_PUBLISHED",
                    "Every question must be published and match the session content type."
                );
            }
            byId.put(version.getId(), version);
        }
        List<ClassroomStudent> participants = studentRepository.findAllForUpdate(
            command.classroomId(),
            command.participantIds(),
            ClassroomStudentStatus.ACTIVE
        );
        if (participants.size() != command.participantIds().size()) {
            throw operation(
                HttpStatus.NOT_FOUND,
                "PARTICIPANT_NOT_FOUND",
                "Every participant must be an active student in the classroom."
            );
        }

        activityRepository.save(
            ActivitySession.liveQuiz(
                command.sessionId(),
                command.classroomId(),
                teacherId,
                command.mode() == QuizMode.GAME && command.leaderboardPublic(),
                now
            )
        );
        QuizSession session = sessionRepository.save(
            QuizSession.open(
                command.sessionId(),
                command.classroomId(),
                command.mode(),
                command.contentType(),
                command.rewardPolicyPublic(),
                command.leaderboardPublic(),
                now
            )
        );
        participantRepository.saveAll(
            participants.stream()
                .map(student -> ActivityParticipant.joined(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    command.sessionId(),
                    student.getId(),
                    now
                ))
                .toList()
        );
        int sequence = 1;
        for (UUID versionId : command.questionVersionIds()) {
            QuizQuestionVersion version = byId.get(versionId);
            List<QuizOption> options = optionRepository
                .findAllByClassroomIdAndQuizQuestionVersionIdOrderBySortOrder(
                    command.classroomId(),
                    versionId
                );
            QuizOption correct = options.stream()
                .filter(QuizOption::isCorrect)
                .findFirst()
                .orElseThrow(() -> operation(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "AMBIGUOUS_ANSWER",
                    "A quiz question has no correct option."
                ));
            if (options.stream().filter(QuizOption::isCorrect).count() != 1) {
                throw operation(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "AMBIGUOUS_ANSWER",
                    "A quiz question must have exactly one correct option."
                );
            }
            List<Map<String, Object>> optionSnapshot = options.stream()
                .sorted(Comparator.comparingInt(QuizOption::getSortOrder))
                .map(option -> Map.<String, Object>of(
                    "option_id", option.getId().toString(),
                    "option_key", option.getOptionKey(),
                    "option_text", option.getOptionText(),
                    "sort_order", option.getSortOrder()
                ))
                .toList();
            sessionQuestionRepository.save(
                QuizSessionQuestion.snapshot(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    command.sessionId(),
                    versionId,
                    sequence++,
                    version.getPrompt(),
                    optionSnapshot,
                    correct.getOptionKey(),
                    100,
                    command.mode() == QuizMode.GAME ? 30 : null,
                    now
                )
            );
        }
        quizModule.trackActiveQuizSession(command.sessionId(), now);
        moduleRepository.save(quizModule);
        // 학생 앱은 활성 모듈 중 먼저 찾은 세션 하나만 읽습니다. 게임형과 평가형이 각각 세션을
        // 들고 있으면 새로 연 퀴즈 대신 예전 퀴즈가 계속 보이므로, 다른 쪽 표시는 지웁니다.
        clearActiveQuizSession(
            command.classroomId(),
            command.mode() == QuizMode.GAME ? QuizMode.TEST : QuizMode.GAME,
            now
        );
        return session;
    }

    /**
     * GAME은 QUIZ_GAME, TEST는 QUIZ_TEST 모듈이 활성이어야 학생 화면에 퀴즈가 열린다.
     */
    private ClassroomModule requireActiveQuizModule(UUID classroomId, QuizMode mode) {
        String moduleCode = mode == QuizMode.GAME ? "QUIZ_GAME" : "QUIZ_TEST";
        ClassroomModule module = moduleRepository
            .findForUpdateByClassroomIdAndModuleCode(classroomId, moduleCode)
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "MODULE_NOT_ACTIVE",
                moduleCode + " must be activated before a quiz can be opened."
            ));
        if (!"ACTIVE".equals(module.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "MODULE_NOT_ACTIVE",
                moduleCode + " must be activated before a quiz can be opened."
            );
        }
        return module;
    }

    /**
     * 제출을 마감하면 학생 화면에서 퀴즈가 내려가야 하므로 활성 세션 표시를 지운다.
     */
    private void clearActiveQuizSession(UUID classroomId, QuizMode mode, Instant now) {
        String moduleCode = mode == QuizMode.GAME ? "QUIZ_GAME" : "QUIZ_TEST";
        moduleRepository
            .findForUpdateByClassroomIdAndModuleCode(classroomId, moduleCode)
            .filter(module -> "ACTIVE".equals(module.getStatus()))
            .ifPresent(module -> {
                module.clearActiveQuizSession(now);
                moduleRepository.save(module);
            });
    }

    private QuizSession close(QuizSessionCommand command, Instant now) {
        QuizSession session = sessionRepository
            .findForUpdate(command.classroomId(), command.sessionId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz session could not be found."
            ));
        if (
            command.expectedStateVersion() == null
                || command.expectedStateVersion() != session.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The quiz session state has changed."
            );
        }
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(command.classroomId(), command.sessionId())
            .orElseThrow();
        try {
            session.closeSubmissions(now);
            activity.beginFinalizing(now);
        } catch (IllegalStateException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                exception.getMessage()
            );
        }
        clearActiveQuizSession(command.classroomId(), session.getMode(), now);
        return session;
    }

    private QuizSession updateReward(QuizSessionCommand command, Instant now) {
        QuizSession session = sessionRepository
            .findForUpdate(command.classroomId(), command.sessionId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz session could not be found."
            ));
        if (
            command.expectedStateVersion() == null
                || command.expectedStateVersion() != session.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The quiz session state has changed."
            );
        }
        if (session.getMode() == QuizMode.TEST) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "FORBIDDEN_REWARD_CONTEXT",
                "A TEST quiz cannot receive a monetary reward."
            );
        }
        if (command.rewardPolicyPublic() == null) {
            throw validation("UPDATE_REWARD requires reward_policy_public.");
        }
        Object reward = command.rewardPolicyPublic().get("reward_per_person");
        if (!(reward instanceof Number number) || number.longValue() < 0L) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_REWARD_POLICY",
                "reward_per_person must be a non-negative amount."
            );
        }
        entityManager.detach(session);
        int updated = sessionRepository.updateRewardPolicy(
            command.classroomId(),
            command.sessionId(),
            command.expectedStateVersion(),
            command.rewardPolicyPublic(),
            now
        );
        if (updated != 1) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The quiz session state has changed."
            );
        }
        return sessionRepository
            .findByClassroomIdAndId(command.classroomId(), command.sessionId())
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The updated quiz session could not be reloaded."
            ));
    }

    private static void validateBase(QuizSessionCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.sessionId() == null
                || command.command() == null
        ) {
            throw validation("Check the quiz session command values.");
        }
    }

    private static void validateStart(QuizSessionCommand command) {
        if (
            command.questionVersionIds() == null
                || command.questionVersionIds().isEmpty()
                || command.participantIds() == null
                || command.participantIds().isEmpty()
                || command.mode() == null
                || command.contentType() == null
                || command.rewardPolicyPublic() == null
                || new HashSet<>(command.questionVersionIds()).size()
                    != command.questionVersionIds().size()
                || new HashSet<>(command.participantIds()).size()
                    != command.participantIds().size()
        ) {
            throw validation("Check the quiz session start values.");
        }
        if (command.mode() == QuizMode.TEST && command.leaderboardPublic()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PUBLIC_DATA_ONLY",
                "A test session cannot publish a leaderboard."
            );
        }
    }

    private QuizResourceResult replay(
        QuizSessionCommand command,
        UUID actorId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(
                command.classroomId(),
                actorId,
                ROUTE,
                command.requestId().toString()
            )
            .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        QuizSession session = sessionRepository
            .findByClassroomIdAndId(command.classroomId(), command.sessionId())
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous quiz session result could not be found."
            ));
        return new QuizResourceResult(
            session.getId(),
            session.getStatus().name(),
            session.getStateVersion()
        );
    }

    private void remember(
        QuizSessionCommand command,
        UUID actorId,
        String requestHash,
        QuizResourceResult result,
        Instant now
    ) {
        idempotencyRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                command.classroomId(),
                actorId,
                ROUTE,
                command.requestId().toString(),
                requestHash,
                HttpStatus.OK.value(),
                Map.of(
                    "quiz_resource_id", result.quizResourceId(),
                    "status", result.status(),
                    "state_version", result.stateVersion()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
    }

    private static String hash(QuizSessionCommand command) {
        return sha256(
            command.classroomId() + "|" + command.sessionId() + "|" + command.command()
                + "|" + command.questionVersionIds() + "|" + command.participantIds()
                + "|" + command.mode() + "|" + command.contentType() + "|"
                + command.rewardPolicyPublic() + "|" + command.leaderboardPublic()
                + "|" + command.expectedStateVersion()
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the quiz session command.", exception);
        }
    }

    private static ClassroomOperationException validation(String message) {
        return operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
