package com.b201.ecovillage.classroom.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;

/**
 * 승인된 경제 이벤트가 경제 정책을 바꾸는 진입점이다.
 *
 * <p>AI 승인 서비스는 이 진입점만 호출하고 정책 버전이나 스냅샷을 직접 만들지 않는다.
 * 실제 변경은 같은 패키지의 {@link EconomicPolicyCommandService}가 수행하므로 허용 범위
 * 검사, 낙관적 잠금, 감사, 정책 스냅샷이 모두 소유자 쪽에 남는다.
 *
 * <p>TAX·금리·가격·재고 변경은 원장 거래를 만들지 않는다. 앞으로의 거래에만 적용되고 개인
 * 잔액을 건드리지 않기 때문이며, 이력은 감사 로그로 남는다.
 */
@Service
public class EconomicEventPolicyApplier {

    static final String POLICY_AREA = "ECONOMIC";

    private final EconomicPolicyCommandService economicPolicyCommandService;
    private final ClassroomPolicyVersionRepository policyRepository;

    public EconomicEventPolicyApplier(
        EconomicPolicyCommandService economicPolicyCommandService,
        ClassroomPolicyVersionRepository policyRepository
    ) {
        this.economicPolicyCommandService = economicPolicyCommandService;
        this.policyRepository = policyRepository;
    }

    /**
     * 교사가 확정한 값만 현재 ECONOMIC 정책 위에 덮어써서 새 버전을 만든다.
     *
     * @param allowedFields 이 이벤트 템플릿이 바꿀 수 있는 항목이다. 그 밖의 값이 오면 거절한다.
     */
    @Transactional
    public EconomicPolicyResult apply(
        String teacherSubject,
        UUID classroomId,
        UUID requestId,
        UUID correlationId,
        Long expectedStateVersion,
        Map<String, Object> edits,
        Set<String> allowedFields
    ) {
        if (edits == null || edits.isEmpty()) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                "The economic event needs confirmed policy values for " + allowedFields + "."
            );
        }
        for (String field : edits.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new ClassroomOperationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "FORBIDDEN_EVENT_EFFECT",
                    "This economic event cannot change " + field + "."
                );
            }
        }

        ClassroomPolicyVersion current = policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, POLICY_AREA)
            .orElse(null);
        Map<String, Object> values = new LinkedHashMap<>(
            current == null ? Map.of() : current.getPolicyValues()
        );
        values.putAll(edits);

        return economicPolicyCommandService.update(new EconomicPolicyCommand(
            teacherSubject,
            classroomId,
            requestId,
            correlationId,
            expectedStateVersion,
            scalar(values, "price_adjustment_bps"),
            scalar(values, "inflation_rate_bps"),
            scalar(values, "income_tax_rate_bps"),
            scalar(values, "vat_rate_bps"),
            scalar(values, "trade_tax_rate_bps"),
            scalar(values, "deposit_rate_bps"),
            scalar(values, "loan_rate_bps"),
            tree(values, "stock_policy"),
            tree(values, "reward_policy"),
            tree(values, "limits"),
            current == null ? 0 : current.getVersion()
        ));
    }

    private static int scalar(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new ClassroomOperationException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "VALIDATION_ERROR",
            key + " must be an integer."
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tree(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ClassroomOperationException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "VALIDATION_ERROR",
            key + " must be an object."
        );
    }
}
