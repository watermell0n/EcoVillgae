package com.b201.ecovillage.quiz.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.AuthorizationDecision;
import com.b201.ecovillage.identity.authorization.ClassroomActorRole;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizResultVersion;
import com.b201.ecovillage.quiz.domain.QuizSession;
import com.b201.ecovillage.quiz.domain.QuizSessionQuestion;
import com.b201.ecovillage.quiz.domain.QuizSessionStatus;
import com.b201.ecovillage.quiz.domain.QuizStudentResult;
import com.b201.ecovillage.quiz.repository.QuizQuestionIssueRepository;
import com.b201.ecovillage.quiz.repository.QuizResultVersionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
import com.b201.ecovillage.quiz.repository.QuizStudentResultRepository;

@Service
public class QuizResultQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final QuizSessionRepository sessionRepository;
    private final QuizSessionQuestionRepository sessionQuestionRepository;
    private final QuizResultVersionRepository resultRepository;
    private final QuizStudentResultRepository studentResultRepository;
    private final QuizQuestionIssueRepository issueRepository;
    private final ClassroomStudentRepository studentRepository;
    private final QuizRewardBudgetService rewardBudgetService;

    public QuizResultQueryService(
        ClassroomAuthorizationService authorizationService,
        QuizSessionRepository sessionRepository,
        QuizSessionQuestionRepository sessionQuestionRepository,
        QuizResultVersionRepository resultRepository,
        QuizStudentResultRepository studentResultRepository,
        QuizQuestionIssueRepository issueRepository,
        ClassroomStudentRepository studentRepository,
        QuizRewardBudgetService rewardBudgetService
    ) {
        this.authorizationService = authorizationService;
        this.sessionRepository = sessionRepository;
        this.sessionQuestionRepository = sessionQuestionRepository;
        this.resultRepository = resultRepository;
        this.studentResultRepository = studentResultRepository;
        this.issueRepository = issueRepository;
        this.studentRepository = studentRepository;
        this.rewardBudgetService = rewardBudgetService;
    }

    @Transactional(readOnly = true)
    public QuizResultsView find(String subject, UUID classroomId, UUID sessionId) {
        UUID studentId = parseUuid(subject);
        AuthorizationDecision authorization = authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_QUIZ_RESULTS,
            studentId
        );
        QuizSession session = sessionRepository.findByClassroomIdAndId(classroomId, sessionId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz session could not be found."
            ));
        QuizResultVersion result = resultRepository
            .findTopByClassroomIdAndQuizSessionIdOrderByVersionDesc(classroomId, sessionId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz result could not be found."
            ));
        boolean student = authorization.actorType() == ClassroomActorRole.STU;
        if (
            student
                && (session.getStatus() != QuizSessionStatus.RESULT_PUBLISHED
                    && session.getStatus() != QuizSessionStatus.CLOSED)
        ) {
            throw operation(
                HttpStatus.FORBIDDEN,
                "PUBLIC_DATA_ONLY",
                "Students can read quiz results only after publication."
            );
        }

        List<QuizStudentResult> allResults = studentResultRepository
            .findAllByQuizResultVersionIdOrderByRankAsc(result.getId());
        List<UUID> questionIds = sessionQuestionRepository
            .findAllByClassroomIdAndQuizSessionIdOrderBySequenceNumber(classroomId, sessionId)
            .stream()
            .map(QuizSessionQuestion::getId)
            .toList();
        long invalidatedCount = questionIds.isEmpty()
            ? 0L
            : issueRepository.findAllByClassroomIdAndQuizSessionQuestionIdInAndStatus(
                classroomId,
                questionIds,
                "INVALIDATED"
            ).stream()
                .map(issue -> issue.getQuizSessionQuestionId())
                .distinct()
                .count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", session.getMode().name());
        summary.put("invalidated_question_count", invalidatedCount);
        summary.put("leaderboard_public", session.isLeaderboardPublic());

        if (student) {
            QuizStudentResult own = studentResultRepository
                .findByQuizResultVersionIdAndClassroomStudentId(result.getId(), studentId)
                .orElseThrow(() -> operation(
                    HttpStatus.FORBIDDEN,
                    "RESOURCE_NOT_OWNED",
                    "The authenticated student has no result in this quiz."
                ));
            summary.put("score", own.getScore());
            summary.put("correct_count", own.getCorrectCount());
            summary.put("rank", own.getRank() == null ? 0 : own.getRank());
            summary.put("reward_amount", own.getRewardAmount());
        } else {
            summary.put("participant_count", allResults.size());
            summary.put(
                "average_score",
                allResults.isEmpty()
                    ? 0
                    : Math.round(allResults.stream()
                        .mapToInt(QuizStudentResult::getScore)
                        .average()
                        .orElse(0D))
            );
            summary.put("reward_total", allResults.stream()
                .mapToLong(QuizStudentResult::getRewardAmount)
                .sum());
            QuizRewardBudget budget = rewardBudgetService.evaluate(classroomId, 0L);
            summary.put("reward_share_bps", budget.rewardShareBasisPoints());
            summary.put("reward_warning", budget.warning());
        }

        boolean leaderboardVisible = session.getMode() == QuizMode.GAME
            && (!student || session.isLeaderboardPublic());
        List<QuizLeaderboardEntryView> leaderboard = leaderboardVisible
            ? leaderboard(result, allResults)
            : List.of();
        return new QuizResultsView(
            result.getVersion(),
            result.getStatus(),
            summary,
            leaderboard
        );
    }

    private List<QuizLeaderboardEntryView> leaderboard(
        QuizResultVersion result,
        List<QuizStudentResult> results
    ) {
        List<QuizStudentResult> top = results.stream()
            .filter(item -> item.getRank() != null && item.getRank() <= 3)
            .sorted(Comparator.comparingInt(QuizStudentResult::getRank))
            .toList();
        Map<UUID, ClassroomStudent> students = studentRepository.findAllById(
            top.stream().map(QuizStudentResult::getClassroomStudentId).toList()
        ).stream().collect(Collectors.toMap(ClassroomStudent::getId, Function.identity()));
        return top.stream().map(item -> {
            ClassroomStudent student = students.get(item.getClassroomStudentId());
            return new QuizLeaderboardEntryView(
                item.getId(),
                item.getRank(),
                student == null ? "student" : student.getNickname(),
                null,
                result.getVersion()
            );
        }).toList();
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
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
