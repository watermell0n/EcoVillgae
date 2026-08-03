package com.b201.ecovillage.ai.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuizDraftContract {

    public static final String SCHEMA_VERSION = "1.2";
    public static final String PROMPT_VERSION = "quiz-draft-v1";
    public static final String LOCALE = "ko-KR";

    private QuizDraftContract() {
    }

    public record Request(
        UUID aiRunId,
        UUID requestId,
        UUID correlationId,
        String schemaVersion,
        String promptVersion,
        int attempt,
        Instant deadlineAt,
        String locale,
        int grade,
        Payload payload
    ) {
        public Request withAttempt(int value) {
            return new Request(
                aiRunId,
                requestId,
                correlationId,
                schemaVersion,
                promptVersion,
                value,
                deadlineAt,
                locale,
                grade,
                payload
            );
        }
    }

    public record Payload(
        String topic,
        Difficulty difficulty,
        List<String> concepts,
        List<String> learningObjectives,
        Category category,
        QuestionType questionType,
        int count
    ) {
        public Payload {
            concepts = List.copyOf(concepts);
            learningObjectives = List.copyOf(learningObjectives);
        }
    }

    public record Response(
        UUID aiRunId,
        String schemaVersion,
        String promptVersion,
        String provider,
        String modelId,
        String safetyStatus,
        List<String> validationCodes,
        Usage usage,
        Output output
    ) {
        public Response {
            validationCodes = List.copyOf(validationCodes);
        }
    }

    public record Usage(int inputTokens, int outputTokens) {
    }

    public record Output(
        String reviewStatus,
        Category category,
        List<QuestionDraft> questions
    ) {
        public Output {
            questions = List.copyOf(questions);
        }
    }

    public record QuestionDraft(
        String reviewStatus,
        QuestionType questionType,
        String question,
        List<Choice> choices,
        String correctChoiceKey,
        String explanation,
        Difficulty difficulty,
        List<String> conceptTags
    ) {
        public QuestionDraft {
            choices = List.copyOf(choices);
            conceptTags = List.copyOf(conceptTags);
        }
    }

    public record Choice(String choiceKey, String text) {
    }

    public enum Category {
        ECONOMY,
        NONSENSE
    }

    public enum QuestionType {
        MULTIPLE_CHOICE,
        OX
    }

    public enum Difficulty {
        INTRODUCTORY,
        STANDARD,
        ADVANCED
    }
}
