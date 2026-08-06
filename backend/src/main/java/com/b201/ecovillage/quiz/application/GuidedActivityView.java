package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record GuidedActivityView(
    UUID activityId,
    String status,
    long stateVersion,
    long participantCount,
    String activityType,
    String presentationTitle,
    String presentationImageUrl,
    // 자료가 여러 장인 수업의 현재 위치입니다. 한 장뿐이면 둘 다 1입니다.
    int presentationPage,
    int presentationTotalPages
) {
}
