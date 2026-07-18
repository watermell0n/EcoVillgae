package com.b201.bibim.feature.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.ModuleStateRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.feature.learning.LearningDto.GuidedLearningResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private DaySessionRepository sessionRepository;

    @Mock
    private ModuleStateRepository moduleStateRepository;

    @Mock
    private StudentFlowService studentFlowService;

    @Mock
    private RoomEntity room;

    @Mock
    private DaySessionEntity session;

    private LearningService service;
    private UUID teacherId;
    private UUID roomId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        service = new LearningService(
                roomRepository,
                studentRepository,
                sessionRepository,
                moduleStateRepository,
                studentFlowService
        );
        teacherId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getSyncVersion()).thenReturn(7L);
        lenient().when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(session));
        when(session.getId()).thenReturn(sessionId);
        when(moduleStateRepository.findBySessionIdAndModuleType(sessionId, "KNOWLEDGE"))
                .thenReturn(Optional.empty());
    }

    @Test
    void returnsThreePayslipLearningPagesInTheRequestedOrder() {
        when(room.getCurrentStage()).thenReturn("DAY1_KNOWLEDGE_GUIDED");

        GuidedLearningResponse response = service.teacherCurrent(teacherId, roomId);

        assertEquals(3, response.pages().size());
        assertEquals(
                List.of("월급과 실수령액", "세금이란?", "세금의 활용"),
                response.pages().stream().map(LearningDto.PageResponse::title).toList()
        );
        assertTrue(response.pages().stream().allMatch(page -> !page.example().isBlank()));
    }

    @Test
    void returnsFiveFinancialLearningPagesInTheRequestedOrder() {
        when(room.getCurrentStage()).thenReturn("DAY1_FINANCE_GUIDED");

        GuidedLearningResponse response = service.teacherCurrent(teacherId, roomId);

        assertEquals(5, response.pages().size());
        assertEquals(
                List.of("소비와 저축", "예금이란?", "이자란?", "투자란?", "위험과 분산투자"),
                response.pages().stream().map(LearningDto.PageResponse::title).toList()
        );
        assertTrue(response.pages().stream().allMatch(page -> !page.icon().isBlank()));
    }

    @Test
    void returnsFinancialTaxBeforeInflationForDayTwo() {
        when(room.getCurrentStage()).thenReturn("DAY2_TAX_INFLATION_GUIDED");
        when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 2))
                .thenReturn(Optional.of(session));

        GuidedLearningResponse response = service.teacherCurrent(teacherId, roomId);

        assertEquals(2, response.pages().size());
        assertEquals(
                List.of("금융세가 뭐예요?", "인플레이션이 뭐예요?"),
                response.pages().stream().map(LearningDto.PageResponse::title).toList()
        );
        assertEquals(
                "어제 사탕 1개 10비빔 → 오늘 12비빔\n같은 돈으로는 전보다 덜 살 수 있어요",
                response.pages().get(1).example()
        );
    }
}
