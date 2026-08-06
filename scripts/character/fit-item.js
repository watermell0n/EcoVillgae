#!/usr/bin/env node
/**
 * 상품만 담긴 투명 배경 원본 → 슬롯에 맞춘 64×64 에셋.
 *
 *   node scripts/character/fit-item.js hat_wizard
 *   node scripts/character/fit-item.js hat_wizard --scale=0.9 --dy=-2 --dry
 *   node scripts/character/fit-item.js --all
 *
 * 입력: assets/characters/incoming/<spriteKey>.png  (캐릭터 없이 상품만, 투명 배경)
 * 출력: student-app/public/character/items/<slot>/<spriteKey>.png
 *
 * ## 왜 이 형태는 자동으로 되는가
 *
 * 합성 이미지(캐릭터+상품)는 상품만 떼어내야 하는데, 상품이 몸을 덮거나 몸에 가려지면
 * 정렬 기준이 사라져 실패한다. 상품만 있으면 그 문제가 없다. 남는 것은
 * "이 상품을 캐릭터의 어디에 얼마 크기로 놓을지"이고, 그건 슬롯별로 정해져 있다.
 *
 * 배치 규칙
 *   1) 원본의 타이트 bbox를 잡는다
 *   2) 비율을 유지하며 슬롯 영역 안에 들어가도록 축소한다
 *   3) 슬롯별 기준점에 맞춰 세로 위치를 정하고, 가로는 캔버스 중앙에 둔다
 *   4) 알파 이진화 + 색 단계 축소 + (대칭 상품이면) 좌우 대칭화
 *
 * 자동 배치가 어긋나면 --scale / --dx / --dy 로 조정한다. 원본 자체의 비례가
 * 캐릭터와 다르면(예: 머리보다 훨씬 긴 모자) 배율로는 못 고친다 — 그건 다시 그려야 한다.
 */
const fs = require('fs');
const path = require('path');
const { decode, encode, blank, blit } = require('./lib/png');
const { SHARED } = require('./lib/palette');

const ROOT = path.join(__dirname, '../..');
const SRC = path.join(ROOT, 'assets/characters/incoming');
const ITEMS = path.join(ROOT, 'student-app/public/character/items');
const BASE_PATH = path.join(ROOT, 'student-app/public/character/base/base_default.png');
const AUDIT = path.join(ROOT, 'artifacts/character-import');
const N = 64;
const NB8 = [[-1, -1], [0, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [0, 1], [1, 1]];

/**
 * 슬롯별 작화 영역과 세로 기준점.
 * 영역은 배포된 상품의 실측 bbox에서 뽑았다 (make-template.js와 같은 값).
 * anchor는 원본 bbox의 어디를 그 y에 맞출지 정한다.
 */
/**
 * `fitBy`
 *   width   — 몸을 감싸는 상품. 폭을 슬롯 폭에 맞추고 높이는 비율대로 둔다.
 *             영역을 세로로 넘겨도 된다. 원본이 캐릭터보다 몸통이 긴 비례로 그려지기
 *             때문에, 영역 안에 억지로 넣으면(contain) 상품이 몸 가운데 작게 떠 버린다.
 *   contain — 몸 옆·뒤에 붙는 상품. 영역 안에 비율 유지로 넣는다.
 */
const SLOT_FIT = {
  HAT: { x: [12, 51], y: [0, 18], anchor: 'bottom', at: 18, fitBy: 'width' },
  FACE: { x: [7, 56], y: [14, 28], anchor: 'center', at: 21, fitBy: 'contain' },
  NECK: { x: [13, 50], y: [24, 32], anchor: 'top', at: 24, fitBy: 'width' },
  TOP: { x: [9, 54], y: [25, 56], anchor: 'top', at: 26, fitBy: 'width' },
  BOTTOM: { x: [10, 53], y: [27, 59], anchor: 'bottom', at: 59, fitBy: 'width' },
  SHOES: { x: [11, 52], y: [49, 61], anchor: 'bottom', at: 61, fitBy: 'width' },
  BACK: { x: [1, 62], y: [18, 51], anchor: 'center', at: 36, fitBy: 'contain' },
  HAND: { x: [0, 63], y: [30, 50], anchor: 'center', at: 40, fitBy: 'contain' },
  EFFECT: { x: [0, 63], y: [30, 63], anchor: 'bottom', at: 62, fitBy: 'width' },
  BACKGROUND: { x: [0, 63], y: [0, 63], anchor: 'fill', at: 0, fitBy: 'fill' },
};

/** 디자인상 좌우가 다른 상품. 나머지는 대칭화한다. */
const ASYMMETRIC = new Set([
  'hat_red_cap', 'hat_wizard', 'hat_safari',
  'neck_scarf', 'neck_knit_scarf',
  'back_briefcase', 'back_dinosaur_tail',
  'hand_baseball_glove', 'hand_magic_wand', 'hand_toy_sword',
]);

const argv = process.argv.slice(2);
const DRY = argv.includes('--dry');
const num = (flag, dflt) => {
  const hit = argv.find((a) => a.startsWith(`--${flag}=`));
  return hit ? Number(hit.split('=')[1]) : dflt;
};
const SCALE_MUL = num('scale', 1);
const DX = num('dx', 0);
const DY = num('dy', 0);
const LEVELS = num('levels', 5);

function loadCore() {
  const { execFileSync } = require('child_process');
  const out = path.join(require('os').tmpdir(), `core-fit-${process.pid}.cjs`);
  execFileSync('node', [
    path.join(ROOT, 'student-app/node_modules/esbuild/bin/esbuild'),
    path.join(ROOT, 'student-app/expo/src/features/character/core/index.ts'),
    '--bundle', '--format=cjs', '--platform=node', '--outfile=' + out,
  ], { stdio: ['ignore', 'ignore', 'inherit'] });
  try { return require(out); } finally { fs.rmSync(out, { force: true }); }
}

const core = loadCore();
const targets = argv.includes('--all')
  ? core.CATALOG.map((c) => c.spriteKey).filter((k) => fs.existsSync(path.join(SRC, `${k}.png`)))
  : argv.filter((a) => !a.startsWith('--'));

if (!targets.length) {
  console.error('대상이 없다. 원본을 assets/characters/incoming/<spriteKey>.png 로 저장한다.');
  process.exit(1);
}

function tightBbox(img, t = 128) {
  const { width: W, height: H, rgba } = img;
  let x0 = W, y0 = H, x1 = -1, y1 = -1;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (rgba[(y * W + x) * 4 + 3] < t) continue;
    if (x < x0) x0 = x; if (y < y0) y0 = y;
    if (x > x1) x1 = x; if (y > y1) y1 = y;
  }
  return { x0, y0, x1, y1, w: x1 - x0 + 1, h: y1 - y0 + 1 };
}

const dist = (a, b) => 2 * (a[0] - b[0]) ** 2 + 4 * (a[1] - b[1]) ** 2 + 3 * (a[2] - b[2]) ** 2;
const toHex = (c) => '#' + c.map((v) => Math.max(0, Math.min(255, Math.round(v)))
  .toString(16).padStart(2, '0')).join('');

/**
 * 색을 k개 대표색으로 군집화한다 (k-means, 명도 순 결정론적 초기화).
 *
 * 채널별 균등 양자화를 쓰면 색이 엉뚱하게 밀린다 — 네이비 rgb(78,94,125)를 5단계로
 * 자르면 남보라가 됐다. 원본에 실제로 있는 색을 대표값으로 뽑아야 색조가 유지된다.
 */
function deriveRamp(samples, k) {
  if (!samples.length) return [];
  const lum = (c) => 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2];
  const sorted = samples.slice().sort((a, b) => lum(a) - lum(b));
  let centres = Array.from({ length: k }, (_, i) =>
    sorted[Math.min(sorted.length - 1, Math.floor((i + 0.5) * sorted.length / k))].slice());
  for (let iter = 0; iter < 14; iter++) {
    const sums = centres.map(() => [0, 0, 0, 0]);
    for (const s of samples) {
      let bi = 0, bd = Infinity;
      centres.forEach((c, i) => { const d = dist(s, c); if (d < bd) { bd = d; bi = i; } });
      sums[bi][0] += s[0]; sums[bi][1] += s[1]; sums[bi][2] += s[2]; sums[bi][3]++;
    }
    centres = centres.map((c, i) => (sums[i][3]
      ? [sums[i][0] / sums[i][3], sums[i][1] / sums[i][3], sums[i][2] / sums[i][3]]
      : c));
  }
  return centres;
}

const results = [];
for (const key of targets) {
  const from = path.join(SRC, `${key}.png`);
  const item = core.CATALOG.find((c) => c.spriteKey === key);
  if (!item) { console.error(`✗ 카탈로그에 없는 spriteKey: ${key}`); continue; }
  const fit = SLOT_FIT[item.slot];

  const img = decode(from);
  const box = tightBbox(img);
  if (box.w <= 0) { console.error(`✗ 내용이 없다: ${key}`); continue; }

  const zoneW = fit.x[1] - fit.x[0] + 1;
  const zoneH = fit.y[1] - fit.y[0] + 1;
  let tw;
  let th;
  if (fit.fitBy === 'fill') {
    tw = N; th = N;
  } else if (fit.fitBy === 'width') {
    // 폭만 맞춘다. 세로로 영역을 넘겨도 된다 (위 주석 참고)
    tw = Math.round(zoneW * SCALE_MUL);
    th = Math.round((tw * box.h) / box.w);
  } else {
    tw = Math.round(Math.min(zoneW, (zoneH * box.w) / box.h) * SCALE_MUL);
    th = Math.round((tw * box.h) / box.w);
    if (th > zoneH) { th = zoneH; tw = Math.round((th * box.w) / box.h); }
  }
  // 대칭 상품은 짝수 폭이어야 캔버스 중심에 정렬된다
  if (!ASYMMETRIC.has(key) && tw % 2 === 1) tw += 1;
  tw = Math.max(1, tw); th = Math.max(1, th);

  const ox = Math.round((N - tw) / 2) + DX;
  const oy = (fit.anchor === 'bottom' ? fit.at - th + 1
    : fit.anchor === 'center' ? Math.round(fit.at - th / 2)
      : fit.anchor === 'fill' ? 0 : fit.at) + DY;

  // 면적 평균 축소 + 알파 이진화. 색은 일단 평균값으로 담아 두고 뒤에서 군집화한다.
  const grid = Array.from({ length: N }, () => new Array(N).fill(null));
  const samples = [];
  for (let cy = 0; cy < th; cy++) for (let cx = 0; cx < tw; cx++) {
    const sx0 = Math.floor(box.x0 + (cx * box.w) / tw);
    const sx1 = Math.max(sx0 + 1, Math.ceil(box.x0 + ((cx + 1) * box.w) / tw));
    const sy0 = Math.floor(box.y0 + (cy * box.h) / th);
    const sy1 = Math.max(sy0 + 1, Math.ceil(box.y0 + ((cy + 1) * box.h) / th));
    let n = 0, r = 0, g = 0, b = 0, w = 0;
    for (let y = sy0; y < sy1; y++) for (let x = sx0; x < sx1; x++) {
      const i = (y * img.width + x) * 4;
      n++;
      const a = img.rgba[i + 3] / 255;
      if (a < 0.5) continue;
      r += img.rgba[i] * a; g += img.rgba[i + 1] * a; b += img.rgba[i + 2] * a; w += a;
    }
    if (!n || w / n < 0.45) continue; // 절반 미만 덮이면 비운다
    const px = ox + cx, py = oy + cy;
    if (px < 0 || py < 0 || px >= N || py >= N) continue;
    const rgb = [r / w, g / w, b / w];
    grid[py][px] = rgb;
    samples.push(rgb);
  }

  // 대표색으로 스냅한다. 잉크는 공용값을 쓰고 나머지는 원본에서 뽑은 램프 3색이다.
  const ramp = deriveRamp(samples, LEVELS - 1);
  const inkRgb = [1, 3, 5].map((i) => parseInt(SHARED.ink.slice(i, i + 2), 16));
  const palette = [inkRgb, ...ramp];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const c = grid[y][x];
    if (!c) continue;
    grid[y][x] = palette.reduce((p, q) => (dist(c, q) < dist(c, p) ? q : p)).map(Math.round);
  }

  // 좌우 대칭화 (합집합). 없는 쪽은 반대쪽 색을 그대로 가져온다.
  if (!ASYMMETRIC.has(key)) {
    for (let y = 0; y < N; y++) for (let x = 0; x < 32; x++) {
      const m = N - 1 - x;
      if (grid[y][x] && !grid[y][m]) grid[y][m] = grid[y][x].slice();
      else if (!grid[y][x] && grid[y][m]) grid[y][x] = grid[y][m].slice();
    }
  }

  // 고립 칸 제거
  const snap = grid.map((row) => row.map((c) => Boolean(c)));
  let kept = 0;
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    if (!grid[y][x]) continue;
    const near = NB8.filter(([dx, dy]) => snap[y + dy]?.[x + dx]).length;
    if (near <= 1) grid[y][x] = null;
    else kept++;
  }

  const out = blank(N, N);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const c = grid[y][x];
    if (!c) continue;
    const i = (y * N + x) * 4;
    out.rgba[i] = c[0]; out.rgba[i + 1] = c[1]; out.rgba[i + 2] = c[2]; out.rgba[i + 3] = 255;
  }

  console.log(
    `${key.padEnd(22)} ${img.width}x${img.height} bbox ${box.w}x${box.h}`
    + ` → ${tw}x${th} @x${ox},y${oy}  ${kept}칸`
    + `${ASYMMETRIC.has(key) ? ' (비대칭 유지)' : ' (대칭화)'}`,
  );

  if (!DRY) {
    const dir = path.join(ITEMS, item.slot.toLowerCase());
    fs.mkdirSync(dir, { recursive: true });
    encode(out, path.join(dir, `${key}.png`));
  }
  results.push({ key, out, slot: item.slot });
}

// ---- 검수 시트: 상품 단독 / 착용 모습
if (results.length) {
  fs.mkdirSync(AUDIT, { recursive: true });
  const base = decode(BASE_PATH);
  const Z = 7, GAP = 5, COLS = 8;
  const cells = [];
  for (const r of results) {
    cells.push(r.out);
    const worn = blank(N, N);
    // BACK·EFFECT는 몸 뒤, 나머지는 몸 앞
    if (r.slot === 'BACK' || r.slot === 'EFFECT' || r.slot === 'BACKGROUND') {
      blit(worn, r.out, 0, 0, 1); blit(worn, base, 0, 0, 1);
    } else {
      blit(worn, base, 0, 0, 1); blit(worn, r.out, 0, 0, 1);
    }
    cells.push(worn);
  }
  const rows = Math.ceil(cells.length / COLS);
  const sheet = blank(COLS * (N * Z + GAP) + GAP, rows * (N * Z + GAP) + GAP, [26, 28, 34, 255]);
  cells.forEach((im, i) => {
    const cx = GAP + (i % COLS) * (N * Z + GAP);
    const cy = GAP + Math.floor(i / COLS) * (N * Z + GAP);
    for (let y = 0; y < N * Z; y++) for (let x = 0; x < N * Z; x++) {
      const v = ((x >> 3) + (y >> 3)) % 2 ? 72 : 56;
      const di = ((cy + y) * sheet.width + cx + x) * 4;
      sheet.rgba[di] = v; sheet.rgba[di + 1] = v; sheet.rgba[di + 2] = v + 6; sheet.rgba[di + 3] = 255;
    }
    blit(sheet, im, cx, cy, Z);
  });
  const p = path.join(AUDIT, 'fitted-items.png');
  if (!DRY) encode(sheet, p);
  console.log(`\n검수 시트: ${path.relative(ROOT, p)} (칸마다 상품 단독 / 착용 모습)`);
  console.log('배치가 어긋나면 --scale=0.9 --dy=-2 처럼 조정한다. --dry 로 쓰기 없이 확인.');
}
