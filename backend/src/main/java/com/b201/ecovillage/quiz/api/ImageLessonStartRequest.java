package com.b201.ecovillage.quiz.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * 선생님 수업 모듈을 여는 요청이다.
 *
 * <p>두 가지 형태를 받는다. 교사가 이미지 한 장을 올려 띄우면 {@code title}·{@code imageUrl}만
 * 오고, 회차 자료를 순서대로 넘기려면 {@code slides}가 온다. 기존 한 장 띄우기 화면을
 * 그대로 쓰기 위해 두 형태를 함께 남겼고, 어느 쪽이 왔는지는 {@link #resolvedSlides()}가
 * 한 가지 모양으로 정리한다.
 */
public record ImageLessonStartRequest(
    // 다른 명령 요청과 같은 감사 필드입니다. fail-on-unknown-properties가 켜져 있어
    // 화면이 보내는 필드를 모두 받아야 합니다.
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,

    @Size(max = 120) String title,
    @Size(max = 8_000_000) String imageUrl,
    @Valid @Size(max = 60) List<ImageLessonSlideRequest> slides
) {

    /**
     * 자료를 항상 "순서 있는 목록"으로 정리해서 돌려준다.
     * 한 장 띄우기도 1장짜리 목록으로 다루면 서비스가 두 경로를 따로 처리하지 않는다.
     */
    public List<ImageLessonSlideRequest> resolvedSlides() {
        if (slides != null && !slides.isEmpty()) {
            return slides;
        }
        if (title == null || title.isBlank() || imageUrl == null || imageUrl.isBlank()) {
            return List.of();
        }
        return List.of(new ImageLessonSlideRequest(title, imageUrl));
    }
}
