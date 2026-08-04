#!/usr/bin/env node
/**
 * 미등록 색을 그 상품의 팔레트 중 가장 가까운 색으로 치환한다.
 *
 *   node scripts/character/snap-palette.js items/bottom/x.png [--dry]
 *
 * 리샘플링 잔여 픽셀을 정리하는 용도다. 의도한 하이라이트라면 치환하지 말고
 * scripts/character/lib/palette.js의 ITEM_RAMPS에 등록한다 — 미러 쌍으로 찍혀 있거나
 * 밝은 면을 따라 이어져 있으면 의도한 색일 가능성이 높다.
 *
 * 외부 상품 이미지를 들여올 때도 같은 스크립트로 팔레트를 강제할 수 있다.
 */
const path = require('path');
const { execFileSync } = require('child_process');
const fs = require('fs');
const { decode, encode } = require('./lib/png');
const { allowedColors } = require('./lib/palette');

const ROOT = path.join(__dirname, '../..');
const ASSETS = path.join(ROOT, 'student-app/public/character');
const argv = process.argv.slice(2);
const DRY = argv.includes('--dry');
const targets = argv.filter((a) => !a.startsWith('--'));

if (!targets.length) {
  console.error('대상 파일을 지정한다. 예: items/bottom/bottom_overalls.png');
  process.exit(1);
}

function loadCore() {
  const out = path.join(require('os').tmpdir(), `core-snap-${process.pid}.cjs`);
  execFileSync('node', [
    path.join(ROOT, 'student-app/node_modules/esbuild/bin/esbuild'),
    path.join(ROOT, 'student-app/expo/src/features/character/core/index.ts'),
    '--bundle', '--format=cjs', '--platform=node', '--outfile=' + out,
  ], { stdio: ['ignore', 'ignore', 'inherit'] });
  try {
    return require(out);
  } finally {
    fs.rmSync(out, { force: true });
  }
}

const core = loadCore();
const idByPath = Object.fromEntries(
  Object.entries(core.CHARACTER_ASSET_PATHS).map(([id, rel]) => [rel, id]),
);
const { ITEM_RAMPS, SHARED } = require('./lib/palette');

const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
const dist = (a, b) => {
  const [r1, g1, b1] = rgb(a);
  const [r2, g2, b2] = rgb(b);
  // 눈이 초록에 민감한 것을 반영한 가중 거리
  return 2 * (r1 - r2) ** 2 + 4 * (g1 - g2) ** 2 + 3 * (b1 - b2) ** 2;
};

let total = 0;
for (const rel of targets) {
  const file = path.join(ASSETS, rel);
  const img = decode(file);
  const assetId = idByPath[rel];
  const spriteKey = core.SPRITE_KEY_BY_ASSET_ID[assetId] ?? assetId;
  const allowed = [...allowedColors(spriteKey)];

  /**
   * 치환 후보는 그 상품의 램프 + 잉크로 제한한다.
   *
   * 몸 공용색(body/rim/shade/cheek)까지 후보에 넣으면 의상의 밝은 잔여 픽셀이
   * 수치상 더 가까운 몸통 색으로 붙어 밝은 점으로 남는다. 상품 램프가 없는
   * 에셋(BASE, 마스크)만 공용색을 후보로 쓴다.
   */
  const ramp = ITEM_RAMPS[spriteKey];
  const candidates = ramp ? [...ramp, SHARED.ink] : allowed;

  let changed = 0;
  const log = [];
  for (let y = 0; y < 64; y++) {
    for (let x = 0; x < 64; x++) {
      const i = (y * 64 + x) * 4;
      if (img.rgba[i + 3] === 0) continue;
      const hex = '#' + [0, 1, 2]
        .map((k) => img.rgba[i + k].toString(16).padStart(2, '0')).join('');
      if (allowed.includes(hex)) continue;
      const nearest = candidates.reduce((best, c) => (dist(hex, c) < dist(hex, best) ? c : best));
      const [r, g, b] = rgb(nearest);
      img.rgba[i] = r; img.rgba[i + 1] = g; img.rgba[i + 2] = b;
      changed++;
      log.push(`(${x},${y}) ${hex} → ${nearest}`);
    }
  }

  console.log(`\n${rel}  ${changed}px ${DRY ? '치환 예정' : '치환'}`);
  for (const line of log) console.log(`  ${line}`);
  if (changed && !DRY) encode(img, file);
  total += changed;
}

console.log(`\n총 ${total}px ${DRY ? '치환 예정' : '치환 완료'}`);
