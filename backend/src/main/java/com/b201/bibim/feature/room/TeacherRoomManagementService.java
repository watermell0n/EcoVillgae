package com.b201.bibim.feature.room;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizQuestionEntity;
import com.b201.bibim.domain.quiz.QuizQuestionEntity.QuizOption;
import com.b201.bibim.domain.quiz.QuizQuestionRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.monitoring.StudentPresenceEntity;
import com.b201.bibim.domain.monitoring.StudentPresenceRepository;
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
import com.b201.bibim.domain.student.StudentRoleCatalogEntity;
import com.b201.bibim.domain.student.StudentRoleCatalogRepository;
import com.b201.bibim.feature.room.TeacherRoomManagementDto.CreateRoomRequest;
import com.b201.bibim.feature.room.TeacherRoomManagementDto.RoomPreparationResponse;
import com.b201.bibim.feature.room.TeacherRoomManagementDto.StudentPreparationResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherRoomManagementService {

    private static final int FIXED_STUDENT_COUNT = 24;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_RANDOM_LENGTH = 5;
    private static final Duration ONLINE_THRESHOLD = Duration.ofSeconds(10);
    private static final Duration OFFLINE_THRESHOLD = Duration.ofSeconds(30);

    private static final List<QuestionDefinition> DAY_ONE_QUESTIONS = List.of(
            question(
                    "《담을 넘은 아이》의 주인공 이름은 무엇인가요?",
                    "C",
                    "해님", "효진", "푸실", "귀손"
            ),
            question(
                    "《담을 넘은 아이》를 쓴 사람은 누구인가요?",
                    "A",
                    "김정민", "이영환", "황선미", "유은실"
            ),
            question(
                    "《담을 넘은 아이》를 출판한 출판사는 어디인가요?",
                    "A",
                    "비룡소", "창비", "문학동네", "웅진주니어"
            ),
            question(
                    "푸실이의 어머니가 대감댁에 가게 된 이유는 무엇인가요?",
                    "B",
                    "푸실이에게 글을 가르치기 위해서",
                    "귀손이의 약값을 갚고 젖어미로 일하기 위해서",
                    "새로운 집으로 이사하기 위해서",
                    "효진 아가씨를 만나기 위해서"
            ),
            question(
                    "푸실이가 글을 배우고 싶어 한 가장 큰 이유는 무엇인가요?",
                    "B",
                    "다른 사람에게 자랑하기 위해",
                    "자신의 생각을 알고 세상을 더 넓게 보기 위해",
                    "돈을 많이 벌기 위해",
                    "집안일을 하지 않기 위해"
            ),
            question(
                    "효진의 아버지는 푸실이가 가진 책을 보고 놀랍니다. 푸실이와 대화를 나눈 뒤, 마지막에 무슨 약속을 꼭 지키라고 했나요?",
                    "D",
                    "책을 읽고 나서 돌려주라는 약속",
                    "책의 주인을 꼭 찾아주라는 약속",
                    "책의 값으로 쌀 두 가마니를 내겠냐는 약속",
                    "글을 배워서 책을 꼭 읽겠다는 약속"
            ),
            question(
                    "'어머니가 대감마님댁에 간 지도 달포나 지났다.'에서 '달포'의 뜻은 무엇인가요?",
                    "C",
                    "한 달의 절반",
                    "딱 한 달",
                    "한 달이 조금 넘는 기간",
                    "한 달이 조금 안 되는 기간"
            ),
            question(
                    "책 제목에서 ‘담’이 의미하는 것으로 가장 알맞은 것은 무엇인가요?",
                    "C",
                    "마을을 지키는 실제 성벽만을 뜻한다.",
                    "사람들이 함께 쉬는 장소를 뜻한다.",
                    "푸실이를 가로막는 차별과 사회의 규칙을 뜻한다.",
                    "푸실이의 가족이 사는 집을 뜻한다."
            ),
            question(
                    "152쪽, 이 책의 마지막 문장은 무엇인지 고르시오.",
                    "A",
                    "어찌 살 것입니까.",
                    "저는 담을 넘겠습니다.",
                    "더 힘차게 앞으로 나아가겠습니다."
            ),
            question(
                    "《담을 넘은 아이》가 우리에게 전하는 생각으로 가장 알맞은 것은 무엇인가요?",
                    "D",
                    "옛날의 규칙은 언제나 지켜야 한다.",
                    "태어난 환경에 따라 사람의 가능성이 정해진다.",
                    "어려움이 생기면 자신의 꿈을 포기해야 한다.",
                    "차별과 편견을 넘어 자신의 삶을 스스로 선택할 수 있다."
            )
    );

    private static final List<ProductDefinition> DEFAULT_PRODUCTS = List.of(
            new ProductDefinition(
                    "말랑카우 빠삐코",
                    "달콤하고 시원하게 즐기는 간식 세트예요.",
                    35,
                    38
            ),
            new ProductDefinition(
                    "쿠크다스케이크",
                    "부드럽고 달콤한 케이크 간식이에요.",
                    65,
                    10
            ),
            new ProductDefinition(
                    "마이쭈 딸기",
                    "쫀득하게 즐기는 딸기맛 간식이에요.",
                    45,
                    18
            ),
            new ProductDefinition(
                    "포도 알맹이",
                    "탱글탱글한 포도맛 젤리 간식이에요.",
                    80,
                    6
            ),
            new ProductDefinition(
                    "카피바라 지우개",
                    "귀여운 카피바라 모양 지우개예요.",
                    120,
                    3
            ),
            new ProductDefinition(
                    "과일말랑이",
                    "말랑한 과일 모양 촉감 장난감이에요.",
                    150,
                    2
            ),
            new ProductDefinition(
                    "왁뿌볼",
                    "재미있게 가지고 놀 수 있는 말랑한 공이에요.",
                    200,
                    1
            ),
            new ProductDefinition(
                    "배움공책 면제권",
                    "배움공책 활동을 한 번 면제받을 수 있는 이용권이에요.",
                    90,
                    15
            )
    );

    private final RoomRepository roomRepository;
    private final RoomRulesRepository roomRulesRepository;
    private final DaySessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final ProductRepository productRepository;
    private final StudentPresenceRepository presenceRepository;
    private final StudentRoleCatalogRepository studentRoleCatalogRepository;

    public TeacherRoomManagementService(
            RoomRepository roomRepository,
            RoomRulesRepository roomRulesRepository,
            DaySessionRepository sessionRepository,
            StudentRepository studentRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository questionRepository,
            ProductRepository productRepository,
            StudentPresenceRepository presenceRepository,
            StudentRoleCatalogRepository studentRoleCatalogRepository
    ) {
        this.roomRepository = roomRepository;
        this.roomRulesRepository = roomRulesRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.productRepository = productRepository;
        this.presenceRepository = presenceRepository;
        this.studentRoleCatalogRepository = studentRoleCatalogRepository;
    }

    @Transactional
    public RoomPreparationResponse create(UUID teacherId, CreateRoomRequest request) {
        List<StudentRoleCatalogEntity> roleCatalog = studentRoleCatalogRepository
                .findAllByOrderByParticipantNumber();
        if (roleCatalog.size() != FIXED_STUDENT_COUNT) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STUDENT_ROLE_CATALOG_INVALID",
                    "24명의 1인1역 목록을 확인할 수 없습니다."
            );
        }
        RoomEntity room = roomRepository.save(RoomEntity.preparing(
                teacherId,
                request.name().trim(),
                FIXED_STUDENT_COUNT
        ));
        roomRulesRepository.save(RoomRulesEntity.defaults(room.getId()));
        sessionRepository.save(DaySessionEntity.dayOneReady(room.getId()));

        List<StudentEntity> students = createInvitedStudents(room.getId(), roleCatalog);
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

    @Transactional
    public RoomPreparationResponse archive(UUID teacherId, UUID roomId) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId)
                .filter(item -> item.getTeacherId().equals(teacherId))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ROOM_NOT_FOUND",
                        "수업 방을 찾을 수 없습니다."
                ));
        room.archive();
        return toResponse(
                room,
                studentRepository.findAllByRoomIdOrderByParticipantNumber(roomId)
        );
    }

    private List<StudentEntity> createInvitedStudents(
            UUID roomId,
            List<StudentRoleCatalogEntity> roleCatalog
    ) {
        List<StudentEntity> students = new ArrayList<>(roleCatalog.size());
        Set<String> generatedCodes = new HashSet<>();
        for (StudentRoleCatalogEntity role : roleCatalog) {
            StudentEntity student = StudentEntity.invited(
                    roomId,
                    role.getParticipantNumber(),
                    nextParticipationCode(generatedCodes)
            );
            student.assignRoleName(role.getRoleName());
            students.add(student);
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
                    null
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
        Map<UUID, StudentPresenceEntity> presenceByStudent = presenceRepository
                .findAllByRoomId(room.getId())
                .stream()
                .collect(Collectors.toMap(StudentPresenceEntity::getStudentId, Function.identity()));
        Instant now = Instant.now();
        List<StudentPreparationResponse> studentResponses = students.stream()
                .map(student -> new StudentPreparationResponse(
                        student.getId(),
                        student.getParticipantNumber(),
                        student.getParticipationCode(),
                        student.getNickname(),
                        student.getStatus(),
                        "ONLINE".equals(connectionStatus(presenceByStudent.get(student.getId()), now)),
                        connectionStatus(presenceByStudent.get(student.getId()), now),
                        presenceByStudent.containsKey(student.getId())
                                ? presenceByStudent.get(student.getId()).getLastSeenAt()
                                : student.getLastSeenAt()
                ))
                .toList();
        long connectedStudentCount = studentResponses.stream()
                .filter(student -> "ONLINE".equals(student.connectionStatus()))
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

    private String connectionStatus(StudentPresenceEntity presence, Instant now) {
        if (presence == null) return "NEVER_CONNECTED";
        if (presence.getForcedOfflineAt() != null) return "OFFLINE";
        Duration elapsed = Duration.between(presence.getLastSeenAt(), now);
        if (elapsed.compareTo(OFFLINE_THRESHOLD) > 0) return "OFFLINE";
        if ("HIDDEN".equals(presence.getVisibility())) return "AWAY";
        if (elapsed.compareTo(ONLINE_THRESHOLD) <= 0) return "ONLINE";
        return "UNSTABLE";
    }

    private static QuestionDefinition question(
            String prompt,
            String correctAnswer,
            String... optionTexts
    ) {
        List<QuizOption> options = new ArrayList<>(optionTexts.length);
        for (int index = 0; index < optionTexts.length; index += 1) {
            options.add(new QuizOption(
                    String.valueOf((char) ('A' + index)),
                    optionTexts[index]
            ));
        }
        return new QuestionDefinition(
                prompt,
                List.copyOf(options),
                correctAnswer
        );
    }

    private record QuestionDefinition(
            String prompt,
            List<QuizOption> options,
            String correctAnswer
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
