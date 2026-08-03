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
        UUID approvedByUserId,
        UUID draftBinding
    ) {
        EconomicEventPolicyEffect.EffectKind kind = policyEffect.kind(template);
        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("template_type", template.name());
        applied.put("effect_kind", kind.name());

        if (kind == EconomicEventPolicyEffect.EffectKind.INVESTMENT_NEWS_RELEASE) {
            // 뉴스는 이야기만 게시한다. 가격은 시나리오에 이미 정해져 있어 바꿀 값이 없다.
            requireNoEdits(command, template);
            applied.put("investment_price_step_id", requiredPriceStepId(draftBinding));
            return applied;
        }

        Set<String> editableFields = policyEffect.editableFields(template, scope);
        requireEditsWithinScope(command.edits(), editableFields, template);

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

    /**
     * 초안이 가격 단계에 연결되어 있지 않으면 게시할 대상이 없다.
     */
    private static String requiredPriceStepId(UUID draftBinding) {
        if (draftBinding == null) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_INVESTMENT_STEP_BINDING",
                "The investment news draft is not bound to a revealed price step."
            );
        }
        return draftBinding.toString();
    }

    private static void requireNoEdits(EconomicEventDecisionCommand command, TemplateType template) {
        if (command.edits() != null && !command.edits().isEmpty()) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "FORBIDDEN_EVENT_EFFECT",
                "Template " + template.name() + " does not accept edits."
            );
        }
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
