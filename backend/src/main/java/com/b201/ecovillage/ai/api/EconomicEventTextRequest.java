package com.b201.ecovillage.ai.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 교사가 고쳐 쓴 AI 이야기 문안이다.
 *
 * <p>정책 효과와 가격 단계 연결은 담지 않는다. 이 요청으로 바뀌는 것은 교사와 학생이 읽는
 * 제목·본문뿐이다.
 *
 * @param expectedEventVersion 화면이 읽어 둔 이야기 판본이다. 보내면 그 사이 다른 화면에서
 *                             바뀌지 않았는지 함께 검사한다.
 */
public record EconomicEventTextRequest(
    @NotBlank @Size(max = 160) String title,
    @NotBlank @Size(max = 2000) String content,
    @Min(0) Long expectedEventVersion
) {
}
