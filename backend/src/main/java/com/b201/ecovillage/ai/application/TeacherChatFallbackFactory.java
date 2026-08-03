package com.b201.ecovillage.ai.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.TeacherChatContract;

/**
 * AI 설명 연결이 끊겼거나 근거가 부족할 때 사용할 제한 답변을 만든다.
 * 제한 답변에는 근거·원인·대응안을 넣지 않는다는 계약을 지킨다.
 */
@Component
public class TeacherChatFallbackFactory {

    public TeacherChatContract.Response create(
        TeacherChatContract.Request request,
        String failureCode,
        String answer
    ) {
        List<String> validationCodes = new ArrayList<>();
        validationCodes.add("SPRING_CHAT_FALLBACK_USED");
        validationCodes.add(failureCode);
        return new TeacherChatContract.Response(
            request.aiRunId(),
            TeacherChatContract.SCHEMA_VERSION,
            TeacherChatContract.PROMPT_VERSION,
            "SPRING_FALLBACK",
            "deterministic-chat-fallback",
            "PASSED",
            validationCodes,
            new TeacherChatContract.Usage(0, 0),
            new TeacherChatContract.Output(
                TeacherChatContract.ResponseMode.LIMITED,
                answer,
                List.of(),
                List.of(),
                List.of(),
                List.of(TeacherChatContract.LimitationCode.INSUFFICIENT_EVIDENCE),
                request.payload().period(),
                request.payload().period().dataAsOf()
            )
        );
    }

    public TeacherChatContract.Response unavailable(
        TeacherChatContract.Request request,
        String failureCode
    ) {
        return create(
            request,
            failureCode,
            "AI 설명 연결에 실패해 답변을 만들지 못했습니다. 학급 지표는 운영 현황 화면에서 확인해 주세요."
        );
    }

    public TeacherChatContract.Response disabled(TeacherChatContract.Request request) {
        return create(
            request,
            "AI_DISABLED_FOR_CLASSROOM",
            "이 학급은 AI 기능을 꺼 두었습니다. 학급 지표는 운영 현황 화면에서 확인해 주세요."
        );
    }
}
