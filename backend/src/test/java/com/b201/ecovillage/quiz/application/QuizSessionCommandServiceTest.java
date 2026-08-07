package com.b201.ecovillage.quiz.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.classroom.application.EffectiveAccessModeService;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizOption;
import com.b201.ecovillage.quiz.domain.QuizQuestionType;
import com.b201.ecovillage.quiz.domain.QuizQuestionVersion;
import com.b201.ecovillage.quiz.domain.QuizSession;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.quiz.repository.QuizOptionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionVersionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
import com.b201.ecovillage.quiz.repository.QuizSubmissionRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

class QuizSessionCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void opensSessionWithPublishedQuestionSnapshotsAndParticipants() {
        ClassroomAuthorizationService authorizationService =
            mock(ClassroomAuthorizationService.class);
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
        ClassroomStudentRepository studentRepository =
            mock(ClassroomStudentRepository.class);
        ActivitySessionRepository activityRepository =
            mock(ActivitySessionRepository.class);
        ActivityParticipantRepository participantRepository =
            mock(ActivityParticipantRepository.class);
        QuizSessionRepository sessionRepository = mock(QuizSessionRepository.class);
        QuizSessionQuestionRepository sessionQuestionRepository =
            mock(QuizSessionQuestionRepository.class);
        QuizSubmissionRepository submissionRepository = mock(QuizSubmissionRepository.class);
        QuizQuestionVersionRepository versionRepository =
            mock(QuizQuestionVersionRepository.class);
        QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
        IdempotencyRecordRepository idempotencyRepository =
            mock(IdempotencyRecordRepository.class);
        ClassroomModuleRepository moduleRepository = mock(ClassroomModuleRepository.class);
        EffectiveAccessModeService accessModeService = mock(EffectiveAccessModeService.class);
        RealtimeEventPublisher eventPublisher = mock(RealtimeEventPublisher.class);
        EntityManager entityManager = mock(EntityManager.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator uuidGenerator = new UuidV7Generator(clock, new SecureRandom());
        QuizSessionCommandService service = new QuizSessionCommandService(
            authorizationService,
            userRepository,
            classroomRepository,
            studentRepository,
            activityRepository,
            participantRepository,
            sessionRepository,
            sessionQuestionRepository,
            submissionRepository,
            versionRepository,
            optionRepository,
            idempotencyRepository,
            moduleRepository,
            accessModeService,
            eventPublisher,
            uuidGenerator,
            entityManager,
            clock
        );

        UUID classroomId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        QuizSessionCommand command = new QuizSessionCommand(
            "teacher-subject",
            classroomId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            0L,
            sessionId,
            QuizSessionCommandType.START,
            List.of(versionId),
            List.of(studentId),
            QuizMode.GAME,
            QuizContentType.ECONOMY,
            Map.of("base_reward", 100),
            true
        );
        QuizQuestionVersion version = QuizQuestionVersion.teacherDraft(
            versionId,
            classroomId,
            questionId,
            1,
            QuizContentType.ECONOMY,
            QuizQuestionType.MULTIPLE_CHOICE,
            "What is saving?",
            null,
            NOW.minusSeconds(100)
        );
        version.approve();
        version.publish(NOW.minusSeconds(90));
        QuizOption wrong = QuizOption.create(
            UUID.randomUUID(),
            classroomId,
            versionId,
            "1",
            "Spend everything",
            false,
            0,
            NOW
        );
        QuizOption correct = QuizOption.create(
            UUID.randomUUID(),
            classroomId,
            versionId,
            "2",
            "Keep money for later",
            true,
            1,
            NOW
        );
        ClassroomStudent student = ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "student",
            NOW.minusSeconds(100)
        );

        when(userRepository.findByExternalSubject("teacher-subject"))
            .thenReturn(Optional.of(UserAccount.createTeacher(
                teacherId,
                "teacher-subject",
                "teacher",
                NOW.minusSeconds(100)
            )));
        when(classroomRepository.findForUpdateById(classroomId))
            .thenReturn(Optional.of(Classroom.active(classroomId, "ABCD1234", NOW)));
        when(activityRepository.findFirstByClassroomIdAndStatus(classroomId, "LIVE"))
            .thenReturn(Optional.empty());
        when(sessionRepository.existsByClassroomIdAndStatusIn(any(), any()))
            .thenReturn(false);
        when(
            idempotencyRepository.findForReplay(
                classroomId,
                teacherId,
                "/classrooms/{classroom_id}/quiz-sessions/{resource_id}/commands",
                command.requestId().toString()
            )
        ).thenReturn(Optional.empty());
        when(sessionRepository.existsById(sessionId)).thenReturn(false);
        // 학생 앱이 세션을 찾으려면 퀴즈 모듈이 활성이어야 하고, 세션 id가 config에 기록된다.
        ClassroomModule quizModule = ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "QUIZ_GAME",
            NOW.minusSeconds(60)
        );
        quizModule.activate(java.util.Map.of(), NOW.minusSeconds(30));
        when(
            moduleRepository.findForUpdateByClassroomIdAndModuleCode(classroomId, "QUIZ_GAME")
        ).thenReturn(Optional.of(quizModule));
        when(versionRepository.findAllByClassroomIdAndIdIn(classroomId, List.of(versionId)))
            .thenReturn(List.of(version));
        when(
            studentRepository.findAllForUpdate(
                classroomId,
                List.of(studentId),
                ClassroomStudentStatus.ACTIVE
            )
        ).thenReturn(List.of(student));
        when(
            optionRepository
                .findAllByClassroomIdAndQuizQuestionVersionIdOrderBySortOrder(
                    classroomId,
                    versionId
                )
        ).thenReturn(List.of(wrong, correct));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QuizResourceResult result = service.execute(command);

        assertEquals("OPEN", result.status());
        verify(activityRepository).save(any());
        verify(participantRepository).saveAll(any());
        verify(sessionQuestionRepository).save(any());
        // 학생 앱이 읽는 키에 세션 id가 기록되어야 한다.
        assertEquals(
            sessionId.toString(),
            quizModule.getConfig().get("active_quiz_session_id")
        );
        verify(moduleRepository).save(quizModule);
    }
}
