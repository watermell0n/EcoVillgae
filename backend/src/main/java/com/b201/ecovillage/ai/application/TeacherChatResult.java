package com.b201.ecovillage.ai.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeacherChatResult(
    UUID aiRunId,
    String answer,
    Instant periodFrom,
    Instant periodTo,
    Instant dataAsOf,
    List<Evidence> evidence,
    List<Recommendation> recommendations,
    boolean fallbackUsed,
    String fallbackCode
) {
    public TeacherChatResult {
        evidence = List.copyOf(evidence);
        recommendations = List.copyOf(recommendations);
    }

    public record Evidence(String factId, String label, Object value, String unit) {
    }

    public record Recommendation(String text, List<String> sideEffects) {
        public Recommendation {
            sideEffects = List.copyOf(sideEffects);
        }
    }
}
