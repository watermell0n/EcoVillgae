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
        long rewardPerParticipant,
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
        Map<UUID, QuizSubmission> latestSubmission = allSubmissions.stream()
            .filter(submission -> mode == QuizMode.GAME || submission.isSubmitted())
            .collect(Collectors.toMap(
                QuizSubmission::getClassroomStudentId,
                Function.identity(),
                (left, right) -> left.getAnswerSheetVersion() >= right.getAnswerSheetVersion()
                    ? left
                    : right
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
            long reward = mode == QuizMode.GAME && latestSubmission.containsKey(studentId)
                ? rewardPerParticipant
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

    private static final class Score {
        private int points;
        private int correctCount;
        private long responseTimeMs;
    }

    private record RankedStudent(UUID studentId, Score score) {
    }
}
