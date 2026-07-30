package com.b201.ecovillage.quiz.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizOption;
import com.b201.ecovillage.quiz.domain.QuizQuestion;
import com.b201.ecovillage.quiz.domain.QuizQuestionType;
import com.b201.ecovillage.quiz.domain.QuizQuestionVersion;
import com.b201.ecovillage.quiz.repository.QuizOptionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionVersionRepository;

class QuizQuestionCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    private QuizQuestionCommandService service;
    private QuizQuestionCommand command;
    private QuizQuestionRepository questionRepository;
    private QuizQuestionVersionRepository versionRepository;
    private QuizOptionRepository optionRepository;
    private AtomicReference<QuizQuestion> savedQuestion;
    private AtomicReference<QuizQuestionVersion> savedVersion;

    @BeforeEach
    void setUp() {
        ClassroomAuthorizationService authorizationService =
            mock(ClassroomAuthorizationService.class);
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
        questionRepository = mock(QuizQuestionRepository.class);
        versionRepository = mock(QuizQuestionVersionRepository.class);
        optionRepository = mock(QuizOptionRepository.class);
        IdempotencyRecordRepository idempotencyRepository =
            mock(IdempotencyRecordRepository.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator uuidGenerator = new UuidV7Generator(clock, new SecureRandom());
        service = new QuizQuestionCommandService(
            authorizationService,
            userRepository,
            classroomRepository,
            questionRepository,
            versionRepository,
            optionRepository,
            idempotencyRepository,
            uuidGenerator,
            clock
        );

        UUID classroomId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        command = new QuizQuestionCommand(
            "teacher-subject",
            classroomId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            0L,
            QuizQuestionCommandType.CREATE,
            null,
            QuizContentType.ECONOMY,
            QuizQuestionType.MULTIPLE_CHOICE,
            "Which choice is saving?",
            List.of(
                new QuizOptionCommand("1", "Spend it all", false, 0),
                new QuizOptionCommand("2", "Keep some for later", true, 1)
            ),
            "Saving reserves money for a future goal.",
            null
        );
        when(userRepository.findByExternalSubject("teacher-subject"))
            .thenReturn(Optional.of(UserAccount.createTeacher(
                teacherId,
                "teacher-subject",
                "teacher",
                NOW.minusSeconds(100)
            )));
        when(classroomRepository.findById(classroomId))
            .thenReturn(Optional.of(Classroom.active(classroomId, "ABCD1234", NOW)));
        when(
            idempotencyRepository.findForReplay(
                classroomId,
                teacherId,
                "/classrooms/{classroom_id}/quiz-question-commands",
                command.requestId().toString()
            )
        ).thenReturn(Optional.empty());
        savedQuestion = new AtomicReference<>();
        when(questionRepository.save(any())).thenAnswer(invocation -> {
            QuizQuestion question = invocation.getArgument(0);
            savedQuestion.set(question);
            return question;
        });
        savedVersion = new AtomicReference<>();
        when(versionRepository.save(any())).thenAnswer(invocation -> {
            QuizQuestionVersion version = invocation.getArgument(0);
            savedVersion.set(version);
            return version;
        });
    }

    @Test
    void createsDraftVersionAndOptions() {
        QuizResourceResult result = service.edit(command);

        assertEquals("DRAFT", result.status());
        assertEquals(1, savedVersion.get().getVersion());
        verify(optionRepository).saveAll(any());
    }

    @Test
    void rejectsAmbiguousCorrectAnswersBeforePersistence() {
        QuizQuestionCommand ambiguous = new QuizQuestionCommand(
            command.teacherSubject(),
            command.classroomId(),
            UUID.randomUUID(),
            command.correlationId(),
            command.expectedStateVersion(),
            command.command(),
            null,
            command.contentType(),
            command.questionType(),
            command.prompt(),
            List.of(
                new QuizOptionCommand("1", "first", true, 0),
                new QuizOptionCommand("2", "second", true, 1)
            ),
            command.explanation(),
            null
        );

        assertThrows(ClassroomOperationException.class, () -> service.edit(ambiguous));
    }
}
