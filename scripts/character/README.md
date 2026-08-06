# 캐릭터 에셋 도구

제작·검수 기준은 [도트 캐릭터 이미지 작업 규격](../../docs/design/CHARACTER_ASSET_SPEC.md)이다.
이 디렉터리의 스크립트는 그 규격을 기계적으로 강제한다. 외부 의존성 없이 Node만 쓴다
(PNG 인코더·디코더를 `lib/png.js`에 직접 구현했다).

## 에셋을 추가·수정할 때

```bash
# 1) 웹 정본에 파일을 넣는다 (student-app/public/character/)
# 2) core에 상품을 등록한다 (expo/src/features/character/core/items.ts)
# 3) Expo assetMap.ts에 require를 추가한다
# 4) Expo로 복제한다
node scripts/character/sync-assets.js

# 5) 검사한다
node scripts/character/check-assets.js

# 6) 눈으로 확인한다
node scripts/character/render-audit.js
```

## 스크립트

| 파일 | 하는 일 |
| --- | --- |
| `sync-assets.js` | 웹 정본(`public/character/`)을 Expo(`expo/assets/character/`)로 복제. `--check`는 차이만 보고 실패 |
| `check-assets.js` | 규격 §10 검사. 크기·알파·팔레트·대칭·경계·누락·사본 일치. `--write-baseline`으로 기준선 재기록 |
| `render-audit.js` | 규격 §10.7 스냅샷. `artifacts/character-audit/`에 확대 시트 생성 |
| `diagnose.js` | 대칭 위반을 좌표로 출력하고 확대 이미지로 표시. 어디가 왜 어긋났는지 볼 때 |
| `normalize-symmetry.js` | 정면 대칭 상품의 알파·잉크를 좌우 대칭으로 맞춘다. 명암은 건드리지 않는다 |
| `snap-palette.js` | 미등록 색을 그 상품 램프의 최근접 색으로 치환. 잔여 픽셀 정리·외부 이미지 반입용 |
| `fix-base-symmetry.js` | 규격 §3.2의 BASE 대칭 결함 수정(일회성, 멱등). 무엇을 왜 바꿨는지 기록용 |
| `lib/png.js` | PNG 디코더·인코더 (8bit RGBA/RGB/팔레트, 비인터레이스) |
| `lib/palette.js` | 팔레트 정본. `core/palette.ts`와 값이 같아야 한다 |
| `known-issues.json` | 위반 허용 기준선. 현재 비어 있다 = 어떤 위반도 허용하지 않는다 |

## 대칭을 고칠 때

```bash
node scripts/character/diagnose.js items/top/x.png            # 어디가 어긋났는지 본다
node scripts/character/normalize-symmetry.js items/top/x.png  # 알파·잉크만 맞춘다
node scripts/character/snap-palette.js items/top/x.png        # 잔여 색을 정리한다
node scripts/character/sync-assets.js                          # Expo로 복제
node scripts/character/check-assets.js                         # 확인
```

`normalize-symmetry.js`는 **명암 램프를 건드리지 않는다.** 전체를 미러링하면 좌상단
광원 표현이 사라져 그림이 평평해진다. 새로 불투명해지는 칸의 색은 같은 쪽 이웃에서
가져와 그쪽 면의 밝기를 유지한다.

기준 절반은 불투명 픽셀이 더 많은 쪽을 자동 선택하지만 근거가 약할 때가 있다.
**한 물체가 여러 파일로 갈려 있으면 `--ref`로 기준을 통일한다** — 헤드폰 앞/뒤가
서로 다른 기준을 고르면 두 레이어가 어긋난다.

미러 쌍으로 찍힌 색은 의도한 하이라이트일 가능성이 높다. 치환하지 말고
`lib/palette.js`의 `ITEM_RAMPS`와 규격 §4.2 표에 등록한다.

## 기준선(known-issues.json)이 있는 이유

검사기를 기존 코드베이스에 도입할 때 남은 부채를 통과시키면서도 **새 위반은 즉시
실패**시키기 위한 장치다. 파일별 허용 위반 수를 못박고, 그 수를 넘거나 목록에 없는
파일에서 위반이 나오면 실패한다. 부채를 고쳐서 수가 줄면 경고로 알려주므로 기준선이
느슨해진 채 방치되지 않는다.

규격 §11 2단계에서 318건을 0건으로 줄여 현재는 비어 있다. 비어 있는 상태가 곧
"어떤 위반도 허용하지 않음"이므로 파일을 지우지 않고 남겨 둔다. 3·4단계에서 외부
이미지를 들여올 때 부채가 생기면 다시 쓸 수 있다.

## CI 연결

```bash
node scripts/character/sync-assets.js --check
node scripts/character/check-assets.js
```

`check-assets.js`는 core를 읽기 위해 `student-app/node_modules/esbuild`를 쓴다.
student-app 의존성 설치 뒤에 실행한다.
