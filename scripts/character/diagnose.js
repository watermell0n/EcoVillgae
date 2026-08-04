#!/usr/bin/env node
/**
 * 에셋 진단. 대칭 위반을 좌표로 출력하고 확대 이미지로 표시한다.
 *
 *   node scripts/character/diagnose.js                     기준선에 오른 파일 전부
 *   node scripts/character/diagnose.js items/top/x.png     특정 파일
 *   node scripts/character/diagnose.js --sheet             확대 시트만 생성
 *
 * 시트 색: 파랑 = 알파만 한쪽에 있음 / 빨강 = 잉크·볼 불일치 / 노랑 = 명암 차이(허용)
 */
const fs = require('fs');
const path = require('path');
const { decode, encode, blank, blit } = require('./lib/png');
const { SHARED, SYMMETRY_ENFORCED_COLORS } = require('./lib/palette');

const ROOT = path.join(__dirname, '../..');
const ASSETS = path.join(ROOT, 'student-app/public/character');
const BASELINE = path.join(__dirname, 'known-issues.json');

const args = process.argv.slice(2).filter((a) => !a.startsWith('--'));
const files = args.length
  ? args
  : Object.keys(fs.existsSync(BASELINE) ? JSON.parse(fs.readFileSync(BASELINE, 'utf8')) : {});

const NAME = {
  [SHARED.ink]: 'ink', [SHARED.body]: 'body', [SHARED.rim]: 'rim',
  [SHARED.shade]: 'shade', [SHARED.cheek]: 'cheek', '#ffffff': 'mask',
};

function read(rel) {
  const img = decode(path.join(ASSETS, rel));
  const hex = (x, y) => {
    const i = (y * img.width + x) * 4;
    if (img.rgba[i + 3] === 0) return null;
    return '#' + [img.rgba[i], img.rgba[i + 1], img.rgba[i + 2]]
      .map((v) => v.toString(16).padStart(2, '0')).join('');
  };
  return { img, hex };
}

const label = (h) => (h === null ? '없음' : NAME[h] ?? h);

const cells = [];

for (const rel of files) {
  const { img, hex } = read(rel);
  const W = img.width;
  const alpha = [];
  const ink = [];
  const ramp = [];

  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < W / 2; x++) {
      const a = hex(x, y);
      const b = hex(W - 1 - x, y);
      if ((a === null) !== (b === null)) { alpha.push({ x, y, a, b }); continue; }
      if (a === b) continue;
      if (SYMMETRY_ENFORCED_COLORS.has(a) || SYMMETRY_ENFORCED_COLORS.has(b)) ink.push({ x, y, a, b });
      else ramp.push({ x, y, a, b });
    }
  }

  console.log(`\n===== ${rel} =====`);
  console.log(`알파 ${alpha.length}쌍 · 잉크·볼 ${ink.length}쌍 · 명암 ${ramp.length}쌍(허용)`);

  const byRow = new Map();
  for (const v of [...alpha, ...ink]) {
    if (!byRow.has(v.y)) byRow.set(v.y, []);
    byRow.get(v.y).push(v);
  }
  for (const [y, list] of [...byRow.entries()].sort((a, b) => a[0] - b[0])) {
    const parts = list
      .sort((p, q) => p.x - q.x)
      .map((v) => `x${v.x}=${label(v.a)}/x${W - 1 - v.x}=${label(v.b)}`);
    console.log(`  y=${String(y).padStart(2)}  ${parts.join('  ')}`);
  }

  // 시각화: 원본 + 위반 오버레이
  const overlay = blank(W, img.height);
  blit(overlay, img, 0, 0, 1);
  const mark = (x, y, color) => {
    for (const px of [x, W - 1 - x]) {
      const i = (y * W + px) * 4;
      overlay.rgba[i] = color[0]; overlay.rgba[i + 1] = color[1];
      overlay.rgba[i + 2] = color[2]; overlay.rgba[i + 3] = 255;
    }
  };
  for (const v of ramp) mark(v.x, v.y, [230, 200, 60]);
  for (const v of ink) mark(v.x, v.y, [235, 60, 60]);
  for (const v of alpha) mark(v.x, v.y, [70, 140, 255]);
  cells.push({ rel, img, overlay });
}

if (!cells.length) {
  console.log('진단할 파일이 없다.');
  process.exit(0);
}

const S = 6;
const GAP = 5;
const COLS = Math.min(cells.length, 5);
const cw = 64 * S * 2 + GAP; // 원본 + 오버레이 한 쌍
const chh = 64 * S + GAP;
const rows = Math.ceil(cells.length / COLS);
const sheet = blank(COLS * cw + GAP, rows * chh + GAP, [22, 26, 32, 255]);

cells.forEach((cell, index) => {
  const cx = GAP + (index % COLS) * cw;
  const cy = GAP + Math.floor(index / COLS) * chh;
  for (let y = 0; y < 64 * S; y++) {
    for (let x = 0; x < 64 * S * 2; x++) {
      const v = ((x >> 4) + (y >> 4)) % 2 ? 70 : 55;
      const di = ((cy + y) * sheet.width + cx + x) * 4;
      sheet.rgba[di] = v; sheet.rgba[di + 1] = v; sheet.rgba[di + 2] = v + 6; sheet.rgba[di + 3] = 255;
    }
  }
  blit(sheet, cell.img, cx, cy, S);
  blit(sheet, cell.overlay, cx + 64 * S, cy, S);
});

const out = path.join(ROOT, 'artifacts/character-audit/diagnose.png');
fs.mkdirSync(path.dirname(out), { recursive: true });
encode(sheet, out);
console.log(`\n시트: ${path.relative(ROOT, out)} (칸마다 좌=원본, 우=위반 표시)`);
console.log('파랑 = 알파 불일치 / 빨강 = 잉크·볼 불일치 / 노랑 = 명암 차이(허용)');
console.log(cells.map((c, i) => `${i}: ${c.rel}`).join('\n'));
