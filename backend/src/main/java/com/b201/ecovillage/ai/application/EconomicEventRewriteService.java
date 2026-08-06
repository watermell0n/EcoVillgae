package com.b201.ecovillage.ai.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.domain.EconomicEventDraft;
import com.b201.ecovillage.ai.repository.EconomicEventDraftRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;

/**
 * 교사가 AI 이야기 초안의 제목과 본문을 고쳐 쓴다.
 *
 * <p>AI가 쓴 문장에는 초등학생이 읽기 어려운 낱말이 섞일 수 있다. 다시 생성하면 내용 전체가
 * 바뀌므로, 마음에 드는 이야기의 어려운 부분만 손볼 수 있는 길을 따로 둔다. 승인 전 초안만
 * 고칠 수 있고, 정책 효과나 가격 단계 연결은 건드리지 않는다.
 */
@Service
public class EconomicEventRewriteService {

    private final ClassroomAuthorizationService authorizationService;
    private final EconomicEventDraftRepository draftRepository;
    private final Clock clock;

    public EconomicEventRewriteService(
        ClassroomAuthorizationService authorizationService,
        EconomicEventDraftRepository draftRepository,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.draftRepository = draftRepository;
        this.clock = clock;
    }

    @Transactional
    public EconomicEventTextResult rewrite(
        String teacherSubject,
        UUID classroomId,
        UUID eventId,
        String title,
        String content,
        Long expectedEventVersion
    ) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.DECIDE_AI_EVENT_DRAFT,
            null
        );
        EconomicEventDraft draft = draftRepository.findByClassroomIdAndId(classroomId, eventId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "이야기 초안을 찾을 수 없습니다."
            ));
        if (expectedEventVersion != null && expectedEventVersion != draft.getStateVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "다른 화면에서 이야기가 바뀌었습니다. 새로고침한 뒤 다시 고쳐 주세요."
            );
        }
        if (!draft.isUnderReview()) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "이미 승인하거나 거절한 이야기는 고칠 수 없습니다."
            );
        }
        try {
            draft.rewrite(title, content, clock.instant());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw operation(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "제목과 이야기를 모두 입력해 주세요."
            );
        }
        draftRepository.save(draft);
        draftRepository.flush();
        return new EconomicEventTextResult(
            draft.getId(), draft.getTitle(), draft.getContent(), draft.getStatus(), draft.getStateVersion()
        );
    }

    /** 고쳐 쓴 결과다. 화면은 state_version을 다음 요청의 기대 버전으로 그대로 쓴다. */
    public record EconomicEventTextResult(
        UUID eventId,
        String title,
        String content,
        String status,
        long stateVersion
    ) {
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
