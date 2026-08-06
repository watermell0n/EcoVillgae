package com.b201.ecovillage.quiz.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class NonsenseQuestionBankTest {

    private static NonsenseQuestionBank bank() {
        return new NonsenseQuestionBank();
    }

    @Test
    void 은행을_읽고_모든_문항이_객관식_계약을_지킨다() {
        NonsenseQuestionBank bank = bank();

        List<NonsenseBankQuestion> all = bank.draw(6, 100, Set.of());

        assertFalse(all.isEmpty());
        assertEquals("nonsense-bank-v1", bank.bankVersion());
        for (NonsenseBankQuestion question : all) {
            List<String> keys = question.choices().stream()
                .map(NonsenseBankQuestion.Choice::key)
                .toList();
            assertTrue(keys.size() >= 3 && keys.size() <= 5, question.id());
            assertEquals(keys.size(), Set.copyOf(keys).size(), question.id());
            assertTrue(keys.contains(question.correctChoiceKey()), question.id());
            assertFalse(question.question().isBlank(), question.id());
            assertFalse(question.explanation().isBlank(), question.id());
        }
    }

    @Test
    void 한_번의_뽑기에서는_같은_문항이_두_번_나오지_않는다() {
        NonsenseQuestionBank bank = bank();

        for (int attempt = 0; attempt < 50; attempt++) {
            List<NonsenseBankQuestion> drawn = bank.draw(5, 10, Set.of());

            assertEquals(10, drawn.size());
            Set<String> ids = drawn.stream()
                .map(NonsenseBankQuestion::id)
                .collect(Collectors.toSet());
            assertEquals(drawn.size(), ids.size());
        }
    }

    @Test
    void 학년에_맞지_않는_문항은_뽑지_않는다() {
        NonsenseQuestionBank bank = bank();

        List<NonsenseBankQuestion> drawn = bank.draw(4, 100, Set.of());

        assertFalse(drawn.isEmpty());
        assertTrue(drawn.stream().allMatch(question -> question.fitsGrade(4)));
    }

    @Test
    void 이미_가져간_문항은_다시_뽑지_않는다() {
        NonsenseQuestionBank bank = bank();
        List<NonsenseBankQuestion> first = bank.draw(6, 5, Set.of());
        Set<String> used = first.stream()
            .map(NonsenseBankQuestion::question)
            .collect(Collectors.toCollection(HashSet::new));

        List<NonsenseBankQuestion> second = bank.draw(6, 100, used);

        assertTrue(second.stream().noneMatch(question -> used.contains(question.question())));
        assertEquals(bank.availableCount(6, used), second.size());
    }

    @Test
    void 남은_문항보다_많이_요청하면_있는_만큼만_돌려준다() {
        NonsenseQuestionBank bank = bank();
        Set<String> used = bank.draw(4, 100, Set.of()).stream()
            .map(NonsenseBankQuestion::question)
            .collect(Collectors.toCollection(HashSet::new));

        assertEquals(0, bank.availableCount(4, used));
        assertTrue(bank.draw(4, 3, used).isEmpty());
    }
}
