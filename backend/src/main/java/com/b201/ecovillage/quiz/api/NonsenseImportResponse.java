package com.b201.ecovillage.quiz.api;

import java.util.List;
import java.util.UUID;

/**
 * 가져온 문항 식별자와 은행에 남은 문항 수를 함께 반환한다.
 */
public record NonsenseImportResponse(
    List<UUID> questionIds,
    int remainingCount,
    String bankVersion
) {

    public NonsenseImportResponse {
        questionIds = List.copyOf(questionIds);
    }
}
