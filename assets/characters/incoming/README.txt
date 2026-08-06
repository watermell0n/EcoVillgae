이 폴더는 새 도트 이미지를 받는 곳이다. 원본은 커밋하지 않는다(.gitignore).

파일명은 카탈로그 spriteKey를 쓴다 (예: hat_red_cap.png).
spriteKey 목록은 student-app/expo/src/features/character/core/items.ts 참고.

정규화 도구
  배경   node scripts/character/convert-background.js --all
  상품   node scripts/character/fit-item.js hat_wizard

상품 원본은 캐릭터가 없는 투명 배경이어야 한다. 캐릭터와 합성된 이미지는
자동 분리가 되지 않는다 — 상품이 몸을 덮거나 몸에 가려지면 정렬 기준이
사라진다. 실제로 캐릭터가 구름 위에 서 있는 원본은 어떤 배율·오프셋에서도
맞지 않았다(일치 923칸 / 누락 1109칸). 합성 원본은 상품만 다시 뽑아 온다.

정규화 뒤에는 반드시 둘을 돌린다.
  node scripts/character/sync-assets.js     웹 -> Expo 복사
  node scripts/character/check-assets.js    규격 검사
