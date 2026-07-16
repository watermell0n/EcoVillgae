package com.b201.bibim.feature.room;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizQuestionEntity;
import com.b201.bibim.domain.quiz.QuizQuestionEntity.QuizOption;
import com.b201.bibim.domain.quiz.QuizQuestionRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesEntity;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.shop.ProductEntity;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.room.TeacherRoomManagementDto.CreateRoomRequest;
import com.b201.bibim.feature.room.TeacherRoomManagementDto.RoomPreparationResponse;
import com.b201.bibim.feature.room.TeacherRoomManagementDto.StudentPreparationResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherRoomManagementService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_RANDOM_LENGTH = 5;

    private static final List<QuestionDefinition> DAY_ONE_QUESTIONS = List.of(
            question(
                    "내가 맡은 역할의 일을 하고 받는 돈을 무엇이라고 할까요?",
                    "역할 소득", "예금 이자", "투자 손실", "상품 가격",
                    "A",
                    "일한 대가로 받는 돈을 소득이라고 합니다."
            ),
            question(
                    "역할 소득 100비빔과 퀴즈 참여수당 10비빔을 받으면 총소득은 얼마일까요?",
                    "90비빔", "100비빔", "110비빔", "120비빔",
                    "C",
                    "100비빔과 10비빔을 더하면 110비빔입니다."
            ),
            question(
                    "소득의 일부를 세금으로 내는 이유로 가장 알맞은 것은 무엇일까요?",
                    "돈을 모두 없애기 위해서", "함께 필요한 곳에 사용하기 위해서",
                    "상품 재고를 줄이기 위해서", "투자 위험을 높이기 위해서",
                    "B",
                    "세금은 공동체에 필요한 일과 서비스를 위해 사용됩니다."
            ),
            question(
                    "돈을 비교적 안전하게 맡기고 약속된 이자를 받는 활동은 무엇일까요?",
                    "소비", "예금", "투표", "세금",
                    "B",
                    "예금은 돈을 일정 기간 맡기고 이자를 받는 금융 활동입니다."
            ),
            question(
                    "일반적으로 더 큰 수익을 기대하는 투자일수록 무엇도 커질 수 있을까요?",
                    "손실 위험", "참여 코드", "학급 인원", "예금 기간",
                    "A",
                    "기대 수익이 큰 투자에는 보통 더 큰 손실 위험이 따릅니다."
            ),
            question(
                    "투자금을 여러 곳에 나누어 투자하는 방법을 무엇이라고 할까요?",
                    "충동구매", "소득세", "분산투자", "중도 인출",
                    "C",
                    "분산투자는 투자 대상을 나누어 위험을 줄이는 방법입니다."
            )
    );

    private static final List<ProductDefinition> DEFAULT_PRODUCTS = List.of(
            new ProductDefinition(
                    "연필 세트",
                    "수업과 일상에서 알차게 사용할 수 있는 연필 세트예요.",
                    25,
                    10
            ),
            new ProductDefinition(
                    "간식 꾸러미",
                    "활동을 마친 뒤 즐길 수 있는 작은 간식 꾸러미예요.",
                    40,
                    8
            ),
            new ProductDefinition(
                    "우선 선택권",
                    "다음 학급 활동에서 순서를 먼저 선택할 수 있어요.",
                    70,
                    3
            )
    );

    private final RoomRepository roomRepository;
    private final RoomRulesRepository roomRulesRepository;
    private final DaySessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final ProductRepository productRepository;

    public TeacherRoomManagementService(
            RoomRepository roomRepository,
            RoomRulesRepository roomRulesRepository,
            DaySessionRepository sessionRepository,
            StudentRepository studentRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository questionRepository,
            ProductRepository productRepository
    ) {
        this.roomRepository = roomRepository;
        this.roomRulesRepository = roomRulesRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public RoomPreparationResponse create(UUID teacherId, CreateRoomRequest request) {
        RoomEntity room = roomRepository.save(RoomEntity.preparing(
                teacherId,
                request.name().trim(),
                request.expectedStudentCount()
        ));
        roomRulesRepository.save(RoomRulesEntity.defaults(room.getId()));
        sessionRepository.save(DaySessionEntity.dayOneReady(room.getId()));

        List<StudentEntity> students = createInvitedStudents(
                room.getId(),
                request.expectedStudentCount()
        );
        studentRepository.saveAll(students);

        QuizEntity quiz = quizRepository.save(QuizEntity.dayOneReady(
                room.getId(),
                DAY_ONE_QUESTIONS.size()
        ));
        questionRepository.saveAll(createDayOneQuestions(quiz.getId()));
        productRepository.saveAll(createDefaultProducts(room.getId()));

        return toResponse(room, students);
    }

    @Transactional(readOnly = true)
    public RoomPreparationResponse students(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        return toResponse(
                room,
                studentRepository.findAllByRoomIdOrderByParticipantNumber(roomId)
        );
    }

    private List<StudentEntity> createInvitedStudents(UUID roomId, int studentCount) {
        List<StudentEntity> students = new ArrayList<>(studentCount);
        Set<String> generatedCodes = new HashSet<>();
        for (int participantNumber = 1; participantNumber <= studentCount; participantNumber += 1) {
            students.add(StudentEntity.invited(
                    roomId,
                    participantNumber,
                    nextParticipationCode(generatedCodes)
            ));
        }
        return students;
    }

    private String nextParticipationCode(Set<String> generatedCodes) {
        for (int attempt = 0; attempt < 100; attempt += 1) {
            StringBuilder code = new StringBuilder("ECO-");
            for (int index = 0; index < CODE_RANDOM_LENGTH; index += 1) {
                code.append(CODE_ALPHABET[SECURE_RANDOM.nextInt(CODE_ALPHABET.length)]);
            }
            String candidate = code.toString();
            if (generatedCodes.add(candidate)
                    && !studentRepository.existsByParticipationCode(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PARTICIPATION_CODE_GENERATION_FAILED",
                "참여 코드를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."
        );
    }

    private List<QuizQuestionEntity> createDayOneQuestions(UUID quizId) {
        List<QuizQuestionEntity> questions = new ArrayList<>(DAY_ONE_QUESTIONS.size());
        for (int index = 0; index < DAY_ONE_QUESTIONS.size(); index += 1) {
            QuestionDefinition definition = DAY_ONE_QUESTIONS.get(index);
            questions.add(QuizQuestionEntity.multipleChoice(
                    quizId,
                    index + 1,
                    definition.prompt(),
                    definition.options(),
                    definition.correctAnswer(),
                    definition.explanation()
            ));
        }
        return questions;
    }

    private List<ProductEntity> createDefaultProducts(UUID roomId) {
        List<ProductEntity> products = new ArrayList<>(DEFAULT_PRODUCTS.size());
        for (int index = 0; index < DEFAULT_PRODUCTS.size(); index += 1) {
            ProductDefinition definition = DEFAULT_PRODUCTS.get(index);
            products.add(ProductEntity.forSale(
                    roomId,
                    definition.name(),
                    definition.description(),
                    definition.price(),
                    definition.stock(),
                    index + 1
            ));
        }
        return products;
    }

    private RoomEntity findOwnedRoom(UUID teacherId, UUID roomId) {
        return roomRepository.findById(roomId)
                .filter(room -> room.getTeacherId().equals(teacherId))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ROOM_NOT_FOUND",
                        "수업 방을 찾을 수 없습니다."
                ));
    }

    private RoomPreparationResponse toResponse(
            RoomEntity room,
            List<StudentEntity> students
    ) {
        List<StudentPreparationResponse> studentResponses = students.stream()
                .map(student -> new StudentPreparationResponse(
                        student.getId(),
                        student.getParticipantNumber(),
                        student.getParticipationCode(),
                        student.getNickname(),
                        student.getStatus(),
                        student.isOnline(),
                        student.getLastSeenAt()
                ))
                .toList();
        long connectedStudentCount = students.stream()
                .filter(StudentEntity::isOnline)
                .count();
        return new RoomPreparationResponse(
                room.getId(),
                room.getName(),
                room.getExpectedStudentCount(),
                connectedStudentCount,
                room.getStatus(),
                room.getCurrentStage(),
                studentResponses
        );
    }

    private static QuestionDefinition question(
            String prompt,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctAnswer,
            String explanation
    ) {
        return new QuestionDefinition(
                prompt,
                List.of(
                        new QuizOption("A", optionA),
                        new QuizOption("B", optionB),
                        new QuizOption("C", optionC),
                        new QuizOption("D", optionD)
                ),
                correctAnswer,
                explanation
        );
    }

    private record QuestionDefinition(
            String prompt,
            List<QuizOption> options,
            String correctAnswer,
            String explanation
    ) {
    }

    private record ProductDefinition(
            String name,
            String description,
            long price,
            int stock
    ) {
    }
}
