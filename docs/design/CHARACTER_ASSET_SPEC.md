# 도트 캐릭터 이미지 작업 규격 v2.0

- 확정일: 2026-08-05
- 대상: 학생 앱(웹 + Expo) 캐릭터 상품 64종 + BASE 캐릭터 1종
- 상태: **운영 정본.** 이 문서가 도트 에셋 제작·검수의 단일 기준이다.
- 대체 대상: `docs/plans/2026-07-29-character-cosmetic-asset-spec.md` (v1.1, 64×72·77종·4체형·PixiJS 기준)

## 0. v1.1에서 바뀐 것

| 항목 | v1.1 | v2.0 |
| --- | --- | --- |
| 논리 캔버스 | 64×72 | **64×64** |
| 상품 수 | 77종 | **64종** |
| 체형 | 4단계(`CHUBBY`/`BASE`/`SLIM`/`VERY_SLIM`) | **1종 고정** |
| 렌더러 | PixiJS | **웹 Canvas 2D + Expo Skia** |
| 제작 방식 | `COMMON`/`BODY_VARIANT`/`POSITION_VARIANT` 3종 | **전체 캔버스 PNG 1종** |
| 총 제작량 | 204개 | **65개 + 마스크** |
| 실루엣 외곽선 | 2px | **1px** |
| 반투명 | 면사포·날개·효과에 허용 | **전면 금지** (알파 0/255만) |
| 앵커 | 런타임 좌표 계산 | **작화 가이드 전용** (런타임 계산 없음) |

v1.1의 체형 대응 규칙(`BODY_VARIANT` 4프레임, 9슬라이스, 체형별 좌표 보정)은 전부 폐기한다. 체형이 1종이므로 필요 없다.

---

## 1. 단일 규격

```text
논리 캔버스   64 × 64 px
색 심도       8bit RGBA PNG
알파          0 또는 255만
확대          정수 배율 + nearest-neighbor
회전·기울임   금지
```

모든 에셋(BASE·상품·마스크)은 **예외 없이 64×64**다. 잘라내기(trim) export를 금지한다. 상품이 캔버스의 일부만 쓰더라도 나머지는 투명으로 채운 64×64로 저장한다.

### 1.1 표시 크기

```ts
const CHARACTER_CANVAS = 64;
const displaySize = CHARACTER_CANVAS * integerScale;
```

| 용도 | 배율 | 표시 크기 |
| --- | ---: | --- |
| 상점 카드 썸네일 | 2 | 128×128 |
| 착용 슬롯 스트립 | 2 | 128×128 |
| 상품 상세 | 3 | 192×192 |
| 상점 미리보기 | 4 | 256×256 |
| 캐릭터 전체 화면 | 5 | 320×320 |

**금지 사항**

```text
64×64 → 48×56           가로·세로 배율이 달라 픽셀 위치와 대칭이 깨진다
64×64 → 비정수 배율      1.5배, 2.5배 등
width: 100% / height: %  컨테이너 크기에 따라 소수 배율이 된다
aspect-ratio 미고정      찌그러진다
캔버스에 max-width      컨테이너가 좁으면 캔버스를 줄여 비정수 배율이 된다
```

### 1.2 컨테이너가 캔버스를 줄이지 못하게 한다

가장 놓치기 쉬운 실수다. 배율을 정수로 계산해 인라인 크기를 줘도, CSS에
`max-width: 100%`가 걸려 있으면 컨테이너가 좁을 때 캔버스가 줄어든다. 세로는 그대로라
**가로만 소수 배율이 되고 비율까지 찌그러진다.**

2026-08-05에 실제로 두 곳에서 발견했다.

| 위치 | 증상 |
| --- | --- |
| 보관함 썸네일 | 128×128 지정인데 100px 열에 갇혀 **99×128** (가로 ×1.547) |
| 미리보기 스테이지 | 1024px 폭에서 256×256이 **237.2×256** (가로 ×3.706) |

규칙:

- **캔버스에 `max-width`·`max-height`를 걸지 않는다.** 크기는 `characterDisplaySize()`가
  정한 값만 쓴다
- **컨테이너를 캔버스에 맞춘다.** 반대로 하지 않는다. 썸네일 열은 `128px`,
  상품 카드 최소 폭은 `192px`(배율 3), 스테이지 패널은 `360px`(배율 5 + 여백)
- 그 폭을 확보할 수 없는 화면에서는 **레이아웃을 1열로 쌓는다.** 캔버스를 줄이지 않는다

검증은 브라우저에서 아래를 실행해 `깨짐: 0`을 확인한다.

```js
[...document.querySelectorAll('canvas.character-pixel-canvas')].filter((c) => {
  const r = c.getBoundingClientRect();
  const sx = r.width / c.width, sy = r.height / c.height;
  return !(Number.isInteger(sx) && Number.isInteger(sy) && sx === sy);
});
```

`48×56` 렌더 경로와 `*_48x56.png` 에셋은 전부 삭제한다.

---

## 2. 좌표계와 대칭 규칙

### 2.1 미러 축은 픽셀 사이다

캔버스 폭이 짝수(64)이므로 **중심 픽셀은 존재하지 않는다.** 미러 축은 `x=31`과 `x=32`의 경계다.

```ts
mirrorX = 63 - x;
```

좌우 대칭인 픽셀 런 `[a, b]`는 반드시 다음을 만족한다.

```text
a + b = 63
```

따라서 **대칭 요소의 가로 폭은 항상 짝수**다. 홀수 폭 런은 구조적으로 대칭이 될 수 없다.

| 예 | 판정 |
| --- | --- |
| `x24..25` ↔ `x38..39` | 24+39=63 ✅ 대칭 |
| `x9..55` (47px) | 9+55=64 ❌ 우측 1px 초과 |
| `x28..36` (9px) | 28+36=64 ❌ 홀수 폭 |
| `x9..54` (46px) | 9+54=63 ✅ 대칭 |

작화 시 `x=32`를 중심선으로 잡으면 항상 0.5px 어긋난다. **중심은 31과 32 사이**로 잡는다.

### 2.2 무엇을 대칭으로 강제하는가

정면 고정 캐릭터지만 광원이 좌상단 고정이므로 **음영은 좌우가 달라야 정상**이다. 대칭 강제 대상을 색 역할별로 나눈다.

| 대상 | 대칭 | 근거 |
| --- | --- | --- |
| 알파(실루엣 형태) | **강제** | 형태는 좌우가 같아야 한다 |
| 잉크 외곽선 `#262b31` | **강제** | 한쪽 선이 두꺼우면 기울어 보인다 |
| 볼 `#f1a3b5` | **강제** | 얼굴이 틀어져 보인다 |
| 눈·입 위치 (잉크) | **강제** | 잉크 검사에 포함된다 |
| 안쪽 테두리 `#dce2e1` | 허용 | 명암 램프의 일부다 (아래 참고) |
| 방향성 음영 `#eef1f0` | 허용 | 광원 방향 표현 |
| 상품 명암 램프 (mid/dark) | 허용 | 광원 방향 표현 |
| 디자인 의도상 비대칭 | 예외 등록 | 캡 챙, 목도리 꼬리, 배낭 등 |

즉 CI 대칭 검사는 **알파 채널 + 잉크색 + 볼색**에 적용하고, **명암 램프는 제외**한다.
램프까지 검사하면 조명이 평평해진다.

`#dce2e1`(rim)은 실측상 `y27..34`에서는 좌우 쌍이지만 `y42..51`에서는 방향성 음영으로
쓰인다. 대칭을 강제하면 하반신 명암을 다시 그려야 하므로 형태가 아니라 조명으로 다룬다.
구현: `scripts/character/lib/palette.js`의 `SYMMETRY_ENFORCED_COLORS`.

허용 위반은 BASE와 정면 대칭 상품 모두 **0쌍**이다. 기존 상품에 남은 위반은
`scripts/character/known-issues.json`에 기준선으로 못박아 두고 §11 2단계에서 해소한다.

---

## 3. BASE 캐릭터 기준

### 3.1 실루엣 정본

`base/base_default.png`(64×64) 실측값이다. **모든 상품이 이 실루엣을 기준으로 그려진다.**
전 구간이 미러 대칭(`a + b = 63`)이다.

| y | 좌–우 | 폭 | 부위 |
| ---: | --- | ---: | --- |
| 4–5 | 22–41 | 20 | 머리 꼭대기 |
| 6–7 | 20–43 | 24 | |
| 8–9 | 18–45 | 28 | |
| 10–12 | 17–46 | 30 | |
| 13–25 | 15–48 | 34 | 얼굴 (눈·볼) |
| 26–29 | 13–50 | 38 | 입·의상 시작선 |
| 30–33 | 11–52 | 42 | 가슴 |
| 34–44 | 9–54 | 46 | **최대 폭 (몸통)** |
| 45–48 | 11–52 | 42 | 허리 |
| 49–51 | 13–50 | 38 | |
| 52–53 | 15–48 | 34 | |
| 54–55 | 17–46 | 30 | |
| 56 | 18–45 | 28 | 다리 시작 |
| 57–58 | 18–30 / 33–45 | 26 | 다리 (가운데 `x31..32` 비움) |
| 59–60 | 19–28 / 35–44 | 20 | 발바닥 |

- 실루엣 범위: `y=4..60` (높이 57), `x=9..54` (최대 폭 46)
- 머리와 몸통이 이어진 단일 덩어리이며 **팔이 없다**
- **해부학적 목이 없다.** 폭이 위에서 아래로 단조 증가하므로 목 위치는 측정이 아니라 규약으로 정한다 (§3.4)

> 이 표는 2026-08-05에 §3.2 결함을 고친 뒤의 값이다. 수정 전 값(얼굴 `x14..48`,
> 몸통 `x9..55`, 다리 `x18..46`)으로 그려진 에셋은 1px 어긋나므로 다시 맞춘다.

### 3.2 고친 결함 (2026-08-05 완료)

측정된 대칭 위반 206px 중 **방향성 음영이 아닌 실제 결함** 52px을 수정했다.
재현·검증은 `node scripts/character/fix-base-symmetry.js`가 담당한다.

| # | 위치 | 현재 | 수정 |
| --- | --- | --- | --- |
| 1 | `y=13..25` `x14`/`x49` | 좌측에 잉크, 우측은 투명 (얼굴 폭 `x14..48`, 합 62) | `x14..49` 또는 `x15..48`로 맞춤 |
| 2 | `y=14..26` `x16`/`x47` | 좌측 안쪽 테두리 `#dce2e1` / 우측 잉크 `#262b31` — 선 두께가 좌우 다름 | 양쪽을 같은 색·같은 두께로 |
| 3 | `y=34..43` `x8`/`x55` | 몸통 `x9..55` (합 64), 우측 1px 초과 | `x9..54`로 축소 |
| 4 | `y=23` 볼 | **좌측 볼만 3행(y23–25), 우측은 2행(y24–25)** | 양쪽 동일 행 수 (3행 권장) |
| 5 | `y=26..27` 입 | `x28..36` (9px, 합 64) — 홀수 폭이라 대칭 불가 | `x28..35` (8px, 합 63) |
| 6 | `y=8..9` `x21`/`x42` | 좌측 안쪽 테두리 / 우측 잉크 | 양쪽 통일 (2번과 동일 원인) |
| 7 | `y=56..58` `x17`/`x46` | 우측 실루엣 1px 초과 | 맞춤 |
| 8 | `y=57..58` 다리 갈림 | 좌 `x18..30` / 우 `x34..46` (합 64/80) | 우측을 `x33..45`로 |
| 9 | `y=56` 다리 사이 음영 | 좌 `x20..27`(8px) / 우 `x36..44`(9px) | 우측을 `x36..43`으로 |

3번과 함께 `x=55`에 외곽선이 있던 `top_blue_tshirt.png`도 `x54`로 맞췄다.

`y=11`의 `x19`(흰색) / `x44`(`#eef1f0`)는 **방향성 음영이므로 결함이 아니다.** 광원 방향을
유지할지만 확인한다. 같은 이유로 `y=36..55`의 `#eef1f0` 우측 편중(우 49px / 좌 18px)도
그대로 둔다.

⚠️ **상품 스프라이트에는 이 방식을 그대로 적용하지 않는다.** 상품이 몸 실루엣 밖에 있는
것은 정상이다(모자·헤드폰·신발은 몸보다 넓다). "몸 밖"이라는 이유로 지우면 상품 내부에
구멍이 난다. 지울 대상은 **원래 몸 외곽선과 좌표가 겹쳐 있던 픽셀**뿐이다.

### 3.3 유지할 요소

```text
머리 꼭대기 y=4
얼굴 폭     y=13..25
눈          x24..25 / x38..39, y=19..23
볼          x19..21 / x42..44
입          y=26..27
최대 폭     y=34..43
발바닥      y=60
좌상단 광원 (하이라이트 위·왼쪽, 음영 아래·오른쪽)
```

BASE의 실루엣과 얼굴 배치는 상품 64종의 좌표 기준이다. **결함 수정 외에 형태를 바꾸면 64종을 전부 다시 그려야 한다.**

### 3.4 작화 가이드 앵커

전 레이어를 `(0, 0)`에 그리므로 **런타임 좌표 계산은 없다.** 아래 표는 작화·검수용 눈금이다.

| 앵커 | x | y | 사용처 |
| --- | --- | ---: | --- |
| `HEAD_TOP` | 31/32 경계 | 4 | 모자, 투구, 후드 |
| `EYE_LEFT` | 24–25 | 19–23 | 안경·고글 좌측 |
| `EYE_RIGHT` | 38–39 | 19–23 | 안경·고글 우측 |
| `FACE_CENTER` | 31/32 경계 | 21 | 얼굴 장식 |
| `MOUTH` | 31/32 경계 | 26 | 입 |
| `COLLAR` | 31/32 경계 | **26** | 의상 시작선 · 목도리 (규약) |
| `TORSO_CENTER` | 31/32 경계 | 38 | 상의 |
| `WAIST` | 31/32 경계 | 45 | 하의 |
| `SIDE_LEFT` | 9 | 38 | 몸통 좌측 최외곽 |
| `SIDE_RIGHT` | 54 | 38 | 몸통 우측 최외곽 |
| `HAND_LEFT` | 12 | 40 | 손 소품 좌측 (몸 옆 부착) |
| `HAND_RIGHT` | 51 | 40 | 손 소품 우측 (몸 옆 부착) |
| `BACK_CENTER` | 31/32 경계 | 36 | 배낭·날개 본체 중심 |
| `TAIL_ROOT` | 52 | 42 | 꼬리 접합부 |
| `FOOT_LEFT` | 23–24 | 59–60 | 좌측 발 |
| `FOOT_RIGHT` | 39–40 | 59–60 | 우측 발 |
| `FLOOR` | 31/32 경계 | **61** | 신발 바닥선·구름 효과 |

`FLOOR`는 BASE 실루엣 하단(`y=60`) 바로 아래다. 현재 `shoes_sneakers.png`가 `y=61`까지 쓰는 것과 일치한다.

⚠️ `character-rig.json`의 기존 앵커(`FLOOR y=45`, `FOOT y=43`, `NECK y=20`)는 **실제 렌더 좌표와 무관한 값**이므로 폐기한다. 이 표로 대체한다.

---

## 4. 팔레트

### 4.1 공용 5색

```ts
export const CHARACTER_PALETTE = {
  ink:    '#262b31',  // 외곽선·눈·입
  body:   '#fffdf8',  // 몸 기본
  rim:    '#dce2e1',  // 안쪽 테두리 — 좌우 쌍으로 사용, 대칭 대상
  shade:  '#eef1f0',  // 방향성 음영 — 우하단에 배치, 비대칭 허용
  cheek:  '#f1a3b5',  // 볼
} as const;
```

두 회색의 역할이 다르므로 혼용하지 않는다. 현재 BASE 실측 분포다.

| 색 | 좌측 | 우측 | 실제 용도 |
| --- | ---: | ---: | --- |
| `#dce2e1` | 33px | 26px | 주로 `x14/49`, `x12/51` 같은 **좌우 쌍**의 안쪽 테두리. 하반신에서는 램프로도 쓰인다 |
| `#eef1f0` | 18px | 49px | `y43..55`가 전부 우측 — **방향성 음영** |

둘 다 명암 램프에 속하므로 대칭 검사 대상이 아니다(§2.2).
새로 그릴 때는 `#dce2e1`을 좌우 쌍으로 쓰고 `#eef1f0`으로 광원 방향을 준다.

절차적 렌더러가 쓰던 `#273038`(잉크)·`#f3a6b8`(볼)은 **폐기**한다. PNG 값을 정본으로 삼는다. 두 값이 공존하면 렌더 경로에 따라 색이 미묘하게 바뀐다.

### 4.2 상품 색은 3단 명암 램프 + 공용 잉크

현재 10종이 모두 따르는 규칙이며 신규 상품도 동일하게 만든다.

```text
주색 (base)  →  중간 (mid)  →  어두운 (dark)  +  #262b31 외곽선
```

| 상품 | base | mid | dark |
| --- | --- | --- | --- |
| `hat_red_cap` | `#d94b45` | `#a93b38` | `#73302f` |
| `hat_teal_beanie` | `#09a8b2` | `#087f8c` | `#075a66` |
| `top_blue_tshirt` | `#5f80b0` | `#466696` | `#30466d` (+ 어깨 하이라이트 `#8ba4c8`) |
| `top_yellow_hoodie` | `#f4c348` | `#d69b2f` | `#a96f22` |
| `top_green_vest` | `#709a3f` | `#527532` | `#38522b` |
| `bottom_overalls` | `#5672a0` | `#3d5680` | `#293d61` |
| `back_backpack` | `#965d31` | `#704125` | `#51301e` |
| `face_headphones` | `#aeb7bd` | `#77818a` | `#4b535e` |
| `face_sunglasses` | `#4b535e`(반사) | `#262b31`(프레임) | `#151b20`(렌즈) |
| `shoes_sneakers` | `#fffdf8` | `#e6e9e9` | `#b7c0c5` (+ 포인트 `#a33f39`/`#d85a50`/`#f08b7d`) |

`face_sunglasses`처럼 어두운 상품은 램프가 공용 잉크와 겹친다. 이때도 외곽선은
`#262b31`을 유지하고 램프를 그보다 어둡게(`#151b20`) 잡는다.

- 한 상품의 색은 **주색 3단 + 잉크 + 포인트 최대 2색**, 총 6색 이내
- 하이라이트가 필요하면 base보다 밝은 1색 추가 (`top_yellow_hoodie`의 `#fff3c2`/`#ffdc73`)
- 팔레트에 없는 색을 쓰려면 이 문서의 표에 먼저 등록한다

### 4.3 정리해야 하는 잔여 픽셀

현재 에셋에 **1~2회만 등장하는 색**이 있다. 리샘플링 잔여물이며 제거 대상이다.

```text
#a9b9d0  x1   bottom_overalls
#7891b8  x2   bottom_overalls
#8ba4c8  x1   top_blue_tshirt
#c2dc86  x1   top_green_vest
#77838b  x1   face_sunglasses
```

### 4.4 마스크는 단색

가림 마스크는 알파 스텐실이므로 **`#ffffff` 단색 + 투명**만 쓴다. 현재 `top_blue_tshirt_occlusion.png`·`shoes_sneakers_occlusion.png`가 이 규칙을 따른다.

---

## 5. 레이어 순서

단일 상수로 관리하고 웹·Expo가 같은 값을 참조한다.

```ts
export const CHARACTER_LAYER_ORDER = [
  'BACKGROUND',
  'BACK_BEHIND',
  'EFFECT_BEHIND',
  'BASE_BODY',
  'SHOES',
  'BOTTOM',
  'TOP',
  'NECK',
  'FACE',
  'HAT',
  'HAND_BEHIND',
  'HAND_FRONT',
  'BACK_FRONT',
  'EFFECT_FRONT',
] as const;
```

### 5.1 한 상품을 앞·뒤로 나눌 수 있다

몸에 가려야 할 부분과 몸 위에 얹어야 할 부분이 함께 있는 상품은 파일을 나눈다.

```ts
interface LayeredCharacterItem {
  id: string;
  slot: CharacterSlot;
  layers: {
    behind?: string;   // BASE보다 뒤
    main?: string;     // 슬롯 기본 위치
    front?: string;    // BASE·의상보다 앞
  };
  occlusion?: string;  // 가림 마스크
}
```

| 상품 | behind | main | front |
| --- | --- | --- | --- |
| 배낭 | 가방 본체 | — | 어깨끈 |
| 튜브 | 뒤쪽 링 | — | 앞쪽 링 |
| 헤드폰 | 머리 뒤 밴드 | — | 이어컵 |
| 요정 날개 | 날개 전체 | — | — |
| 공룡 꼬리 | 꼬리 전체 | — | — |
| 구름 효과 | 뒤쪽 뭉치 | — | 앞쪽 뭉치 |

헤드폰은 이미 `face_headphones_back.png` / `face_headphones_front.png`로 분리되어 있다. 이 방식을 표준으로 확장한다.

---

## 6. 가림 마스크

### 6.0 필요할 때만 둔다 — 현재 배포 상품에는 없다

**불투명한 의상을 위에 덮는 것으로 충분하면 마스크를 두지 않는다.** 마스크는 상품이
몸 실루엣을 새로 정의해야 할 때만 쓴다.

2026-08-05에 배포되어 있던 마스크 2종(`top_blue_tshirt`, `shoes_sneakers`)을 삭제했다.
상품이 실제로 덮는 영역보다 넓게 파내서 티셔츠 착용 시 목에 구멍이 생겼다.
검증 결과 두 상품 모두 마스크가 필요하지 않았다 — 상품 bbox 안에서 몸이 비치는 지점을
전수 조사했더니 티셔츠 8px은 몸의 **입**(`y27, x28..35`), 운동화 2px은 **다리 사이 틈**
(`y56, x31..32`)이었고 둘 다 정상적으로 보여야 하는 부분이었다. 실제 누출은 0이다.

마스크 렌더링 자체는 살아 있다. `assets.occlusion`을 등록한 상품이 생기면 즉시 적용된다.
새 상품에 마스크가 필요한지 판단하려면 위와 같이 **상품 bbox 안에서 몸이 비치는 지점을
확인**한다. 비치는 것이 몸의 실루엣 외곽선이면 마스크가 필요하고, 얼굴·틈처럼 보여야
하는 부분이면 필요 없다.

### 6.1 마스크가 필요할 수 있는 슬롯

```text
TOP     의상 아래 몸 색·외곽선 제거
BOTTOM  하의 아래 몸 제거
SHOES   신발 위로 발이 보이는 현상 방지
HAT     모자 아래로 머리 외곽선이 튀어나오는 현상 방지
NECK    목도리 아래 의상 깃 제거
BACK    배낭·날개·꼬리가 몸에 완전히 가려지는 현상 방지
HAND    소품이 몸 외곽선을 덮는 현상 방지
EFFECT  효과가 몸을 덮는 현상 방지
```

현재는 `TOP`(1종)·`SHOES`(1종)만 마스크가 있다.

### 6.2 렌더 절차

```text
1. BACKGROUND
2. BACK_BEHIND / EFFECT_BEHIND
3. BASE_BODY
4. 장착 상품의 가림 마스크 적용  ← destination-out
5. SHOES → BOTTOM → TOP → NECK
6. FACE → HAT
7. HAND → BACK_FRONT → EFFECT_FRONT
```

웹 Canvas 2D:

```ts
ctx.save();
ctx.globalCompositeOperation = 'destination-out';
ctx.drawImage(occlusionMask, 0, 0, 64, 64);
ctx.globalCompositeOperation = 'source-over';
ctx.drawImage(itemSprite, 0, 0, 64, 64);
ctx.restore();
```

Expo Skia는 `BlendMode.DstOut` 또는 `Mask`로 같은 결과를 만든다.

### 6.3 옷을 입으면 옷이 새 실루엣이다

의상 착용 시 **BASE의 외곽선을 살리지 않는다.** 몸 외곽선과 의상 외곽선을 동시에 남기면 선이 두 겹으로 보이고, 반대로 의상이 몸 외곽선을 덮으면 윤곽이 끊긴다. 현재 `top_yellow_hoodie`의 소매가 정확히 이 문제다.

```text
잘못: BASE 외곽선 유지 + 의상이 그 위를 덮음  → 윤곽 끊김
올바름: 마스크로 해당 영역의 BASE를 지움 + 의상이 자기 외곽선을 가짐
```

---

## 7. 파일명과 디렉터리

**정본은 `student-app/public/character/`다.** 여기만 고치고
`node scripts/character/sync-assets.js`로 Expo에 복제한다.

```text
student-app/public/character/          ← 정본. Vite가 URL로 서빙한다
  base/
    base_default.png

  items/
    hat/        hat_red_cap.png …
    face/       face_sunglasses.png, face_headphones_back.png, face_headphones_front.png …
    neck/       neck_scarf.png …
    top/        top_blue_tshirt.png …
    bottom/     bottom_overalls.png …
    shoes/      shoes_sneakers.png …
    back/       back_backpack_behind.png, back_backpack_front.png …
    hand/       hand_baseball_glove.png …
    effect/     effect_swim_tube_behind.png, effect_swim_tube_front.png …
    background/ bg_spring.png …

  masks/
    top_blue_tshirt_occlusion.png
    shoes_sneakers_occlusion.png
    …

student-app/expo/assets/character/     ← 복제본. 직접 수정하지 않는다
```

같은 파일을 한 곳에서 참조할 수 없는 이유: 웹은 `public/`을 URL로 서빙하고 Expo(Metro)는
정적 `require()`로 번들에 담는다. 대신 복제본이 **어긋날 수 없게** 만든다 —
`sync-assets.js --check`와 `check-assets.js`가 두 사본의 바이트 일치를 검사한다.

> 모노레포로 재배치할 때 정본을 `assets/characters/`로 옮기고 두 앱이 빌드 시점에
> 가져가는 구조로 바꾼다. 지금 그렇게 하려면 Metro `watchFolders` 설정과 Vite
> `publicDir` 변경이 필요해 위험이 크다.

### 7.1 명명 규칙

```text
{spriteKey}.png                    단일 레이어
{spriteKey}_behind.png             BASE보다 뒤
{spriteKey}_front.png              BASE·의상보다 앞
{spriteKey}_occlusion.png          가림 마스크
```

- `spriteKey`는 **기존 카탈로그 값을 그대로 유지**한다. DB `character_catalog.character_code`와 학생 구매·착용 이력이 이 값에 묶여 있다
- 접미사 `_48x56`은 폐기
- export 시 trim / rotate / smoothing / 임의 배율을 모두 끈다

---

## 8. 상품 호환성 규칙

```ts
interface CharacterItemDefinition {
  id: string;
  slot: CharacterSlot;
  layers: { behind?: string; main?: string; front?: string };
  occlusion?: string;

  coversSlots?: CharacterSlot[];    // 시각적으로 덮음 (소유·착용 유지)
  hidesSlots?: CharacterSlot[];     // 렌더에서 제외
  incompatibleWith?: string[];      // 동시 착용 금지
  layerPriority?: number;           // 같은 밴드 내 순서 조정
}
```

| 상품 | 규칙 |
| --- | --- |
| `hat_space_helmet` | `hidesSlots: ['FACE']` — 헬멧이 얼굴을 감싼다 |
| `hat_knight` | `hidesSlots: ['FACE']` — 바이저가 얼굴을 덮는다 |
| `hat_dinosaur` | `hidesSlots: ['FACE']` — 후드가 자체 얼굴을 가진다 |
| `top_wizard_robe` | `coversSlots: ['BOTTOM']` |
| `top_spacesuit` | `coversSlots: ['BOTTOM']` |
| `top_fantasy_armor` | `coversSlots: ['BOTTOM']` |
| `top_dinosaur` | `coversSlots: ['BOTTOM']`, `hidesSlots: ['NECK']` |

**이 필드를 추가하면 렌더러가 반드시 사용해야 한다.** 설정만 있고 호출되지 않는 상태를 만들지 않는다 — 기존 `character-rig.json`의 `bodyTiers`·`renderPolicy`·`conflictGroups`가 그렇게 방치되어 실제 동작을 오해하게 만들었다.

---

## 9. 문제 상품 재작업 기준

### 9.1 배낭 `back_backpack` — **해결 (2026-08-05)**

원인은 아트가 아니라 **레이어 배정**이었다. 배포된 스프라이트에는 가방 본체가 없고
**어깨끈만** 그려져 있는데, BACK 슬롯의 기본 위치(BASE보다 뒤)에 놓여서 276px 중
254px(92%)이 몸에 가려 갈색 조각만 보였다.

```text
assets: { front: 'back_backpack' }   어깨끈은 몸 앞으로 지나가는 부위다
```

어깨끈 좌우가 60쌍 어긋나 있던 것도 함께 맞췄다. 팔이 없는 정면 캐릭터에서는
어깨끈만으로 배낭으로 읽히므로 가방 본체는 두지 않는다.

가방 본체를 추가하려면 `behind`에 별도 파일을 넣는다. 그때 규칙:

```text
본체를 BACK_CENTER(x 31/32 경계, y36) 중심으로
몸통 폭(x9..54)보다 좌우 3~4px 넓게 → 양옆으로 노출
캔버스 최외곽(x0..2, x61..63)에는 픽셀을 두지 않는다
```

### 9.2 공룡 꼬리 `back_dinosaur_tail`

현재: 변경 픽셀 25개. 몸에 거의 전부 가려진다.

```text
접합부를 TAIL_ROOT(x52, y42)로 이동
꼬리 끝은 몸통 최외곽(x54)에서 최소 6px 밖으로 (x60까지)
BACK_BEHIND 레이어
꼬리 앞부분이 몸 위로 지나가야 하면 _front로 분리
```

### 9.3 요정 날개 `back_fairy_wings`

현재: 흰 몸에 연한 하늘색이라 대비가 없고, 옆구리에 얇은 조각만 보인다.

```text
#262b31 외곽선 1px 추가
몸통 좌우로 각각 최소 8px 노출 (x1..8, x55..62)
3단 명암 램프 적용 — 흰색·연하늘 단독 사용 금지
BACK_BEHIND 레이어
```

### 9.4 샌들 `shoes_sandals`

현재: 변경 픽셀 90개. 얇은 갈색 선으로 보인다.

```text
발바닥 두께 최소 3px
스트랩을 발등에 명확히 표현
좌우 발 동일 크기 (FOOT_LEFT x23..24 / FOOT_RIGHT x39..40, a+b=63)
바닥선 FLOOR y=61
shoes_sandals_occlusion.png로 발 가림
```

### 9.5 몽실 구름 `effect_fluffy_cloud`

현재: 흰 몸·흰 배경에서 보이지 않는다.

```text
#dce2e1 이상의 외곽선 추가
EFFECT_BEHIND / EFFECT_FRONT로 분리해 캐릭터가 구름 위에 선 느낌
몸통(y34..48)과 겹치지 않게 FLOOR(y61) 주변에 배치
배경 없이도 식별되는 최소 명암 대비 확보
```

### 9.6 목도리 `neck_scarf` / `neck_knit_scarf`

현재: 배 높이(48×56 기준 `y26..32`)에 납작한 띠로 그려지고 꼬리가 우측 캔버스 밖으로 나간다.

```text
띠를 COLLAR(y26) 주변 y24..30에 배치
띠 폭은 얼굴 폭(x14..49)에 맞춤, 몸통 폭까지 넓히지 않음
꼬리는 몸통 측면(x46..54)을 따라 y30..44로 내림
캔버스 경계(x63) 밖 픽셀 금지
TOP 위에 렌더 (레이어 순서 §5)
후드형 상의와 충돌하면 incompatibleWith 등록
```

### 9.7 TOP 전체

현재: 소매가 BASE 외곽선을 덮어 윤곽이 끊긴다.

```text
상품별 occlusion 마스크 필수
의상이 덮는 영역의 BASE를 마스크로 지움
의상 자체가 1px 외곽선을 가짐
몸 외곽선과 의상 외곽선을 동시에 남기지 않음
어깨선은 TORSO_CENTER(y38) 기준, 몸통 최외곽(x9..54)을 넘지 않음
```

### 9.8 기사 투구 + 스키 고글

현재: 고글이 투구 위로 돌출한다.

```text
hat_knight에 hidesSlots: ['FACE'] 적용 → 고글이 렌더되지 않음
```

조합을 보여주고 싶다면 조합 전용 에셋을 별도로 만든다. 다만 조합 폭발(64종 × 조합)을 감당할 수 없으므로 **기본은 `hidesSlots`로 처리**한다.

---

## 10. 자동 검사

`scripts/character-asset-check`로 구현하고 CI에서 실행한다.

### 10.1 캔버스 크기

```ts
if (png.width !== 64 || png.height !== 64) {
  throw new Error(`${file}: 64x64 필요, 실제 ${png.width}x${png.height}`);
}
```

### 10.2 알파 이진

```ts
const ALLOWED_ALPHA = new Set([0, 255]);
```

반투명 픽셀이 있으면 확대 시 경계가 흐려진다.

### 10.3 팔레트

§4에 등록된 색만 허용한다. 미등록 색이 있으면 실패시키고, 필요하면 문서에 먼저 추가한다. **등장 횟수 1~2회인 색은 잔여 픽셀로 간주해 경고**한다.

### 10.4 대칭

`x ↔ 63-x`로 비교하되 **알파 + 잉크색 + 얼굴 요소색만** 검사한다 (§2.2).

```ts
const SYMMETRY_EXCEPTIONS = new Set([
  'hat_red_cap',          // 챙이 한쪽으로
  'hat_wizard',
  'hat_safari',
  'neck_scarf',           // 꼬리가 한쪽으로
  'neck_knit_scarf',
  'back_backpack',
  'back_briefcase',
  'back_dinosaur_tail',
  'hand_baseball_glove',
  'hand_magic_wand',
  'hand_toy_sword',
]);
```

BASE와 정면 대칭 상품의 허용 위반은 **0쌍**이다.

### 10.5 캔버스 경계

`x=0`, `x=63`, `y=0`, `y=63` 라인에 픽셀이 있으면 경고한다. 배경(`bg_*`)만 예외다. 경계에 닿은 상품은 잘려 보인다.

### 10.6 에셋 누락

```ts
for (const item of CHARACTER_CATALOG) {
  if (!ASSET_MAP[item.id]) {
    throw new Error(`에셋 누락: ${item.id}`);
  }
}
```

카탈로그 64종 중 10종만 에셋이 있는 현재 상태를 **빌드 단계에서 차단**한다. Expo가 조용히 건너뛰던 문제(`if (!profile) continue`)의 근본 차단책이다.

### 10.7 렌더 스냅샷

64종 각각에 대해 자동 생성한다.

```text
artifacts/character-audit/
  base.png                    BASE 단독
  item_{spriteKey}.png        BASE + 상품
  fullset_{theme}.png         대표 조합
  sheet.png                   전체 확대 시트
```

PR에서 확대 시트로 눈으로 확인할 수 있게 한다.

---

## 11. 제작 순서

64종을 한 번에 만들지 않는다. 기반이 흔들리면 전부 다시 그려야 한다.

### 1단계 · 기반 — **완료 (2026-08-05)**

```text
✅ BASE 64×64 결함 수정 (§3.2, 52px)
✅ 공용 팔레트 확정 (§4) — core/palette.ts + scripts/character/lib/palette.js
✅ 레이어 순서 상수화 (§5) — core/layerOrder.ts, 합성은 core/composition.ts 단일 경로
✅ 웹·Expo 공통 카탈로그 통합 — expo/src/features/character/core/ 단일 사본
✅ 48×56 경로·에셋 제거
✅ 자동 검사 스크립트 (§10) — scripts/character/
```

### 2단계 · 기존 10종 검수 — **완료 (2026-08-05)**

`known-issues.json` 기준선 9개 파일 318건을 **0건**으로 줄였다. 파일은 빈 객체(`{}`)로
남겨 두어, 앞으로 어떤 파일에서든 위반이 나오면 즉시 실패한다.

| 파일 | 조치 |
| --- | --- |
| `items/face/face_sunglasses.png` | 대칭 정규화 63px |
| `items/face/face_headphones_back/front.png` | 대칭 정규화 81px. 같은 물체이므로 기준 절반을 통일했다 |
| `items/bottom/bottom_overalls.png` | 대칭 정규화 48px + 잔여 색 3px 치환 |
| `items/top/top_blue_tshirt.png` | 대칭 정규화 43px. `#8ba4c8`은 `x21/x42` 미러 쌍이라 하이라이트로 등록 |
| `items/top/top_yellow_hoodie.png` | 대칭 정규화 22px |
| `items/top/top_green_vest.png` | 대칭 정규화 20px + 잔여 색 1px |
| `items/hat/hat_teal_beanie.png` | 대칭 정규화 12px |
| `items/back/back_backpack.png` | 레이어를 `front`로 (§9.1) + 대칭 정규화 110px |
| `masks/*_occlusion.png` | 삭제 (§6.0) |

명암 램프는 손대지 않았다. 전체를 미러링하면 좌상단 광원 표현이 사라진다.
`scripts/character/normalize-symmetry.js`가 알파와 잉크만 맞추고, 새로 채워지는 칸의
색은 **같은 쪽 이웃**에서 가져와 그쪽 면의 밝기를 유지한다.

남은 경고 2건은 결함이 아니다. `hat_teal_beanie`와 `face_headphones_back`이 `y0`에
닿지만, 머리 꼭대기가 `y4`라 모자류의 여유는 4px뿐이고 §3.4도 모자에 `y0..18`을 준다.
정면 대칭 예외로 남은 상품은 `hat_red_cap`(챙), 목도리 2종, 서류가방·꼬리·손 소품이다.

```text
hat_red_cap        hat_teal_beanie
face_sunglasses    face_headphones
top_blue_tshirt    top_yellow_hoodie    top_green_vest
bottom_overalls    shoes_sneakers       back_backpack
```

확인: BASE 좌표 일치 / 마스크 정상 / 웹·Expo 동일 / 정수 배율 선명도 / `x=55` 초과 픽셀 정리(§3.2-3)

### 3단계 · 죽은 슬롯 복구 (최우선)

Expo에서 슬롯 전체가 작동하지 않는 12종이다.

```text
HAND 3종        hand_baseball_glove, hand_magic_wand, hand_toy_sword
EFFECT 2종      effect_swim_tube, effect_fluffy_cloud
BACKGROUND 5종  bg_spring, bg_summer_sea, bg_autumn, bg_winter, bg_space
NECK 2종        neck_scarf, neck_knit_scarf
```

### 4단계 · 나머지 42종

```text
HAT 17 → TOP 15 → SHOES 5 → BACK 3 → FACE 2
```

### 5단계 · 조합 테스트

```text
BASIC / 여름 / 겨울 / 야구 / 우주 / 경찰 / 파일럿 / 마법사 / 기사 / 공룡
```

---

## 12. 검수 체크리스트

배포된 10종 기준 상태다. 3·4단계에서 상품을 추가하면 해당 항목을 다시 확인한다.

### 규격
- [x] BASE·상품 전부 64×64
- [x] 알파 0/255만
- [x] 팔레트 등록 색만 사용
- [x] `_48x56` 에셋 0개
- [x] 캔버스 경계 라인에 픽셀 없음 — 모자류 `y0` 접촉 2건은 의도 (§11 2단계)

### 대칭
- [x] BASE 알파·잉크·얼굴 요소 위반 0쌍
- [x] 정면 대칭 상품 위반 0쌍 (기준선 318건 → 0건)
- [x] 비대칭 상품은 예외 목록에 등록
- [x] 음영은 광원 방향 유지 (대칭 강제 안 함)

### 렌더 — 2026-08-05 실제 앱에서 확인
- [x] 웹·Expo에서 **10종** 동일하게 표시 · 나머지 54종은 3·4단계
- [x] 웹: 1440 / 1180 / 1024 / 768 / 375px 전 구간 정수 배율, 가로 스크롤 없음
- [x] Expo: 스테이지 ×5, 썸네일 ×1, 전 레이어 `offset 0,0` · `image-rendering: pixelated`
- [x] **레이어 순서가 두 플랫폼에서 동일** — Expo 스테이지가
      `base → bottom_overalls → top_yellow_hoodie → hat_red_cap` 순으로 그린다.
      `core/composition.ts`의 BASE_BODY(3) → BOTTOM(5) → TOP(6) → HAT(9)와 일치한다
- [x] 6슬롯 동시 착용 시 반투명 0px, 색 24종 전부 등록 팔레트
- [x] 에셋 누락 시 개발 환경 경고 (`describeMissingAssets`) — 웹에서 4종 확인
- [x] 에셋 누락 시 CI 실패 (`check-assets.js`)
- [ ] 마스크 적용 후 몸 외곽선과 의상 외곽선이 겹치지 않음 — 현재 마스크를 쓰는 상품 없음 (§6.0)

### 개별 결함
- [x] 배낭이 배낭 형태로 보임 (§9.1 해결)
- [ ] 공룡 꼬리가 몸 밖으로 6px 이상 노출 — 에셋 미배포
- [ ] 요정 날개가 흰 몸·투명 배경에서 식별됨 — 에셋 미배포
- [ ] 샌들이 신발로 인식됨 — 에셋 미배포
- [ ] 구름이 흰 배경에서 보임 — 에셋 미배포
- [ ] 목도리가 `COLLAR(y26)` 주변에 있고 캔버스를 벗어나지 않음 — 에셋 미배포
- [x] TOP 착용 시 윤곽 끊김 없음
- [ ] 투구 착용 시 고글 미돌출 — 에셋 미배포

### 구조
- [x] 웹·Expo가 동일 카탈로그·팔레트 참조 (단일 사본)
- [x] 레이어 순서가 단일 상수
- [x] 미사용 리그 필드 제거
- [x] `spriteKey` 변경 없음 (DB 구매·착용 이력 보존)

### 확인 방법

웹은 브라우저 콘솔에서 §1.2의 스니펫으로 배율을 검사한다. Expo는 웹 빌드에서
아래로 레이어 순서·배율·보간을 한 번에 확인한다.

```js
[...document.querySelectorAll('[id^="crisp-"]')].map((s) => ({
  stack: s.getBoundingClientRect().width,
  layers: [...s.querySelectorAll('img')].map((i) => ({
    file: (i.currentSrc || i.src).split('/').pop().split('?')[0],
    scale: i.getBoundingClientRect().width / 64,
    rendering: getComputedStyle(i).imageRendering,
  })),
}));
```

> Expo 웹은 세션을 메모리에만 보관한다(`src/auth/token-vault.ts` — 리프레시 토큰을
> localStorage에 두지 않으려는 의도). **URL로 이동하면 리로드가 일어나 로그아웃된다.**
> 화면 이동은 앱 내부 내비게이션으로 해야 한다.

---

## 13. 폐기 대상

1단계에서 아래를 모두 제거했다.

```text
에셋
  ✅ *_48x56.png                              15개 삭제
  ✅ layers/basic/face_headphones.png         결합본, 미사용
  ✅ base_char_base.png → base/base_default.png

코드 (웹)
  ✅ baseProfile.ts          renderWidth/renderHeight 포함
  ✅ basicLayerProfile.ts    → src/features/character/assetMap.ts
  ✅ catalog.ts / types.ts   → core/
  ✅ assetProfile.ts         placementFor / validateRig / fitMode /
                               coversSlots / ASSET_PROFILE_BY_KEY 전부
                               (conflictsFor는 core/items.ts로 이전)
  ✅ character-rig.json      anchors → §3.4, bodyTiers·conflictGroups·
                               renderPolicy·pixelCell·displayScale 전부 미사용이라 삭제
  ✅ pixelRenderer.ts        → docs/legacy/pixel-renderer-48x56/
                               (54종 작화 참고자료로 보존, §11 4단계 후 삭제)

코드 (Expo)
  ✅ catalog.ts / types.ts / assetProfile.ts / character-rig.json  중복 사본 삭제
  ✅ layerProfile.ts         → assetMap.ts

문서
  ✅ docs/plans/2026-07-29-character-cosmetic-asset-spec.md   폐기 배너
  ✅ docs/plans/2026-07-30-character-rig-integer-pixel-spec.md 폐기 배너
```

미사용 설정을 남겨두면 실제 동작을 잘못 이해하게 된다. 제거하거나 실제로 연결한다.

### 13.1 남은 정리 대상

```text
docs/legacy/pixel-renderer-48x56/     §11 4단계 완료 후 삭제
core/items.ts PLANNED_COMPATIBILITY   상품이 CHARACTER_ITEMS로 올라오면 합쳐서 제거
```

`scripts/character/known-issues.json`은 비었지만(`{}`) 남겨 둔다. 비어 있는 상태가
"어떤 위반도 허용하지 않음"이라는 뜻이고, 검사기가 매 실행마다 읽어 판정에 쓴다.
3·4단계에서 외부 이미지를 들여올 때 부채가 생기면 다시 기준선을 쓸 수 있다.

---

## 14. 참고 이미지

아래는 색상·무늬 참고용이다. **실루엣과 좌표는 §3의 실측값을 우선**한다. 참고 이미지는 64×72·4체형 시절 기준이므로 그대로 옮기지 않는다.

- [기본 상품](../assets/character/product-basic-reference.png)
- [계절 상품](../assets/character/product-seasonal-reference.png)
- [스포츠 상품](../assets/character/product-sports-reference.png)
- [직업 상품](../assets/character/product-career-reference.png)
- [판타지 상품](../assets/character/product-fantasy-reference.png)
- [행사 상품](../assets/character/product-event-reference.png)
- [대표 테마 코디](../assets/character/product-theme-outfits-reference.png)
