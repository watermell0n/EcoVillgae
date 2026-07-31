package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.quiz.domain.QuizSession;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;

@Service
public class QuizSessionQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ActivityParticipantRepository participantRepository;
    private final QuizSessionRepository sessionRepository;
    private final QuizSessionQuestionRepository questionRepository;

    public QuizSessionQueryService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ActivityParticipantRepository participantRepository,
        QuizSessionRepository sessionRepository,
        QuizSessionQuestionRepository questionRepository
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public QuizSessionView find(
        String subject,
        UUID classroomId,
        UUID sessionId
    ) {
        if (userRepository.findByExternalSubject(subject).isPresent()) {
            authorizationService.authorize(
                subject,
                classroomId,
                ClassroomPermission.MANAGE_QUIZ_SESSIONS,
                null
            );
        } else {
            UUID studentId = studentId(subject);
            authorizationService.authorize(
                subject,
                classroomId,
                ClassroomPermission.PARTICIPATE_QUIZ,
                studentId
            );
            if (
                !participantRepository
                    .existsByClassroomIdAndActivitySessionIdAndClassroomStudentId(
                        classroomId,
                        sessionId,
                        studentId
                    )
            ) {
                throw operation(
                    HttpStatus.FORBIDDEN,
                    "NOT_PARTICIPANT",
                    "The student is not a participant in this quiz."
                );
            }
        }
        QuizSession session = sessionRepository
            .findByClassroomIdAndId(classroomId, sessionId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz session could not be found."
            ));
        List<QuizSessionQuestionView> questions = questionRepository
            .findAllByClassroomIdAndQuizSessionIdOrderBySequenceNumber(
                classroomId,
                sessionId
            )
            .stream()
            .map(question -> new QuizSessionQuestionView(
                question.getId(),
                "Question " + question.getSequenceNumber(),
                "AVAILABLE",
                1,
                0,
                question.getPromptSnapshot(),
                question.getOptionsSnapshot().stream()
                    .map(option -> String.valueOf(option.get("option_text")))
                    .toList()
            ))
            .toList();
        return new QuizSessionView(
            session.getId(),
            session.getMode(),
            session.getStatus().name(),
            questions
        );
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid session is required."
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
