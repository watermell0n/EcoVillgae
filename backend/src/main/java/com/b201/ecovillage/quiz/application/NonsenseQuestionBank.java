package com.b201.ecovillage.quiz.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.ObjectMapper;

/**
 * 사람이 검토한 넌센스 문항을 담은 읽기 전용 문제은행이다.
 *
 * <p>넌센스는 교사의 학습목표를 평가하지 않고 재미가 목적이라 매번 새로 만들 이유가 없다.
 * AI 생성은 말장난 성립률이 낮아 대신 고정 문항을 뽑아 쓴다.
 * 문항 수가 고정이고 교사가 은행을 늘리지 않으므로 테이블 대신 리소스 파일로 둔다.
 */
@Component
public class NonsenseQuestionBank {

    static final String RESOURCE_PATH = "quiz/nonsense-bank-v1.json";

    private final String bankVersion;
    private final List<NonsenseBankQuestion> questions;

    /**
     * 파일 형식이 웹 계층 Jackson 설정에 끌려다니지 않도록 전용 ObjectMapper로 읽는다.
     * 키 이름은 {@link JsonProperty}로 직접 지정한다.
     */
    public NonsenseQuestionBank() {
        Bank bank = read(RESOURCE_PATH);
        this.bankVersion = bank.bankVersion();
        this.questions = List.copyOf(bank.questions());
    }

    private static Bank read(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream input = resource.getInputStream()) {
            Bank bank = new ObjectMapper().readValue(input, Bank.class);
            if (bank == null || bank.questions() == null || bank.questions().isEmpty()) {
                throw new IllegalStateException("넌센스 문제은행이 비어 있습니다: " + resourcePath);
            }
            return bank;
        } catch (IOException exception) {
            throw new IllegalStateException(
                "넌센스 문제은행을 읽을 수 없습니다: " + resourcePath,
                exception
            );
        }
    }

    public String bankVersion() {
        return bankVersion;
    }

    /**
     * 학년에 맞고 아직 학급에 없는 문항을 무작위로 고른다.
     * 한 번의 뽑기 안에서는 같은 문항이 두 번 나오지 않는다.
     * 남은 문항이 count보다 적으면 있는 만큼만 돌려준다.
     */
    public List<NonsenseBankQuestion> draw(int grade, int count, Set<String> usedQuestions) {
        if (count <= 0) {
            return List.of();
        }
        List<NonsenseBankQuestion> candidates = available(grade, usedQuestions);
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        return List.copyOf(candidates.subList(0, Math.min(count, candidates.size())));
    }

    public int availableCount(int grade, Set<String> usedQuestions) {
        return available(grade, usedQuestions).size();
    }

    private List<NonsenseBankQuestion> available(int grade, Set<String> usedQuestions) {
        List<NonsenseBankQuestion> candidates = new ArrayList<>();
        for (NonsenseBankQuestion question : questions) {
            if (question.fitsGrade(grade) && !usedQuestions.contains(question.question())) {
                candidates.add(question);
            }
        }
        return candidates;
    }

    private record Bank(
        @JsonProperty("bank_version") String bankVersion,
        List<NonsenseBankQuestion> questions
    ) {
    }
}
