package com.b201.bibim.feature.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizQuestionEntity;
import com.b201.bibim.domain.quiz.QuizQuestionRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.settlement.SettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.student.StudentFlowStateRepository;
import com.b201.bibim.domain.student.StudentRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Day2QuizRewardServiceTest {

    @Mock QuizRepository quizRepository;
    @Mock QuizQuestionRepository questionRepository;
    @Mock QuizSubmissionRepository submissionRepository;
    @Mock RoomRepository roomRepository;
    @Mock RoomRulesRepository rulesRepository;
    @Mock DaySessionRepository sessionRepository;
    @Mock StudentRepository studentRepository;
    @Mock StudentFlowStateRepository flowStateRepository;
    @Mock SettlementRunRepository runRepository;
    @Mock SettlementItemRepository itemRepository;
    @Mock AccountRepository accountRepository;
    @Mock FinancialTransactionRepository transactionRepository;
    @Mock FinancialEntryRepository entryRepository;

    @InjectMocks Day2QuizRewardService service;

    @Test
    void createsTenQuestionEconomyQuizWithAnswersAndExplanations() {
        UUID roomId = UUID.randomUUID();
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 2))
                .thenReturn(Optional.empty());
        when(quizRepository.saveAndFlush(any(QuizEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureQuiz(roomId);

        ArgumentCaptor<QuizEntity> quizCaptor = ArgumentCaptor.forClass(QuizEntity.class);
        verify(quizRepository).saveAndFlush(quizCaptor.capture());
        assertEquals("경제 퀴즈", quizCaptor.getValue().getTitle());
        assertEquals(10, quizCaptor.getValue().getQuestionCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<QuizQuestionEntity>> questionCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(questionRepository).saveAll(questionCaptor.capture());
        List<QuizQuestionEntity> questions = toList(questionCaptor.getValue());

        assertEquals(10, questions.size());
        assertEquals("내가 일해서 받는 돈을 무엇이라고 할까요?",
                questions.getFirst().getPrompt());
        assertEquals(List.of("B", "C", "B", "D", "D", "A", "C", "B", "B", "C"),
                questions.stream().map(QuizQuestionEntity::getCorrectAnswer).toList());
        assertEquals("반 전체가 사용할 공용 물품을 산다.",
                questions.getLast().getOptions().get(2).text());
        assertFalse(questions.stream()
                .anyMatch(question -> question.getExplanation() == null
                        || question.getExplanation().isBlank()));
    }

    private <T> List<T> toList(Iterable<T> values) {
        List<T> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }
}
