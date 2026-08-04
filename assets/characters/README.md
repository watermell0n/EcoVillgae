# 도트 캐릭터 에셋

제작·검수 기준은 [도트 캐릭터 이미지 작업 규격 v2.0](../../docs/design/CHARACTER_ASSET_SPEC.md)이
정본입니다. 아래는 요약이며, 상세 규칙과 좌표는 규격 문서를 따릅니다.

- 논리 캔버스는 **64×64 단일 규격**입니다. `48×56` 에셋과 렌더 경로는 폐기했습니다.
- 모든 레이어는 잘라내지 않은 64×64 투명 PNG이며 `(0, 0)`에 그립니다.
- 알파는 0 또는 255만 사용하고, 확대는 정수 배율 + nearest-neighbor만 허용합니다.
- 체형은 1종 고정입니다. 체형별 프레임은 만들지 않습니다.
- 웹과 Expo가 **같은 에셋과 같은 팔레트**를 참조합니다. 한쪽에만 상품을 추가하지 않습니다.
- 상품 추가 시 카탈로그 등록과 에셋 파일이 항상 1:1이어야 하며, 누락은 빌드 단계에서
  차단합니다.

## 디렉터리

```text
assets/character/
  base/       BASE 캐릭터와 몸 실루엣 스텐실
  items/      슬롯별 상품 레이어 (hat, face, neck, top, bottom, shoes, back, hand, effect, background)
  masks/      슬롯별 가림 마스크
```

파일명은 `{spriteKey}.png`, 앞·뒤 분할은 `{spriteKey}_behind.png` / `{spriteKey}_front.png`,
가림 마스크는 `{spriteKey}_occlusion.png`를 사용합니다.

`spriteKey`는 DB `character_catalog.character_code`와 학생 구매·착용 이력에 묶여 있으므로
변경하지 않습니다.
