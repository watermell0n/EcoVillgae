package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.quiz.domain.QuizQuestion;
import com.b201.ecovillage.quiz.domain.QuizQuestionVersion;
import com.b201.ecovillage.quiz.repository.QuizOptionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionVersionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionQuestionRepository;

@Service
public class QuizQuestionQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final QuizQuestionRepository questionRepository;
    private final QuizQuestionVersionRepository versionRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizSessionQuestionRepository sessionQuestionRepository;

    public QuizQuestionQueryService(
        ClassroomAuthorizationService authorizationService,
        QuizQuestionRepository questionRepository,
        QuizQuestionVersionRepository versionRepository,
        QuizOptionRepository optionRepository,
        QuizSessionQuestionRepository sessionQuestionRepository
    ) {
        this.authorizationService = authorizationService;
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.optionRepository = optionRepository;
        this.sessionQuestionRepository = sessionQuestionRepository;
    }

    @Transactional(readOnly = true)
    public List<QuizQuestionView> findAll(String teacherSubject, UUID classroomId) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.MANAGE_QUIZ_QUESTIONS,
            null
        );
        // 이미 퀴즈로 열린 문항은 다시 쓸 수 없습니다. 세션 하나가 아니라 학급 전체 기록을 봐야
        // 교사가 화면을 새로고침해도 같은 문항이 다시 선택 가능해지지 않습니다.
        Set<UUID> usedVersionIds = Set.copyOf(
            sessionQuestionRepository.findUsedQuestionVersionIds(classroomId)
        );
        return questionRepository.findAllByClassroomIdOrderByUpdatedAtDesc(classroomId)
            .stream()
            .map(question -> view(classroomId, question, usedVersionIds))
            .toList();
    }

    private QuizQuestionView view(
        UUID classroomId,
        QuizQuestion question,
        Set<UUID> usedVersionIds
    ) {
        QuizQuestionVersion version = versionRepository
            .findTopByClassroomIdAndQuizQuestionIdOrderByVersionDesc(
                classroomId,
                question.getId()
            )
            .orElseThrow();
        return new QuizQuestionView(
            question.getId(),
            version.getId(),
            question.getTitle(),
            question.getStatus().name(),
            version.getVersion(),
            version.getId(),
            question.getStateVersion(),
            version.getContentType(),
            version.getQuestionType(),
            version.getPrompt(),
            version.getExplanation(),
            version.getSource(),
            version.getAiRunId(),
            usedVersionIds.contains(version.getId()),
            optionRepository
                .findAllByClassroomIdAndQuizQuestionVersionIdOrderBySortOrder(
                    classroomId,
                    version.getId()
                )
                .stream()
                .map(option -> new QuizQuestionView.Option(
                    option.getId(),
                    option.getOptionKey(),
                    option.getOptionText(),
                    option.isCorrect(),
                    option.getSortOrder()
                ))
                .toList()
        );
    }
}
