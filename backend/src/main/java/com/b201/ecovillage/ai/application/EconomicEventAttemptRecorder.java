package com.b201.ecovillage.ai.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;

/**
 * 승인 실패한 시도를 감사 로그에 남긴다.
 *
 * <p>승인이 실패하면 모든 변경을 되돌리고 초안은 DRAFT로 남겨 재시도할 수 있게 한다. 그런데
 * 같은 트랜잭션에 기록하면 롤백과 함께 사라지므로, 이 기록만 별도 트랜잭션에서 커밋한다.
 * 초안 상태를 FAILED로 고정하지 않고 실패 사유와 시도 이력만 남기는 방식이다.
 */
@Service
public class EconomicEventAttemptRecorder {

    private final AuditTrailService auditTrailService;

    public EconomicEventAttemptRecorder(AuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
        UUID classroomId,
        UUID actorId,
        UUID eventId,
        UUID requestId,
        UUID correlationId,
        String templateType,
        String failureCode,
        String failureMessage,
        Map<String, Object> requestedEdits
    ) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", "DRAFT");
        before.put("template_type", templateType);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", "DRAFT");
        after.put("outcome", "APPLY_FAILED");
        after.put("failure_code", failureCode);
        after.put("failure_message", failureMessage);
        after.put("requested_edits", requestedEdits == null ? Map.of() : Map.copyOf(requestedEdits));
        auditTrailService.record(
            classroomId,
            actorId,
            "AI_ECONOMIC_EVENT_APPLY_FAILED",
            "ECONOMIC_EVENT_DRAFT",
            eventId,
            requestId,
            correlationId,
            before,
            after,
            "AI",
            "경제 이벤트 적용이 실패해 초안을 검토 대기로 되돌렸습니다."
        );
    }
}
