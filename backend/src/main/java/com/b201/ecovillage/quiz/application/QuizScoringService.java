package com.b201.ecovillage.quiz.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.quiz.domain.ActivityParticipant;
import com.b201.ecovillage.quiz.domain.QuizAnswer;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizStudentResult;
import com.b201.ecovillage.quiz.domain.QuizSubmission;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.QuizAnswerRepository;
import com.b201.ecovillage.quiz.repository.QuizSubmissionRepository;

@Service
public class QuizScoringService {

    private final ActivityParticipantRepository participantRepository;
    private final QuizSubmissionRepository submissionRepository;
    private final QuizAnswerRepository answerRepository;
    private final UuidV7Generator uuidGenerator;

    public QuizScoringService(
        ActivityParticipantRepository participantRepository,
        QuizSubmissionRepository submissionRepository,
        QuizAnswerRepository answerRepository,
        UuidV7Generator uuidGenerator
    ) {
        this.participantRepository = participantRepository;
        this.submissionRepository = submissionRepository;
        this.answerRepository = answerRepository;
        this.uuidGenerator = uuidGenerator;
    }

    public List<QuizStudentResult> score(
        UUID classroomId,
        UUID sessionId,
        UUID resultVersionId,
        QuizMode mode,
        long rewardPerCorrectAnswer,
        Set<UUID> invalidatedQuestionIds,
        Instant scoredAt
    ) {
        List<UUID> studentIds = participantRepository
            .findAllByClassroomIdAndActivitySessionId(classroomId, sessionId)
            .stream()
            .map(ActivityParticipant::getClassroomStudentId)
            .distinct()
            .toList();
        List<QuizSubmission> allSubmissions = submissionRepository
            .findAllByClassroomIdAndQuizSessionId(classroomId, sessionId);
        // 학생마다 채점할 답안지 한 장을 고른다. 평가형은 "제출"을 누른 답안지를 우선하되,
        // 누르지 못한 학생은 마지막으로 저장된 답안지로 채점한다. 교사가 제출을 마감한
        // 시점의 답안지가 곧 걷은 시험지이므로, 제출 버튼을 못 눌렀다고 0점을 줄 이유가 없다.
        Map<UUID, QuizSubmission> latestSubmission = allSubmissions.stream()
            .collect(Collectors.toMap(
                QuizSubmission::getClassroomStudentId,
                Function.identity(),
                (left, right) -> preferredSubmission(mode, left, right)
            ));
        List<UUID> submissionIds = latestSubmission.values().stream()
            .map(QuizSubmission::getId)
            .toList();
        Map<UUID, UUID> studentBySubmission = latestSubmission.entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getValue().getId(), Map.Entry::getKey));
        Map<UUID, Score> scores = new HashMap<>();
        studentIds.forEach(studentId -> scores.put(studentId, new Score()));
        if (!submissionIds.isEmpty()) {
            for (QuizAnswer answer : answerRepository
                .findAllByClassroomIdAndQuizSubmissionIdIn(classroomId, submissionIds)) {
                if (invalidatedQuestionIds.contains(answer.getQuizSessionQuestionId())) {
                    continue;
                }
                UUID studentId = studentBySubmission.get(answer.getQuizSubmissionId());
                Score score = scores.get(studentId);
                if (score == null) {
                    continue;
                }
                score.points = Math.addExact(score.points, answer.getScoreAwarded());
                score.correctCount += answer.isCorrect() ? 1 : 0;
                score.responseTimeMs = Math.addExact(
                    score.responseTimeMs,
                    answer.getResponseTimeMs()
                );
            }
        }

        List<RankedStudent> ranked = studentIds.stream()
            .map(studentId -> new RankedStudent(studentId, scores.get(studentId)))
            .sorted(
                Comparator.comparingInt((RankedStudent item) -> item.score().points).reversed()
                    .thenComparingLong(item -> item.score().responseTimeMs)
                    .thenComparing(RankedStudent::studentId)
            )
            .toList();
        Map<UUID, Integer> rankByStudent = new HashMap<>();
        if (mode == QuizMode.GAME) {
            for (int index = 0; index < ranked.size(); index++) {
                rankByStudent.put(ranked.get(index).studentId(), index + 1);
            }
        }

        List<QuizStudentResult> results = new ArrayList<>(studentIds.size());
        for (UUID studentId : studentIds) {
            Score score = scores.get(studentId);
            // 게임형 보상은 문제당 보상액 × 맞힌 문제 수다. 참여만 해서는 받지 못한다.
            long reward = mode == QuizMode.GAME
                ? Math.multiplyExact(rewardPerCorrectAnswer, (long) score.correctCount)
                : 0L;
            results.add(QuizStudentResult.scored(
                uuidGenerator.generate(),
                classroomId,
                resultVersionId,
                studentId,
                score.points,
                score.correctCount,
                rankByStudent.get(studentId),
                reward,
                score.responseTimeMs,
                scoredAt
            ));
        }
        return results;
    }

    /**
     * 한 학생의 답안지 둘 중 채점할 하나다. 평가형은 제출한 답안지를 먼저 보고, 둘 다
     * 제출했거나 둘 다 임시 저장이면 나중 판본을 쓴다.
     */
    private static QuizSubmission preferredSubmission(
        QuizMode mode,
        QuizSubmission left,
        QuizSubmission right
    ) {
        if (mode == QuizMode.TEST && left.isSubmitted() != right.isSubmitted()) {
            return left.isSubmitted() ? left : right;
        }
        return left.getAnswerSheetVersion() >= right.getAnswerSheetVersion() ? left : right;
    }

    private static final class Score {
        private int points;
        private int correctCount;
        private long responseTimeMs;
    }

    private record RankedStudent(UUID studentId, Score score) {
    }
}
