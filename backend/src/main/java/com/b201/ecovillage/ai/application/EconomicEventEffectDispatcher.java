package com.b201.ecovillage.ai.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.b201.ecovillage.ai.contract.EconomicEventContract.InterestRateScope;
import com.b201.ecovillage.ai.contract.EconomicEventContract.TemplateType;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.application.EconomicEventPolicyApplier;
import com.b201.ecovillage.classroom.application.EconomicPolicyResult;
import com.b201.ecovillage.ledger.application.EconomicEventPayoutCommand;
import com.b201.ecovillage.ledger.application.EconomicEventPayoutResult;
import com.b201.ecovillage.ledger.application.EconomicEventPayoutService;

/**
 * 승인된 경제 이벤트를 템플릿별 도메인 진입점으로 보낸다.
 *
 * <p>AI 쪽은 정책 버전이나 원장 거래를 직접 만들지 않는다. 정책 효과는
 * {@link EconomicEventPolicyApplier}, 화폐가 움직이는 효과는
 * {@link EconomicEventPayoutService}가 담당한다. 두 진입점 모두 호출자의 트랜잭션에
 * 참여하므로 일부만 반영되는 상태가 생기지 않는다.
 */
@Service
public class EconomicEventEffectDispatcher {

    private final EconomicEventPolicyEffect policyEffect;
    private final EconomicEventPolicyApplier policyApplier;
    private final EconomicEventPayoutService payoutService;

    public EconomicEventEffectDispatcher(
        EconomicEventPolicyEffect policyEffect,
        EconomicEventPolicyApplier policyApplier,
        EconomicEventPayoutService payoutService
    ) {
        this.policyEffect = policyEffect;
        this.policyApplier = policyApplier;
        this.payoutService = payoutService;
    }

    /**
     * @return 감사 기록에 남길 적용 결과다. 정책 갈래는 정책 버전, 원장 갈래는 거래 그룹을 담는다.
     */
    public Map<String, Object> apply(
        EconomicEventDecisionCommand command,
        TemplateType template,
        InterestRateScope scope,
        UUID approvedByUserId
    ) {
        EconomicEventPolicyEffect.EffectKind kind = policyEffect.kind(template);
        if (kind == EconomicEventPolicyEffect.EffectKind.UNSUPPORTED) {
            // 투자 뉴스는 공개된 가격 단계에 연결해야 게시할 수 있다. 연결 대상이 없으므로
            // edits를 검사하기 전에 거절한다. S15P11B201-289에서 풀린다.
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_INVESTMENT_STEP_BINDING",
                "INVESTMENT_NEWS needs the investment scenario domain, which is not implemented yet."
            );
        }
        Set<String> editableFields = policyEffect.editableFields(template, scope);
        requireEditsWithinScope(command.edits(), editableFields, template);

        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("template_type", template.name());
        applied.put("effect_kind", kind.name());

        switch (kind) {
            case POLICY -> {
                EconomicPolicyResult result = policyApplier.apply(
                    command.teacherSubject(),
                    command.classroomId(),
                    command.requestId(),
                    command.correlationId(),
                    command.expectedStateVersion(),
                    command.edits(),
                    editableFields
                );
                applied.put("policy_version", result.policyVersion());
                applied.put("policy_version_id", result.policyVersionId().toString());
                applied.put("edits", Map.copyOf(command.edits()));
            }
            case REWARD_ISSUANCE -> {
                EconomicEventPayoutResult result = payoutService.issueReward(
                    payoutCommand(command, template, approvedByUserId)
                );
                putPayout(applied, result);
            }
            case PUBLIC_FUND_GRANT -> {
                EconomicEventPayoutResult result = payoutService.grantPublicFund(
                    payoutCommand(command, template, approvedByUserId)
                );
                putPayout(applied, result);
            }
            default -> throw new IllegalStateException("unreachable effect kind: " + kind);
        }
        return applied;
    }

    private EconomicEventPayoutCommand payoutCommand(
        EconomicEventDecisionCommand command,
        TemplateType template,
        UUID approvedByUserId
    ) {
        String field = policyEffect.amountField(template);
        Object value = command.edits() == null ? null : command.edits().get(field);
        if (!(value instanceof Number number)) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                field + " must be supplied as an integer amount."
            );
        }
        return new EconomicEventPayoutCommand(
            command.classroomId(),
            command.eventId(),
            approvedByUserId,
            command.correlationId(),
            number.longValue(),
            command.reason()
        );
    }

    private static void putPayout(Map<String, Object> applied, EconomicEventPayoutResult result) {
        applied.put("transaction_group_id", result.transactionGroupId().toString());
        applied.put("total_amount", result.totalAmount());
        applied.put("recipient_count", result.recipientCount());
        applied.put("replayed", result.replayed());
    }

    private static void requireEditsWithinScope(
        Map<String, Object> edits,
        Set<String> editableFields,
        TemplateType template
    ) {
        if (edits == null || edits.isEmpty()) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                "Template " + template.name() + " requires edits for " + editableFields + "."
            );
        }
        for (String field : edits.keySet()) {
            if (!editableFields.contains(field)) {
                throw new ClassroomOperationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "FORBIDDEN_EVENT_EFFECT",
                    "Template " + template.name() + " cannot change " + field + "."
                );
            }
        }
    }
}
