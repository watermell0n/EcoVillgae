package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.quiz.domain.QuizQuestion;
import com.b201.ecovillage.quiz.domain.QuizQuestionVersion;
import com.b201.ecovillage.quiz.repository.QuizQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionVersionRepository;

@Service
public class QuizQuestionQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final QuizQuestionRepository questionRepository;
    private final QuizQuestionVersionRepository versionRepository;

    public QuizQuestionQueryService(
        ClassroomAuthorizationService authorizationService,
        QuizQuestionRepository questionRepository,
        QuizQuestionVersionRepository versionRepository
    ) {
        this.authorizationService = authorizationService;
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional(readOnly = true)
    public List<QuizQuestionView> findAll(String teacherSubject, UUID classroomId) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.MANAGE_QUIZ_QUESTIONS,
            null
        );
        return questionRepository.findAllByClassroomIdOrderByUpdatedAtDesc(classroomId)
            .stream()
            .map(question -> view(classroomId, question))
            .toList();
    }

    private QuizQuestionView view(UUID classroomId, QuizQuestion question) {
        QuizQuestionVersion version = versionRepository
            .findTopByClassroomIdAndQuizQuestionIdOrderByVersionDesc(
                classroomId,
                question.getId()
            )
            .orElseThrow();
        return new QuizQuestionView(
            question.getId(),
            question.getTitle(),
            question.getStatus().name(),
            version.getVersion(),
            question.getStateVersion(),
            version.getContentType(),
            version.getQuestionType(),
            version.getPrompt()
        );
    }
}
