# 캐릭터 상점 개편 기록

- 작업일: 2026-08-05
- 브랜치: `feat/pixel`
- 관련 규격: [도트 캐릭터 이미지 작업 규격 v2.0](../design/CHARACTER_ASSET_SPEC.md)

## 1. 무엇이 문제였나

상점에 상품이 10종만 보였다. 원인은 그리드가 아니라 **필터**였다.

```tsx
const readyItems = useMemo(() => items.filter(hasReadyAsset), [items]);
```

`hasReadyAsset`은 64×64 에셋이 등록된 상품만 통과시켰고, 상품 목록·미리보기·착용·
카테고리·선택 상세가 전부 이 배열에서 파생됐다. 그래서 그리드 개수만 늘려도 해결되지
않는 구조였다.

필터를 그냥 지우면 상점에는 64종이 보이지만 착용해도 아무 변화가 없다. 그래서
**에셋을 먼저 채우고 필터를 지웠다.**

## 2. 한 일

### 2-1. 렌더 — 54종 에셋 제작

`scripts/character/generate-items.js`가 미배포 54종의 64×64 PNG를 만든다. 손으로 그리지
않고 코드로 생성한 이유와 방식은 [규격 §11 4단계](../design/CHARACTER_ASSET_SPEC.md)에 있다.

카탈로그 64종 전부 `core/items.ts`의 `CHARACTER_ITEMS`에 등록되어
`PENDING_ASSET_KEYS`가 비었다. 웹·Expo가 같은 `planCharacterLayers`를 쓰므로 두 플랫폼의
레이어 순서가 어긋날 수 없다.

렌더 누락은 더 이상 조용히 넘어가지 않는다. 이전 Expo `CharacterStage`는
`.filter(source => source !== null)`로 해석 못 한 레이어를 버렸다. 지금은
`reportMissingCharacterAsset`으로 알리고, BASE를 못 읽으면 §3.1 좌표로 폴백 실루엣을
그린다.

### 2-2. 상품 데이터 — 설명과 노출 순서

`V1_22__character_store_descriptions_and_order.sql`이 64종의 `description`과
`sort_order`를 채운다. 프론트에 공통 문구를 하드코딩하지 않는다.

`sort_order`는 상점 '추천' 첫 화면에 기본 코디 한 벌(모자·상의·하의·신발·배경·등장식)이
오도록 앞쪽에 두고, 이후 계절 → 스포츠 → 직업 → 판타지로 묶는다.

### 2-3. 백엔드 — 카탈로그 응답과 일괄 저장 API

- `GET /store/catalog` 응답에 `description`·`sort_order` 추가
- `PUT /classrooms/{C}/students/me/character-profile/equipment` 신설 (API-ITM-002)

일괄 저장은 프로필 단위 낙관적 락으로 한 트랜잭션에서 전체 슬롯을 교체한다. 슬롯별 단건
명령을 반복하면 6슬롯 저장에 최대 6번의 요청이 나가고 중간 실패 시 일부만 저장된 상태가
남는다. 기존 단건 API는 보관함의 즉시 착용용으로 유지한다.

삭제를 먼저 flush한 뒤 삽입한다. `character_equipment`에 `inventory_item_id` 유니크
제약이 있고 `slot`이 `updatable = false`라, 같은 아이템이 다른 슬롯으로 옮겨갈 때 삭제와
삽입을 한 flush에 섞으면 유니크 위반이 난다.

### 2-4. 프론트 — 구매와 착용 분리

```text
구매하기       → 즉시 서버 확정 (재화 차감·인벤토리 추가). 자동 착용하지 않는다
카드 클릭      → 보유 상품이면 드래프트 착용, 미보유면 상세 선택만
원래 모습으로  → 드래프트 폐기 (서버 요청 없음)
저장된 모습 유지 → 드래프트를 일괄 저장 API로 확정
```

저장 실패 시 드래프트를 유지한다. `409 STATE_VERSION_CONFLICT`면 최신 프로필을 다시
읽고 사용자에게 재확인을 요청한다 — 자동 병합은 하지 않는다.

카드에는 구매 버튼을 두지 않는다. 카드 클릭과 결제가 이어지면 실수로 재화를 쓴다.
구매는 좌측 상세 카드에서만 일어난다.

## 3. 결정과 근거

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 상품 카드 배율 | 3 → **2** (192 → 128px) | 배율 3이면 6열에 1152px가 필요해 어떤 화면에서도 6열이 안 된다. 규격 §1.1이 카드 썸네일을 배율 2로 정해 두었다 |
| 그리드 열 수 | 화면 폭이 아니라 **그리드 실측 폭**에서 계산 | 폭 기준 breakpoint로 잡았더니 1440px에서 6열이 되어 카드가 107px, `overflow: hidden`이 캔버스를 잘랐다 |
| 태블릿·모바일 열 수 | 상한 3 / 2 고정 | 1열 레이아웃에서는 폭이 남아 4열이 들어가지만 가이드라인 §8이 정한 밀도를 지킨다 |
| 착용 스트립 썸네일 | 배율 1 (64px) | 7칸 × 128px = 896px는 320px 패널에 안 들어간다. 규격 §1.2대로 캔버스를 줄이지 않고 칸을 줄바꿈한다 |
| 스트립 이미지 | 상품 단독이 아니라 **캐릭터 착용 모습** | 상품 단독 스프라이트가 없다(가이드라인 §5.7). 카드와 같은 방식으로 통일 |
| 등장식 탭 | `BACK`·`NECK`·`HAND`·`EFFECT` UI 통합 | 데이터 구조는 그대로 두고 상점 분류만 합친다(가이드라인 §5.3) |

## 4. 가이드라인과 다르게 한 것

1. **데스크톱 6열이 항상 6열은 아니다.** 1920px에서는 6열이지만 1440px에서는 5열,
   1280px에서는 4열이다. 6열을 강행하면 카드가 128px 캔버스보다 좁아져 캐릭터가 잘린다.
   열 수는 잘리지 않는 최대값을 쓴다.
2. **페이지당 상품 수도 열 수에 따라 달라진다.** 18(6열) / 15(5열) / 12(4열) / 6(3·2열) /
   4(1열). 열 수의 배수라야 마지막 줄이 어긋나지 않는다. 가이드라인이 지정한
   데스크톱 18과 태블릿 6은 각각 6열·3열에서 그대로 나온다.
3. **뒤로가기 버튼은 넣지 않았다.** 목업 좌상단의 `<` 버튼은 앱 셸의 사이드바 내비게이션과
   중복이고, `CharacterShop`에 내비게이션 prop이 없다.

## 5. 범위에서 제외 (가이드라인 §1·§10.2와 동일)

화폐 단위, 목업과 DB 가격 차이, VAT 비율, 가격 버전, 기존 구매 이력 정합성.
`V1_22`는 `default_character_price`를 건드리지 않는다.

## 6. 확인 방법

```bash
node scripts/character/check-assets.js
node scripts/character/render-audit.js
```

웹은 브라우저 콘솔에서 규격 §1.2 스니펫으로 배율을 검사하고, 카드 잘림은 아래로 본다.

```js
[...document.querySelectorAll('.character-product-card')].filter((card) => {
  const canvas = card.querySelector('canvas');
  return canvas && card.getBoundingClientRect().width < canvas.getBoundingClientRect().width - 0.5;
}).length; // 0이어야 한다
```
