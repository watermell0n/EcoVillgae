package com.b201.bibim.feature.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizQuestionEntity;
import com.b201.bibim.domain.quiz.QuizQuestionRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesEntity;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.domain.shop.ProductEntity;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.room.TeacherRoomManagementDto.CreateRoomRequest;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TeacherRoomManagementServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomRulesRepository roomRulesRepository;

    @Mock
    private DaySessionRepository sessionRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizQuestionRepository questionRepository;

    @Mock
    private ProductRepository productRepository;

    @Test
    void createsRoomWithUniqueCodesAndDefaultContent() {
        UUID teacherId = UUID.randomUUID();
        when(roomRepository.save(any(RoomEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(quizRepository.save(any(QuizEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(studentRepository.existsByParticipationCode(anyString())).thenReturn(false);

        TeacherRoomManagementService service = service();
        var response = service.create(
                teacherId,
                new CreateRoomRequest("  6학년 3반 경제교실  ", 3)
        );

        assertEquals("6학년 3반 경제교실", response.name());
        assertEquals(3, response.expectedStudentCount());
        assertEquals("PREPARING", response.status());
        assertEquals("DAY1_LOBBY", response.currentStage());
        assertEquals(3, response.students().size());
        assertEquals(0, response.connectedStudentCount());
        assertEquals(
                3,
                new HashSet<>(response.students().stream()
                        .map(item -> item.participationCode())
                        .toList()).size()
        );
        assertTrue(response.students().stream()
                .allMatch(item -> item.participationCode().startsWith("ECO-")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<StudentEntity>> studentCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(studentRepository).saveAll(studentCaptor.capture());
        assertEquals(3, count(studentCaptor.getValue()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<QuizQuestionEntity>> questionCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(questionRepository).saveAll(questionCaptor.capture());
        List<QuizQuestionEntity> questions = toList(questionCaptor.getValue());
        assertEquals(10, questions.size());
        assertEquals("《담을 넘은 아이》의 주인공 이름은 무엇인가요?",
                questions.getFirst().getPrompt());
        assertEquals("C", questions.getFirst().getCorrectAnswer());
        assertEquals("푸실", questions.getFirst().getOptions().get(2).text());
        assertEquals(3, questions.get(8).getOptions().size());
        assertEquals("D", questions.getLast().getCorrectAnswer());
        assertTrue(questions.stream().allMatch(question -> question.getExplanation() == null));

        ArgumentCaptor<QuizEntity> quizCaptor = ArgumentCaptor.forClass(QuizEntity.class);
        verify(quizRepository).save(quizCaptor.capture());
        assertEquals("독서 퀴즈", quizCaptor.getValue().getTitle());
        assertEquals(10, quizCaptor.getValue().getQuestionCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ProductEntity>> productCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(productRepository).saveAll(productCaptor.capture());
        List<ProductEntity> products = toList(productCaptor.getValue());
        assertEquals(8, products.size());
        assertEquals(
                List.of(
                        "말랑카우 빠삐코", "쿠크다스케이크", "마이쭈 딸기", "포도 알맹이",
                        "카피바라 지우개", "과일말랑이", "왁뿌볼", "배움공책 면제권"
                ),
                products.stream().map(ProductEntity::getName).toList()
        );
        assertEquals(List.of(35L, 65L, 45L, 80L, 120L, 150L, 200L, 90L),
                products.stream().map(ProductEntity::getOriginalPrice).toList());
        assertEquals(List.of(38, 10, 18, 6, 3, 2, 1, 15),
                products.stream().map(ProductEntity::getInitialStock).toList());

        ArgumentCaptor<RoomRulesEntity> rulesCaptor = ArgumentCaptor.forClass(RoomRulesEntity.class);
        verify(roomRulesRepository).save(rulesCaptor.capture());
        RoomRulesEntity rules = rulesCaptor.getValue();
        assertEquals(150, rules.getRoleIncome());
        assertEquals(0, rules.getDay1ParticipationReward());
        assertEquals(5, rules.getDay1CorrectReward());
        assertEquals(0, rules.getDay2ParticipationReward());
        assertEquals(5, rules.getDay2CorrectReward());
        assertEquals(1_000, rules.getIncomeTaxBps());

        assertNotNull(response.roomId());
        verify(sessionRepository).save(any());
    }

    @Test
    void hidesRoomOwnedByAnotherTeacher() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomEntity anotherTeachersRoom = RoomEntity.preparing(
                UUID.randomUUID(),
                "다른 반",
                3
        );
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(anotherTeachersRoom));

        TeacherRoomManagementService service = service();
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.students(teacherId, roomId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("ROOM_NOT_FOUND", exception.getCode());
        verify(studentRepository, never()).findAllByRoomIdOrderByParticipantNumber(roomId);
    }

    @Test
    void returnsStudentPreparationStatusInParticipantOrder() {
        UUID teacherId = UUID.randomUUID();
        RoomEntity room = RoomEntity.preparing(teacherId, "테스트 반", 2);
        List<StudentEntity> students = List.of(
                StudentEntity.invited(room.getId(), 1, "ECO-AAAAA"),
                StudentEntity.invited(room.getId(), 2, "ECO-BBBBB")
        );
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(studentRepository.findAllByRoomIdOrderByParticipantNumber(room.getId()))
                .thenReturn(students);

        var response = service().students(teacherId, room.getId());

        assertEquals(List.of(1, 2), response.students().stream()
                .map(item -> item.participantNumber())
                .toList());
        assertTrue(response.students().stream().allMatch(item -> "INVITED".equals(item.status())));
    }

    private TeacherRoomManagementService service() {
        return new TeacherRoomManagementService(
                roomRepository,
                roomRulesRepository,
                sessionRepository,
                studentRepository,
                quizRepository,
                questionRepository,
                productRepository
        );
    }

    private long count(Iterable<?> values) {
        long count = 0;
        for (Object ignored : values) {
            count += 1;
        }
        return count;
    }

    private <T> List<T> toList(Iterable<T> values) {
        List<T> result = new java.util.ArrayList<>();
        values.forEach(result::add);
        return result;
    }
}
