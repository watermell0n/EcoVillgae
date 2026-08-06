package com.b201.ecovillage.quiz.application;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 넌센스 문제은행에 실린 문항 하나다.
 * 사람이 미리 검토한 고정 문항이므로 AI 생성과 달리 내용 검증 단계를 두지 않는다.
 *
 * <p>파일 형식은 전역 Jackson 설정과 무관해야 하므로 이름을 직접 지정한다.
 */
public record NonsenseBankQuestion(
    String id,
    List<Integer> grades,
    String theme,
    @JsonProperty("pun_type") String punType,
    String question,
    List<Choice> choices,
    @JsonProperty("correct_choice_key") String correctChoiceKey,
    String explanation
) {

    public NonsenseBankQuestion {
        grades = List.copyOf(grades);
        choices = List.copyOf(choices);
    }

    public boolean fitsGrade(int grade) {
        return grades.contains(grade);
    }

    public record Choice(String key, String text) {
    }
}
