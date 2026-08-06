#!/usr/bin/env node
/**
 * 도트 툴(Aseprite 등)에서 64×64로 직접 그릴 때 쓰는 템플릿을 만든다.
 *
 *   node scripts/character/make-template.js
 *
 * 산출물 (artifacts/character-template/)
 *   base_default.png   현재 BASE. 도트 툴에서 참조 레이어로 깔고 잠근다
 *   guides.png         미러 축·앵커·슬롯 영역 안내선. 별도 레이어로 깔고 내보낼 때 숨긴다
 *   guides_<slot>.png  슬롯별 작화 영역만 표시한 안내선
 *   palette.gpl        승인 팔레트. Aseprite/GIMP에서 불러온다
 *   README.md          설정값과 슬롯별 영역 표
 *
 * 규격: docs/design/CHARACTER_ASSET_SPEC.md
 */
const fs = require('fs');
const path = require('path');
const { decode, encode, blank, blit } = require('./lib/png');
const { SHARED, ITEM_RAMPS } = require('./lib/palette');

const ROOT = path.join(__dirname, '../..');
const BASE = path.join(ROOT, 'student-app/public/character/base/base_default.png');
const OUT = path.join(ROOT, 'artifacts/character-template');
const N = 64;

/**
 * 슬롯별 작화 영역.
 * 배포된 10종의 실측 bbox에서 뽑았다. 추정이 아니라 실제로 그려진 범위다.
 * 상품이 이 범위를 넘어도 되지만(모자는 몸보다 넓다), 넘을 이유가 없으면 안에 둔다.
 */
const SLOT_ZONES = {
  HAT: { x: [12, 51], y: [0, 18], note: '모자·투구·후드. 머리 꼭대기가 y4라 위쪽 여유는 4px뿐이다' },
  FACE: { x: [7, 56], y: [14, 28], note: '눈은 x24-25 / x38-39, y19-23. 입은 y26-27' },
  NECK: { x: [13, 50], y: [24, 32], note: '해부학적 목이 없다. 의상 깃선 y26 주변에 둔다' },
  TOP: { x: [9, 54], y: [25, 56], note: '몸통 최대 폭이 x9..54다. 소매를 그 밖으로 내지 않는다' },
  BOTTOM: { x: [10, 53], y: [27, 59], note: '멜빵을 쓰면 위로 y27까지 올라간다' },
  SHOES: { x: [11, 52], y: [49, 61], note: '바닥선 y61. 발은 x19-28 / x35-44' },
  BACK: { x: [1, 62], y: [18, 51], note: '몸에 가려지지 않도록 x9..54 밖으로 내보낸다' },
  HAND: { x: [0, 63], y: [30, 50], note: '팔이 없다. 몸 옆 x12 / x51 에 매단 형태로 그린다' },
  EFFECT: { x: [0, 63], y: [0, 63], note: '앞·뒤로 나눌 수 있다. 흰 몸과 대비되는 색을 쓴다' },
  BACKGROUND: { x: [0, 63], y: [0, 63], note: '캔버스 전체를 채운다' },
};

/** 안내선 색 (반투명 — 템플릿이므로 알파 정책을 따르지 않는다) */
const G = {
  mirror: [255, 60, 160, 150],
  anchor: [90, 200, 255, 130],
  zone: [255, 200, 60, 110],
  margin: [255, 90, 90, 90],
  bodyEdge: [120, 255, 180, 110],
};

function put(img, x, y, rgba) {
  if (x < 0 || y < 0 || x >= N || y >= N) return;
  const i = (y * N + x) * 4;
  const a = rgba[3] / 255;
  for (let k = 0; k < 3; k++) {
    img.rgba[i + k] = Math.round(rgba[k] * a + img.rgba[i + k] * (1 - a));
  }
  img.rgba[i + 3] = Math.max(img.rgba[i + 3], rgba[3]);
}

const hLine = (img, y, c, x0 = 0, x1 = N - 1) => {
  for (let x = x0; x <= x1; x++) put(img, x, y, c);
};
const vLine = (img, x, c, y0 = 0, y1 = N - 1) => {
  for (let y = y0; y <= y1; y++) put(img, x, y, c);
};
const rect = (img, x0, y0, x1, y1, c) => {
  hLine(img, y0, c, x0, x1); hLine(img, y1, c, x0, x1);
  vLine(img, x0, c, y0, y1); vLine(img, x1, c, y0, y1);
};

/** BASE 실루엣의 행별 좌우 끝 */
function silhouette(base) {
  const rows = [];
  for (let y = 0; y < N; y++) {
    let l = -1, r = -1;
    for (let x = 0; x < N; x++) {
      if (base.rgba[(y * N + x) * 4 + 3] === 0) continue;
      if (l < 0) l = x;
      r = x;
    }
    rows.push([l, r]);
  }
  return rows;
}

const base = decode(BASE);
const rows = silhouette(base);
fs.mkdirSync(OUT, { recursive: true });
fs.copyFileSync(BASE, path.join(OUT, 'base_default.png'));

// ---------------------------------------------------------------- 공통 안내선
function commonGuides() {
  const g = blank(N, N);
  // 캔버스 경계 여유 (상품이 닿으면 잘려 보인다)
  for (const x of [0, 1, 62, 63]) vLine(g, x, G.margin);
  for (const y of [0, 63]) hLine(g, y, G.margin);
  // 미러 축: x31 / x32 경계
  vLine(g, 31, G.mirror);
  vLine(g, 32, G.mirror);
  // 몸 실루엣 최외곽
  rows.forEach(([l, r], y) => {
    if (l < 0) return;
    put(g, l, y, G.bodyEdge);
    put(g, r, y, G.bodyEdge);
  });
  // 주요 앵커 행
  for (const y of [4, 19, 23, 26, 38, 45, 61]) hLine(g, y, G.anchor);
  return g;
}

const guides = commonGuides();
encode(guides, path.join(OUT, 'guides.png'));

for (const [slot, z] of Object.entries(SLOT_ZONES)) {
  const g = blank(N, N);
  blit(g, guides, 0, 0, 1);
  rect(g, z.x[0], z.y[0], z.x[1], z.y[1], G.zone);
  encode(g, path.join(OUT, `guides_${slot.toLowerCase()}.png`));
}

// ---------------------------------------------------------------- 팔레트
const paletteEntries = [
  ['ink 외곽선·눈·입', SHARED.ink],
  ['body 몸 기본', SHARED.body],
  ['rim 안쪽 테두리', SHARED.rim],
  ['shade 방향성 음영', SHARED.shade],
  ['cheek 볼', SHARED.cheek],
];
for (const [key, ramp] of Object.entries(ITEM_RAMPS)) {
  ramp.forEach((hex, i) => paletteEntries.push([`${key} ${i + 1}`, hex]));
}
const gpl = ['GIMP Palette', 'Name: EcoVillage Character', 'Columns: 8', '#']
  .concat(paletteEntries.map(([name, hex]) => {
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
    return `${String(r).padStart(3)} ${String(g).padStart(3)} ${String(b).padStart(3)}\t${name}`;
  }))
  .join('\n') + '\n';
fs.writeFileSync(path.join(OUT, 'palette.gpl'), gpl);

// ---------------------------------------------------------------- README
const zoneRows = Object.entries(SLOT_ZONES)
  .map(([slot, z]) => `| ${slot} | x${z.x[0]}–${z.x[1]} | y${z.y[0]}–${z.y[1]} | ${z.note} |`)
  .join('\n');

fs.writeFileSync(path.join(OUT, 'README.md'), `# 64×64 도트 작화 템플릿

규격은 [도트 캐릭터 이미지 작업 규격](../../docs/design/CHARACTER_ASSET_SPEC.md)이 정본이다.
이 폴더는 도트 툴에서 바로 쓰는 준비물이다.

## 파일

| 파일 | 용도 |
| --- | --- |
| \`base_default.png\` | 현재 BASE. 참조 레이어로 깔고 **잠근다**. 상품만 새 레이어에 그린다 |
| \`guides.png\` | 미러 축·앵커·몸 최외곽·캔버스 여유. 별도 레이어로 깔고 **내보낼 때 숨긴다** |
| \`guides_<slot>.png\` | 위 안내선 + 해당 슬롯 작화 영역(노란 사각형) |
| \`palette.gpl\` | 승인 팔레트. Aseprite/GIMP에서 불러온다 |

안내선 색: 분홍 = 미러 축(x31/x32 경계) · 하늘 = 주요 앵커 행 · 연두 = 몸 최외곽 ·
노랑 = 슬롯 영역 · 빨강 = 캔버스 여유(닿으면 잘려 보인다)

## Aseprite 설정

\`\`\`text
새 파일        64 x 64, Color Mode: RGBA, Background: Transparent
팔레트         Palette 메뉴 → Load Palette → palette.gpl
대칭           View → Symmetry Options → Horizontal Symmetry, 축을 32.0 에 맞춘다
연필           Brush size 1, Pixel-perfect 켜기
안티앨리어싱    쓰지 않는다 (Aseprite 기본 연필은 끄여 있다)
내보내기        File → Export → PNG, Scale 100%, Resize 없음
\`\`\`

**대칭 축이 32.0인 것이 중요하다.** 64는 짝수라 중심 픽셀이 없고 축은 x31과 x32의
경계다. 축을 31이나 32에 맞추면 1px 어긋난다.

## 슬롯별 작화 영역

배포된 10종의 실측 bbox에서 뽑은 값이다. 넘어도 되지만 넘을 이유가 없으면 안에 둔다.

| 슬롯 | x | y | 비고 |
| --- | --- | --- | --- |
${zoneRows}

## 지켜야 할 것

- **알파는 0 또는 255만.** 반투명 픽셀이 하나도 없어야 한다
- **색은 팔레트에 있는 것만.** 새 색이 필요하면 규격 §4.2 표에 먼저 등록한다
- **외곽선 1px**, 상품 주색은 3단 명암 + 공용 잉크(\`${SHARED.ink}\`)
- **정면 대칭 상품은 좌우 대칭.** 대칭 런 [a,b]는 a+b=63을 만족하므로 **폭이 짝수**여야 한다
- 한 상품이 몸 앞뒤로 걸치면 파일을 나눈다: \`{key}_behind.png\` / \`{key}_front.png\`

## 파일명

카탈로그 \`spriteKey\`를 그대로 쓴다 (예: \`hat_wizard.png\`). DB
\`character_catalog.character_code\`와 학생 구매·착용 이력이 이 값에 묶여 있어 바꾸면 안 된다.

## 반입 절차

\`\`\`bash
# 1) student-app/public/character/items/<slot>/ 에 넣는다
# 2) core에 등록: expo/src/features/character/core/items.ts 의 CHARACTER_ITEMS
# 3) Expo assetMap.ts 에 require 추가
node scripts/character/sync-assets.js     # Expo로 복제
node scripts/character/check-assets.js    # 크기·알파·팔레트·대칭·누락 검사
node scripts/character/render-audit.js    # 착용 모습 확대 시트
\`\`\`
`);

console.log(`템플릿 생성: ${path.relative(ROOT, OUT)}`);
console.log(`  base_default.png, guides.png, guides_<slot>.png ×${Object.keys(SLOT_ZONES).length}`);
console.log(`  palette.gpl (${paletteEntries.length}색), README.md`);
