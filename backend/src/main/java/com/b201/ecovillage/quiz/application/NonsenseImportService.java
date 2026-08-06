package com.b201.ecovillage.quiz.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizQuestionType;
import com.b201.ecovillage.quiz.repository.QuizQuestionVersionRepository;

/**
 * 넌센스 문제은행에서 학급으로 문항을 가져온다.
 *
 * <p>AI를 부르지 않으므로 학급 AI 설정과 일일 한도를 보지 않는다. 저장은 기존 문제은행 명령
 * 서비스를 그대로 호출해 상태 전이와 버전 관리를 퀴즈 도메인이 계속 소유하게 한다.
 */
@Service
public class NonsenseImportService {

    static final int MAX_QUESTION_COUNT = 30;

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final NonsenseQuestionBank bank;
    private final QuizQuestionCommandService commandService;
    private final QuizQuestionVersionRepository questionVersionRepository;
    private final UuidV7Generator uuidGenerator;

    public NonsenseImportService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        NonsenseQuestionBank bank,
        QuizQuestionCommandService commandService,
        QuizQuestionVersionRepository questionVersionRepository,
        UuidV7Generator uuidGenerator
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.bank = bank;
        this.commandService = commandService;
        this.questionVersionRepository = questionVersionRepository;
        this.uuidGenerator = uuidGenerator;
    }

    @Transactional
    public NonsenseImportResult importQuestions(NonsenseImportCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_QUIZ_QUESTIONS,
            null
        );
        if (!classroomRepository.existsById(command.classroomId())) {
            throw operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom was not found."
            );
        }

        Set<String> used = new HashSet<>(
            questionVersionRepository.findPromptsByClassroomIdAndContentType(
                command.classroomId(),
                QuizContentType.NONSENSE
            )
        );
        List<NonsenseBankQuestion> drawn = bank.draw(
            command.grade(),
            command.questionCount(),
            used
        );
        if (drawn.isEmpty()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "NONSENSE_BANK_EXHAUSTED",
                "There is no nonsense question left to import for this grade."
            );
        }

        List<UUID> questionIds = new ArrayList<>();
        for (NonsenseBankQuestion question : drawn) {
            questionIds.add(persist(command, question));
            used.add(question.question());
        }
        return new NonsenseImportResult(
            questionIds,
            bank.availableCount(command.grade(), used),
            bank.bankVersion()
        );
    }

    private UUID persist(NonsenseImportCommand command, NonsenseBankQuestion question) {
        List<QuizOptionCommand> options = new ArrayList<>();
        int sortOrder = 1;
        for (NonsenseBankQuestion.Choice choice : question.choices()) {
            options.add(new QuizOptionCommand(
                choice.key(),
                choice.text(),
                choice.key().equals(question.correctChoiceKey()),
                sortOrder++
            ));
        }
        QuizResourceResult created = commandService.edit(new QuizQuestionCommand(
            command.teacherSubject(),
            command.classroomId(),
            uuidGenerator.generate(),
            command.correlationId(),
            null,
            QuizQuestionCommandType.CREATE,
            null,
            QuizContentType.NONSENSE,
            // 은행은 객관식만 담는다. OX는 말장난이 성립하지 않아 넣지 않았다.
            QuizQuestionType.MULTIPLE_CHOICE,
            question.question(),
            options,
            question.explanation(),
            null
        ));
        return created.quizResourceId();
    }

    private void validate(NonsenseImportCommand command) {
        if (command.teacherSubject() == null || command.teacherSubject().isBlank()) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A teacher session is required."
            );
        }
        if (command.grade() < 4 || command.grade() > 6) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "grade must be 4, 5, or 6.");
        }
        if (command.questionCount() < 1 || command.questionCount() > MAX_QUESTION_COUNT) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "question_count must be between 1 and " + MAX_QUESTION_COUNT + "."
            );
        }
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
