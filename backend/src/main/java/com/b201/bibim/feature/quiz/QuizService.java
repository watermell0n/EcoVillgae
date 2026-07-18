package com.b201.bibim.feature.quiz;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.quiz.QuizAnswerEntity;
import com.b201.bibim.domain.quiz.QuizAnswerRepository;
import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizQuestionEntity;
import com.b201.bibim.domain.quiz.QuizQuestionRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionEntity;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.quiz.QuizDto.QuizOptionResponse;
import com.b201.bibim.feature.quiz.QuizDto.QuizQuestionResponse;
import com.b201.bibim.feature.quiz.QuizDto.QuizResultQuestionResponse;
import com.b201.bibim.feature.quiz.QuizDto.SaveAnswerRequest;
import com.b201.bibim.feature.quiz.QuizDto.SaveAnswerResponse;
import com.b201.bibim.feature.quiz.QuizDto.StudentQuizResponse;
import com.b201.bibim.feature.quiz.QuizDto.StudentQuizResultResponse;
import com.b201.bibim.feature.quiz.QuizDto.SubmitQuizResponse;
import com.b201.bibim.feature.quiz.QuizDto.TeacherQuizStudentResponse;
import com.b201.bibim.feature.quiz.QuizDto.TeacherQuizStudentQuestionResponse;
import com.b201.bibim.feature.quiz.QuizDto.TeacherQuizQuestionSummaryResponse;
import com.b201.bibim.feature.quiz.QuizDto.TeacherQuizSummaryResponse;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.feature.monitoring.ActivityEventService;
import com.b201.bibim.feature.flow.StudentFlowService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizSubmissionRepository submissionRepository;
    private final QuizAnswerRepository answerRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final ActivityEventService activityEventService;
    private final StudentFlowService studentFlowService;

    public QuizService(
            QuizRepository quizRepository,
            QuizQuestionRepository questionRepository,
            QuizSubmissionRepository submissionRepository,
            QuizAnswerRepository answerRepository,
            RoomRepository roomRepository,
            StudentRepository studentRepository,
            ActivityEventService activityEventService,
            StudentFlowService studentFlowService
    ) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.answerRepository = answerRepository;
        this.roomRepository = roomRepository;
        this.studentRepository = studentRepository;
        this.activityEventService = activityEventService;
        this.studentFlowService = studentFlowService;
    }

    @Transactional(readOnly = true)
    public StudentQuizResponse currentQuiz(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        QuizEntity quiz = findOpenQuiz(room, studentId);
        List<QuizQuestionEntity> questions = questionRepository
                .findAllByQuizIdOrderByOrderNo(quiz.getId());
        var submission = submissionRepository.findByQuizIdAndStudentId(quiz.getId(), studentId);
        List<QuizAnswerEntity> answers = submission
                .map(item -> answerRepository.findAllBySubmissionId(item.getId()))
                .orElseGet(List::of);
        Map<UUID, String> savedAnswers = answers.stream().collect(Collectors.toMap(
                QuizAnswerEntity::getQuestionId,
                QuizAnswerEntity::getAnswer
        ));

        return new StudentQuizResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getDayNumber(),
                quiz.getQuestionCount(),
                submission.map(QuizSubmissionEntity::getStatus).orElse("NOT_STARTED"),
                answers.size(),
                submission.map(QuizSubmissionEntity::getSubmittedAt).orElse(null),
                questions.stream()
                        .map(question -> toQuestionResponse(
                                question,
                                savedAnswers.get(question.getId())
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public StudentQuizResultResponse dayOneResult(UUID studentId) {
        return quizResult(studentId, (short) 1, "DAY1_QUIZ_RESULT");
    }

    @Transactional(readOnly = true)
    public StudentQuizResultResponse dayTwoResult(UUID studentId) {
        return quizResult(studentId, (short) 2, "DAY2_QUIZ_RESULT");
    }

    private StudentQuizResultResponse quizResult(
            UUID studentId,
            short dayNumber,
            String resultStage
    ) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        String effectiveStage = studentFlowService.effectiveStage(
                studentId,
                room.getCurrentStage()
        );
        if (!resultStage.equals(effectiveStage)) {
            throw quizResultNotAvailable();
        }
        QuizEntity quiz = quizRepository.findByRoomIdAndDayNumber(room.getId(), dayNumber)
                .filter(item -> "CLOSED".equals(item.getStatus()))
                .orElseThrow(this::quizResultNotAvailable);
        List<QuizQuestionEntity> questions = questionRepository
                .findAllByQuizIdOrderByOrderNo(quiz.getId());
        var submission = submissionRepository.findByQuizIdAndStudentId(quiz.getId(), studentId);
        List<QuizAnswerEntity> answers = submission
                .map(item -> answerRepository.findAllBySubmissionId(item.getId()))
                .orElseGet(List::of);
        Map<UUID, String> answersByQuestionId = answers.stream().collect(Collectors.toMap(
                QuizAnswerEntity::getQuestionId,
                QuizAnswerEntity::getAnswer
        ));
        List<QuizResultQuestionResponse> resultQuestions = questions.stream()
                .map(question -> {
                    String selectedAnswer = answersByQuestionId.get(question.getId());
                    return new QuizResultQuestionResponse(
                            question.getId(),
                            question.getOrderNo(),
                            question.getPrompt(),
                            question.getOptions().stream()
                                    .map(option -> new QuizOptionResponse(option.id(), option.text()))
                                    .toList(),
                            selectedAnswer,
                            question.getCorrectAnswer(),
                            question.isCorrect(selectedAnswer),
                            dayNumber == 1 ? null : question.getExplanation()
                    );
                })
                .toList();
        int correctCount = (int) resultQuestions.stream()
                .filter(QuizResultQuestionResponse::correct)
                .count();
        return new StudentQuizResultResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getQuestionCount(),
                answers.size(),
                correctCount,
                resultQuestions
        );
    }

    @Transactional
    public SaveAnswerResponse saveAnswer(
            UUID studentId,
            UUID quizId,
            UUID questionId,
            SaveAnswerRequest request
    ) {
        StudentEntity student = lockActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        QuizEntity quiz = findQuizForStudent(quizId, room, studentId);
        QuizQuestionEntity question = questionRepository.findByIdAndQuizId(questionId, quizId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "QUIZ_QUESTION_NOT_FOUND",
                        "퀴즈 문제를 찾을 수 없습니다."
                ));
        String answer = request.answer().trim().toUpperCase(Locale.ROOT);
        if (!question.accepts(answer)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUIZ_ANSWER",
                    "선택할 수 없는 답안입니다."
            );
        }

        QuizSubmissionEntity submission = getOrStartSubmission(quizId, studentId);
        ensureNotSubmitted(submission);
        QuizAnswerEntity savedAnswer = answerRepository
                .findBySubmissionIdAndQuestionId(submission.getId(), questionId)
                .map(existing -> {
                    existing.change(answer, question.isCorrect(answer));
                    return existing;
                })
                .orElseGet(() -> QuizAnswerEntity.create(
                        submission.getId(),
                        questionId,
                        answer,
                        question.isCorrect(answer)
                ));
        answerRepository.save(savedAnswer);
        int answeredCount = answerRepository.findAllBySubmissionId(submission.getId()).size();
        activityEventService.record(
                room.getId(),
                student.getId(),
                "STUDENT",
                student.getId(),
                "QUIZ_ANSWER_SAVED",
                room.getCurrentStage(),
                "QUIZ",
                "QUIZ_QUESTION_" + question.getOrderNo(),
                Map.of(
                        "quizId", quizId.toString(),
                        "questionId", questionId.toString(),
                        "answeredCount", answeredCount
                )
        );

        return new SaveAnswerResponse(questionId, answer, answeredCount, Instant.now());
    }

    @Transactional
    public SubmitQuizResponse submit(UUID studentId, UUID quizId) {
        StudentEntity student = lockActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        QuizEntity quiz = findQuizForStudent(quizId, room, studentId);
        QuizSubmissionEntity submission = getOrStartSubmission(quizId, studentId);
        ensureNotSubmitted(submission);

        int answeredCount = answerRepository.findAllBySubmissionId(submission.getId()).size();
        int correctCount = Math.toIntExact(
                answerRepository.countBySubmissionIdAndCorrectTrue(submission.getId())
        );
        submission.submit(correctCount);
        activityEventService.record(
                room.getId(),
                student.getId(),
                "STUDENT",
                student.getId(),
                "QUIZ_SUBMITTED",
                room.getCurrentStage(),
                "QUIZ",
                "QUIZ_SUBMITTED",
                Map.of(
                        "quizId", quizId.toString(),
                        "answeredCount", answeredCount,
                        "correctCount", correctCount
                )
        );

        return new SubmitQuizResponse(
                quiz.getId(),
                submission.getStatus(),
                answeredCount,
                quiz.getQuestionCount(),
                submission.getSubmittedAt()
        );
    }

    @Transactional(readOnly = true)
    public TeacherQuizSummaryResponse teacherSummary(UUID teacherId, UUID roomId) {
        RoomEntity room = findRoom(roomId);
        if (!room.getTeacherId().equals(teacherId)) {
            throw roomNotFound();
        }
        QuizEntity quiz = quizRepository
                .findByRoomIdAndDayNumber(roomId, (short) room.getCurrentDay())
                .orElseThrow(this::quizNotOpen);
        Map<UUID, QuizSubmissionEntity> submissions = submissionRepository
                .findAllByQuizId(quiz.getId())
                .stream()
                .collect(Collectors.toMap(
                        QuizSubmissionEntity::getStudentId,
                        Function.identity()
                ));
        List<QuizQuestionEntity> questions = questionRepository
                .findAllByQuizIdOrderByOrderNo(quiz.getId());
        List<QuizAnswerEntity> answers = submissions.isEmpty()
                ? List.of()
                : answerRepository.findAllBySubmissionIdIn(
                        submissions.values().stream().map(QuizSubmissionEntity::getId).toList()
                );
        Map<UUID, Map<UUID, QuizAnswerEntity>> answersBySubmission = answers.stream()
                .collect(Collectors.groupingBy(
                        QuizAnswerEntity::getSubmissionId,
                        Collectors.toMap(QuizAnswerEntity::getQuestionId, Function.identity())
                ));
        Set<UUID> submittedSubmissionIds = submissions.values().stream()
                .filter(QuizSubmissionEntity::isSubmitted)
                .map(QuizSubmissionEntity::getId)
                .collect(Collectors.toSet());
        List<TeacherQuizStudentResponse> students = studentRepository
                .findAllByRoomIdOrderByParticipantNumber(roomId)
                .stream()
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .map(student -> toTeacherStudent(
                        student,
                        submissions.get(student.getId()),
                        questions,
                        answersBySubmission
                ))
                .toList();
        long submittedCount = students.stream()
                .filter(student -> "SUBMITTED".equals(student.submissionStatus()))
                .count();
        long inProgressCount = students.stream()
                .filter(student -> "IN_PROGRESS".equals(student.submissionStatus()))
                .count();
        long notStartedCount = students.stream()
                .filter(student -> "NOT_STARTED".equals(student.submissionStatus()))
                .count();
        List<TeacherQuizQuestionSummaryResponse> questionSummaries = questions.stream()
                .map(question -> {
                    List<QuizAnswerEntity> submittedAnswers = answers.stream()
                            .filter(answer -> submittedSubmissionIds.contains(answer.getSubmissionId()))
                            .filter(answer -> question.getId().equals(answer.getQuestionId()))
                            .toList();
                    long correctCount = submittedAnswers.stream()
                            .filter(QuizAnswerEntity::isCorrect)
                            .count();
                    return new TeacherQuizQuestionSummaryResponse(
                            question.getId(),
                            question.getOrderNo(),
                            question.getPrompt(),
                            question.getOptions().stream()
                                    .map(option -> new QuizOptionResponse(option.id(), option.text()))
                                    .toList(),
                            question.getCorrectAnswer(),
                            room.getCurrentDay() == 1 ? null : question.getExplanation(),
                            submittedAnswers.size(),
                            correctCount
                    );
                })
                .toList();

        return new TeacherQuizSummaryResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getStatus(),
                quiz.getQuestionCount(),
                room.getExpectedStudentCount(),
                students.size(),
                submittedCount,
                inProgressCount,
                notStartedCount,
                students.size() - submittedCount,
                students,
                questionSummaries
        );
    }

    private QuizQuestionResponse toQuestionResponse(
            QuizQuestionEntity question,
            String savedAnswer
    ) {
        return new QuizQuestionResponse(
                question.getId(),
                question.getOrderNo(),
                question.getQuestionType(),
                question.getPrompt(),
                question.getOptions().stream()
                        .map(option -> new QuizOptionResponse(option.id(), option.text()))
                        .toList(),
                savedAnswer
        );
    }

    private TeacherQuizStudentResponse toTeacherStudent(
            StudentEntity student,
            QuizSubmissionEntity submission,
            List<QuizQuestionEntity> questions,
            Map<UUID, Map<UUID, QuizAnswerEntity>> answersBySubmission
    ) {
        String status = submission == null ? "NOT_STARTED" : submission.getStatus();
        Integer correctCount = submission != null && submission.isSubmitted()
                ? submission.getCorrectCount()
                : null;
        Map<UUID, QuizAnswerEntity> studentAnswers = submission == null
                ? Map.of()
                : answersBySubmission.getOrDefault(submission.getId(), Map.of());
        List<TeacherQuizStudentQuestionResponse> questionResults = submission != null
                && submission.isSubmitted()
                ? questions.stream().map(question -> {
                    QuizAnswerEntity answer = studentAnswers.get(question.getId());
                    return new TeacherQuizStudentQuestionResponse(
                            question.getId(),
                            question.getOrderNo(),
                            answer != null,
                            answer != null && answer.isCorrect()
                    );
                }).toList()
                : List.of();
        return new TeacherQuizStudentResponse(
                student.getId(),
                student.getParticipantNumber(),
                student.getNickname(),
                student.isOnline(),
                status,
                correctCount,
                submission == null ? null : submission.getSubmittedAt(),
                questionResults
        );
    }

    private QuizSubmissionEntity getOrStartSubmission(UUID quizId, UUID studentId) {
        return submissionRepository.findByQuizIdAndStudentId(quizId, studentId)
                .orElseGet(() -> submissionRepository.save(
                        QuizSubmissionEntity.start(quizId, studentId)
                ));
    }

    private StudentEntity findActiveStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private StudentEntity lockActiveStudent(UUID studentId) {
        return studentRepository.findByIdForUpdate(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private RoomEntity findRoom(UUID roomId) {
        return roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
    }

    private QuizEntity findOpenQuiz(RoomEntity room, UUID studentId) {
        String effectiveStage = studentFlowService.effectiveStage(studentId, room.getCurrentStage());
        short dayNumber = (short) ("DAY2_QUIZ".equals(effectiveStage) ? 2 : 1);
        QuizEntity quiz = quizRepository
                .findByRoomIdAndDayNumber(room.getId(), dayNumber)
                .orElseThrow(this::quizNotOpen);
        ensureQuizOpen(quiz, room, studentId);
        return quiz;
    }

    private QuizEntity findQuizForStudent(UUID quizId, RoomEntity room, UUID studentId) {
        QuizEntity quiz = quizRepository.findById(quizId)
                .filter(item -> item.getRoomId().equals(room.getId()))
                .orElseThrow(this::quizNotOpen);
        ensureQuizOpen(quiz, room, studentId);
        return quiz;
    }

    private void ensureQuizOpen(QuizEntity quiz, RoomEntity room, UUID studentId) {
        String expectedStage = quiz.getDayNumber() == 1 ? "DAY1_QUIZ" : "DAY2_QUIZ";
        if (studentFlowService.canUseStage(studentId, expectedStage)) return;
        if (!"OPEN".equals(quiz.getStatus())
                || quiz.getDayNumber() != room.getCurrentDay()
                || !expectedStage.equals(room.getCurrentStage())) {
            throw quizNotOpen();
        }
    }

    private void ensureNotSubmitted(QuizSubmissionEntity submission) {
        if (submission.isSubmitted()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUIZ_ALREADY_SUBMITTED",
                    "이미 제출한 퀴즈는 수정하거나 다시 제출할 수 없습니다."
            );
        }
    }

    private ApiException authenticationRequired() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "로그인이 필요합니다."
        );
    }

    private ApiException quizNotOpen() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "QUIZ_NOT_OPEN",
                "현재는 퀴즈를 풀 수 있는 단계가 아닙니다."
        );
    }

    private ApiException quizResultNotAvailable() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "QUIZ_RESULT_NOT_AVAILABLE",
                "퀴즈 마감 후 결과 확인 단계에서 공개됩니다."
        );
    }

    private ApiException roomNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "ROOM_NOT_FOUND",
                "수업 방을 찾을 수 없습니다."
        );
    }
}
