package com.b201.ecovillage.ai.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;

/**
 * 학급 AI 설정을 읽는다. 설정은 별도 테이블이 아니라 AI_SETTINGS 정책 버전으로 보관한다.
 * 설정이 없으면 AI를 사용하는 상태로 본다.
 */
@Component
public class ClassroomAiSettings {

    public static final String POLICY_AREA = "AI_SETTINGS";
    public static final String ENABLED_KEY = "enabled";

    private final ClassroomPolicyVersionRepository policyRepository;

    public ClassroomAiSettings(ClassroomPolicyVersionRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public boolean enabled(UUID classroomId) {
        return current(classroomId)
            .map(policy -> !Boolean.FALSE.equals(readEnabled(policy)))
            .orElse(true);
    }

    public java.util.Optional<ClassroomPolicyVersion> current(UUID classroomId) {
        return policyRepository.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
            classroomId,
            POLICY_AREA
        );
    }

    private static Boolean readEnabled(ClassroomPolicyVersion policy) {
        Map<String, Object> values = policy.getPolicyValues();
        if (values == null) {
            return null;
        }
        Object value = values.get(ENABLED_KEY);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return Boolean.valueOf(text);
        }
        return null;
    }
}
