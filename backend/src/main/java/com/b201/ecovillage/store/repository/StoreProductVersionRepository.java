package com.b201.ecovillage.store.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.store.domain.StoreProductVersion;

public interface StoreProductVersionRepository
    extends JpaRepository<StoreProductVersion, UUID> {

    Optional<StoreProductVersion>
        findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullOrderByVersionDesc(
            UUID classroomId,
            UUID storeProductId
        );

    /**
     * 은퇴 여부를 가리지 않고 최신 버전을 찾는다.
     *
     * <p>V1_27이 판매 종료 캐릭터의 구매를 막으려고 버전을 은퇴시켰는데, 그 결과 교사도
     * 그 상품을 목록에서 볼 수 없고 가격 변경·삭제도 할 수 없게 되었다. 교사 화면과 상품
     * 명령은 이 메서드를 써서 은퇴한 버전이라도 찾는다. 학생 구매 경로는 위의
     * `RetiredAtIsNull` 메서드를 계속 쓰므로 구매 차단은 그대로 유지된다.
     */
    Optional<StoreProductVersion> findTopByClassroomIdAndStoreProductIdOrderByVersionDesc(
        UUID classroomId,
        UUID storeProductId
    );

    Optional<StoreProductVersion>
        findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullAndPublishedAtLessThanEqualOrderByVersionDesc(
            UUID classroomId,
            UUID storeProductId,
            Instant publishedAt
        );

    Optional<StoreProductVersion> findByClassroomIdAndStoreProductIdAndVersion(
        UUID classroomId,
        UUID storeProductId,
        int version
    );
}
