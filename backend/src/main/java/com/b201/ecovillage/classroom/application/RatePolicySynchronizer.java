package com.b201.ecovillage.classroom.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;

/**
 * 금리·세율을 두 곳에서 관리하기 때문에 필요한 동기화입니다.
 *
 * <p>운영 관리 화면은 예금·적금 상품과 대출 정책에 이율을 직접 저장하고, AI 설정 화면은 모듈의
 * 확정 정책(DEPOSIT_RATE·SAVINGS_RATE·LOAN_RATE)을 읽어 "적용된 기준금리"로 보여 줍니다.
 * 한쪽만 바꾸면 같은 학급에서 두 화면의 숫자가 달라지므로, 운영 관리에서 이율을 저장할 때
 * 모듈 정책도 같은 값으로 맞춥니다.
 *
 * <p>모듈을 아직 활성화하지 않아 정책이 없으면 만들지 않습니다. 초기 정책 확정은 모듈 활성화
 * 흐름이 담당하며, 여기서 미리 만들면 그 검증을 건너뛰게 됩니다.
 */
@Component
public class RatePolicySynchronizer {

    private final ClassroomPolicyVersionRepository policyRepository;
    private final UuidV7Generator uuidGenerator;

    public RatePolicySynchronizer(
        ClassroomPolicyVersionRepository policyRepository,
        UuidV7Generator uuidGenerator
    ) {
        this.policyRepository = policyRepository;
        this.uuidGenerator = uuidGenerator;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(UUID classroomId, String policyArea, int rateBps, Instant now) {
        ClassroomPolicyVersion current = policyRepository
            .findForUpdateByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, policyArea)
            .orElse(null);
        if (current == null) {
            return;
        }
        Object rate = current.getPolicyValues().get("rate_bps");
        if (rate instanceof Number number && number.intValue() == rateBps) {
            return;
        }
        current.supersede(now);
        policyRepository.flush();
        policyRepository.save(ClassroomPolicyVersion.policy(
            uuidGenerator.generate(),
            classroomId,
            policyArea,
            current.getVersion() + 1,
            Map.of("rate_bps", rateBps),
            "TEACHER",
            now
        ));
        policyRepository.flush();
    }
}
