# 도트 캐릭터 에셋

제작·검수 기준은 [도트 캐릭터 이미지 작업 규격 v2.0](../../docs/design/CHARACTER_ASSET_SPEC.md)이
정본입니다. 아래는 요약이며, 상세 규칙과 좌표는 규격 문서를 따릅니다.

- 논리 캔버스는 **64×64 단일 규격**입니다. `48×56`·`64×72` 에셋과 렌더 경로는 폐기했습니다.
- 모든 레이어는 잘라내지 않은 64×64 투명 PNG이며 `(0, 0)`에 그립니다.
- 알파는 0 또는 255만 사용하고, 확대는 정수 배율 + nearest-neighbor만 허용합니다.
- 체형은 1종 고정입니다. 체형별 프레임은 만들지 않습니다.
- 웹과 Expo가 **같은 에셋과 같은 팔레트**를 참조합니다. 한쪽에만 상품을 추가하지 않습니다.

## 이 디렉터리에는 에셋이 없습니다

`assets/characters/`는 작업용 자리이고, 실제 에셋은 학생 앱 안에 있습니다.

```text
student-app/public/character/          ← 정본. 여기만 고친다
  base/       base_default.png
  items/      hat, top, shoes, face, back, effect, background, hand, neck

student-app/expo/assets/character/     ← 복제본. 직접 수정하지 않는다
```

`bottom/` 디렉터리는 현재 없습니다. 유일한 하의였던 멜빵바지(`bottom_overalls`)를 판매
종료하면서 에셋을 지웠습니다. 카탈로그에는 `RETIRED`로 남아 있습니다.

정본과 복제본을 분리하는 이유는 규격 문서 §7에 있습니다. Expo(Metro)가 정적 `require()`로만
에셋을 번들에 담아 프로젝트 루트 밖의 파일을 읽지 못하기 때문입니다. 두 곳을 맞추는 것은
`node scripts/character/sync-assets.js`가 합니다.

`incoming/`은 외부에서 받은 원본을 잠시 두는 자리입니다. 정규화를 거쳐 위 경로로 옮기며,
원본 자체는 커밋하지 않습니다(`.gitignore`). 자세한 절차는 `incoming/README.txt`에 있습니다.

## 파일 이름

```text
{spriteKey}.png                     한 장으로 끝나는 레이어
{spriteKey}_behind.png              캐릭터 뒤에 그리는 레이어
{spriteKey}_front.png               캐릭터 앞에 그리는 레이어
```

앞·뒤 분할은 배낭·수영 튜브처럼 몸을 감싸는 물체에만 씁니다. 현재 10개 파일이 이 형태입니다.

가림 마스크(`{spriteKey}_occlusion.png`)는 **현재 없습니다.** 규격 §6.0에서 삭제했습니다.
렌더러의 마스크 경로는 살아 있어 다시 등록하면 동작합니다.

`spriteKey`는 DB `character_catalog.character_code`와 학생 구매·착용 이력에 묶여 있으므로
변경하지 않습니다. 슬롯은 별도 칼럼이 아니라 `character_code`의 접두어(`hat_`, `top_` …)로
표현합니다. 배경 슬롯만 디렉터리는 `background/`, 코드 접두어는 `bg_`입니다.

전체 `spriteKey` 목록은 `student-app/expo/src/features/character/core/items.ts`에 있습니다.

## 카탈로그와 에셋의 관계

상품 추가 시 카탈로그 등록과 에셋 파일이 1:1이어야 합니다. 어긋남은
`node scripts/character/check-assets.js`로 확인합니다.

> 이 검사는 아직 CI에 걸려 있지 않습니다. Jenkinsfile과 npm script 어디에도 등록돼 있지 않아
> 지금은 사람이 직접 돌려야 합니다. 빌드 단계에서 막고 싶으면 파이프라인에 추가해야 합니다.

카탈로그 현황은 `character_catalog` 표에서 확인합니다. 판매 종료한 상품은 지우지 않고
`status = 'RETIRED'`로 남깁니다. 이미 구매한 학생의 보유·착용 이력이 참조하기 때문입니다.
