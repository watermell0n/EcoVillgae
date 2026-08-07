package com.b201.ecovillage.quiz.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 선생님 수업 모듈에서 순서대로 넘겨 보여 줄 자료 한 장이다.
 *
 * <p>{@code imageUrl}은 교사가 올린 데이터 URI일 수도 있고, 저장소에 이미 있는
 * 수업자료 경로(`lessons/...`)일 수도 있다. 회차 자료를 그대로 쓸 때는 경로를 보내
 * 폴링마다 수 MB를 다시 내려받지 않게 한다.
 */
public record ImageLessonSlideRequest(
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 8_000_000) String imageUrl
) {
}
