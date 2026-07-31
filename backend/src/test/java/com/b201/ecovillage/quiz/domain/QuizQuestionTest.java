package com.b201.ecovillage.quiz.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class QuizQuestionTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void followsReviewPublishAndRetireLifecycle() {
        QuizQuestion question = QuizQuestion.draft(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            QuizContentType.ECONOMY,
            "Why do prices change?",
            NOW
        );

        question.approve(NOW.plusSeconds(1));
        question.publish(NOW.plusSeconds(2));
        question.retire(NOW.plusSeconds(3));

        assertEquals(QuizQuestionStatus.RETIRED, question.getStatus());
        assertThrows(
            IllegalStateException.class,
            () -> question.revise(
                QuizContentType.NONSENSE,
                "new question",
                NOW.plusSeconds(4)
            )
        );
    }

    @Test
    void questionVersionCannotPublishBeforeApproval() {
        QuizQuestionVersion version = QuizQuestionVersion.teacherDraft(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            QuizContentType.ECONOMY,
            QuizQuestionType.MULTIPLE_CHOICE,
            "Question",
            "Explanation",
            NOW
        );

        assertThrows(
            IllegalStateException.class,
            () -> version.publish(NOW.plusSeconds(1))
        );
    }
}
