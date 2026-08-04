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
| `fix-base-symmetry.js` | 규격 §3.2의 BASE 대칭 결함 수정(일회성, 멱등). 무엇을 왜 바꿨는지 기록용 |
| `lib/png.js` | PNG 디코더·인코더 (8bit RGBA/RGB/팔레트, 비인터레이스) |
| `lib/palette.js` | 팔레트 정본. `core/palette.ts`와 값이 같아야 한다 |
| `known-issues.json` | 기존 부채 기준선. 규격 §11 2단계에서 줄여 나가고 비면 삭제 |

## 기준선(known-issues.json)이 있는 이유

기존 10종 상품 스프라이트에는 좌우 비대칭과 미등록 색이 남아 있다(규격 §11 2단계에서
해소 예정). 검사기를 지금 CI에 걸려면 이 부채를 통과시켜야 하지만, 그렇다고 검사를
끄면 **새로 추가되는 에셋에도 검사가 안 걸린다.**

그래서 파일별 현재 위반 수를 못박아 두고 그 수를 넘거나 목록에 없는 파일에서 위반이
나오면 실패시킨다. 부채를 고쳐서 수가 줄면 경고로 알려주므로 기준선이 느슨해진 채
방치되지 않는다.

## CI 연결

```bash
node scripts/character/sync-assets.js --check
node scripts/character/check-assets.js
```

`check-assets.js`는 core를 읽기 위해 `student-app/node_modules/esbuild`를 쓴다.
student-app 의존성 설치 뒤에 실행한다.
