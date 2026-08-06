package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

/**
 * 가져온 문항 식별자와 은행에 남은 문항 수를 함께 돌려준다.
 * 교사 화면이 "더 가져올 문제가 없습니다"를 미리 알려 줄 수 있게 한다.
 */
public record NonsenseImportResult(
    List<UUID> questionIds,
    int remainingCount,
    String bankVersion
) {

    public NonsenseImportResult {
        questionIds = List.copyOf(questionIds);
    }
}
