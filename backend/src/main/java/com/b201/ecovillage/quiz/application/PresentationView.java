package com.b201.ecovillage.quiz.application;

import java.util.UUID;

/**
 * 지금 학생 화면에 걸려 있는 자료 한 장과 그 위치다.
 * 교사 화면이 "3 / 6"을 보여 주고 이전·다음 버튼을 잠그는 데 쓴다.
 */
public record PresentationView(
    UUID activityId,
    String status,
    long stateVersion,
    int page,
    int totalPages,
    String title,
    String imageUrl
) {

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages;
    }
}
