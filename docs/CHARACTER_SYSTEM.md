# 캐릭터 시스템 (코드 기준)

> 갱신 기준: 2026-08-06
> 이 문서는 과거 기획안이 아니라 현재 애플리케이션 코드와 마이그레이션을 기준으로 다시 작성했다.

## 기준 소스

- 앱 카탈로그: `student-app/expo/src/features/character/core/catalog.ts`
- 슬롯·DTO: `student-app/expo/src/features/character/core/types.ts`
- 실제 합성 순서: `student-app/expo/src/features/character/core/layerOrder.ts`
- 스프라이트 연결: `student-app/expo/src/features/character/assetMap.ts`
- 백엔드 슬롯 판정: `backend/src/main/java/com/b201/ecovillage/character/domain/CharacterSlot.java`
- 장착 처리: `backend/src/main/java/com/b201/ecovillage/character/application/StudentCharacterProfileService.java`
- DB 구성: `V1_17`, `V1_18`, `V1_24`~`V1_27` 캐릭터 마이그레이션

## 현재 구조

캐릭터는 한 장의 완성 이미지가 아니라 슬롯별 PNG 레이어를 합성한다. 화면 합성 순서는 아래와 같다.

1. `BACKGROUND`
2. `EFFECT`
3. `BACK`
4. `BOTTOM`
5. `SHOES`
6. `TOP`
7. `NECK`
8. `FACE`
9. `HAND`
10. `HAT`

`CharacterSlot.fromCharacterCode()`는 `hat_red_cap`처럼 첫 번째 밑줄 앞의 접두사를 슬롯으로 해석한다. 배경만 `bg_*`를 `BACKGROUND`로 변환한다. 새 아이템 코드는 반드시 이 규칙을 따라야 한다.

## 카탈로그와 판매 범위

앱 카탈로그에는 현재 64개 스프라이트가 등록되어 있으며 테마는 `BASIC`, `SEASONAL`, `SPORTS`, `CAREER`, `FANTASY`, `EVENT`를 사용한다. 화면 이름이나 가격을 추가할 때는 `catalog.ts`를, 실제 이미지를 추가할 때는 `assetMap.ts`를 함께 수정해야 한다.

카탈로그에 존재하는 것과 상점에서 판매되는 것은 다르다. 이미 구매한 아이템의 렌더링을 깨뜨리지 않기 위해 판매 종료 아이템도 앱 카탈로그와 에셋 맵에는 남긴다. 현재 판매 제한 목록은 `OFF_SALE_SPRITE_KEYS`가 표현하며, 서버의 `PUBLISHED`/`purchasable` 상태가 최종 판매 가능 여부를 결정한다.

`BOTTOM`은 독립 판매 목록이 비어 있다. 일부 전신 상의가 하의 영역을 함께 덮는 합성 규칙을 사용하기 때문이다.

## 서버 데이터 모델

- `character_catalog`: 전역 캐릭터 코드와 승인 상태
- `classroom_characters`: 교실별 판매 구성과 상점 상품 연결
- `student_character_profiles`: 학생별 캐릭터 프로필과 낙관적 잠금 버전
- `character_equipment`: 프로필의 슬롯별 장착 인벤토리
- `inventory_items`: 학생이 실제 소유한 캐릭터 아이템
- `store_products` / `store_product_versions`: 판매 상태, 가격, 버전

장착 저장은 `PUT /classrooms/{classroom_id}/students/me/character-profile/equipment`에서 전체 슬롯 상태를 한 번에 교체한다. 서버는 다음을 모두 검증한다.

- 요청 학생이 해당 인벤토리를 소유하는지
- 아이템 상태가 `AVAILABLE`인지
- 캐릭터 코드의 슬롯과 요청 슬롯이 일치하는지
- 같은 인벤토리 아이템을 여러 슬롯에 중복 장착하지 않았는지
- `expected_state_version`이 최신인지

조회는 `GET /classrooms/{classroom_id}/students/me/character-profile`, 보유 목록은 `GET /classrooms/{classroom_id}/students/me/inventory`를 사용한다.

## 아이템 추가 체크리스트

1. 슬롯 접두사를 지킨 `spriteKey`를 정한다. 배경은 `bg_`, 나머지는 슬롯 이름의 소문자를 사용한다.
2. 투명 배경 PNG를 에셋 디렉터리에 추가한다.
3. `assetMap.ts`에 정적 `require` 연결을 추가한다.
4. `catalog.ts`에 이름, 슬롯, 가격, 테마, 표시 순서를 추가한다.
5. 서버 `character_catalog`와 교실 판매 구성용 마이그레이션을 추가한다.
6. 판매할 아이템만 상점 상품을 `PUBLISHED` 및 구매 가능 버전으로 만든다.
7. 앱 합성, 상점 미리보기, 구매 후 장착, 장착 해제까지 확인한다.

## 변경 시 지켜야 할 불변 조건

- 과거 구매 아이템의 `character_code`와 `spriteKey`를 재사용하거나 의미를 바꾸지 않는다.
- 판매 종료는 에셋 삭제가 아니라 서버 판매 중지로 처리한다.
- 슬롯 순서는 `layerOrder.ts`가 단일 기준이며 DTO의 참고용 순서와 어긋나지 않게 유지한다.
- 상품 가격 변경은 새 `store_product_versions`를 만들고 과거 주문이 참조한 버전은 보존한다.
- 장착 상태 변경은 부분 PATCH가 아니라 프로필 단위 전체 교체와 `state_version` 검증을 유지한다.
