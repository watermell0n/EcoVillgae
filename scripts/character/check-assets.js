#!/usr/bin/env node
/**
 * 캐릭터 에셋 자동 검사. 규격 docs/design/CHARACTER_ASSET_SPEC.md §10
 *
 *   node scripts/character/check-assets.js
 *
 * 검사 항목
 *   1. 캔버스 크기 64×64
 *   2. 알파 0/255만
 *   3. 팔레트 등록 색만
 *   4. 좌우 대칭 (알파 + 잉크 + 볼, 예외 목록 제외)
 *   5. 캔버스 경계 픽셀
 *   6. 에셋 누락 (core 등록 ↔ 실제 파일 ↔ Expo require)
 *   7. 웹·Expo 사본 일치
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { decode } = require('./lib/png');
const { SHARED, allowedColors, SYMMETRY_ENFORCED_COLORS } = require('./lib/palette');

const ROOT = path.join(__dirname, '../..');
const WEB_ASSETS = path.join(ROOT, 'student-app/public/character');
const EXPO_ASSETS = path.join(ROOT, 'student-app/expo/assets/character');
const CORE = path.join(ROOT, 'student-app/expo/src/features/character/core/index.ts');
const EXPO_ASSET_MAP = path.join(ROOT, 'student-app/expo/src/features/character/assetMap.ts');

/**
 * 디자인상 좌우가 다른 상품. spriteKey로 적는다(파일 이름이 아니다).
 * 규격 §10.4
 *
 * 여기에 넣는 기준은 "형태가 한쪽으로 향하는 것이 디자인"인 경우다.
 * 대칭이어야 하는데 어긋난 것(선글라스·헤드폰·비니 등)은 넣지 않고 고친다.
 */
const SYMMETRY_EXCEPTIONS = new Set([
  'hat_red_cap', // 챙이 한쪽으로 뻗는다
  'hat_wizard',
  'hat_safari',
  'neck_scarf', // 꼬리가 한쪽으로 내려온다
  'neck_knit_scarf',
  'back_briefcase', // 몸 한쪽에 매단다
  'back_dinosaur_tail',
  'hand_baseball_glove',
  'hand_magic_wand',
  'hand_toy_sword',
]);

const errors = [];
const warnings = [];
const fail = (msg) => errors.push(msg);
const warn = (msg) => warnings.push(msg);

// ---------------------------------------------------------------- core 읽기
/**
 * core는 TypeScript ESM이고 상대 import에 확장자가 없어 Node가 그대로 읽지 못한다.
 * Expo 앱의 `typescript`로 CommonJS로 트랜스파일한 뒤 require한다.
 */
function loadCore() {
  const tsc = path.join(ROOT, 'student-app/expo/node_modules/typescript/bin/tsc');
  if (!fs.existsSync(tsc)) {
    throw new Error(
      'typescript를 찾을 수 없다. student-app/expo에서 `npm ci`를 실행한 뒤 다시 실행한다.',
    );
  }
  const coreDir = path.dirname(CORE);
  const out = fs.mkdtempSync(path.join(require('os').tmpdir(), 'character-core-'));
  try {
    execFileSync('node', [
      tsc, CORE,
      '--outDir', out,
      '--rootDir', coreDir,
      '--module', 'commonjs',
      '--target', 'ES2020',
      '--esModuleInterop',
      '--skipLibCheck',
      // tsc는 진단을 stdout으로 내보낸다. 삼키면 실패 원인이 보이지 않는다.
    ], { stdio: ['ignore', 'inherit', 'inherit'] });
    return require(path.join(out, 'index.js'));
  } finally {
    fs.rmSync(out, { force: true, recursive: true });
  }
}

const core = loadCore();
const requiredIds = core.requiredAssetIds();
const assetPaths = core.CHARACTER_ASSET_PATHS;
const spriteKeyOf = core.SPRITE_KEY_BY_ASSET_ID;

/**
 * 기존 부채 기준선.
 *
 * 규격 §11 2단계에서 기존 10종을 손볼 때까지 남는 위반이다. 파일별 현재 위반 수를
 * 못박아 두고, 이 수를 넘거나 목록에 없는 파일에서 위반이 나오면 실패시킨다.
 * 그래야 새로 추가되는 에셋에는 검사가 즉시 유효하다.
 *
 * 2단계에서 해당 항목을 고치면 여기서 지운다. 목록이 비면 이 상수를 제거한다.
 */
const BASELINE_PATH = path.join(__dirname, 'known-issues.json');
const baseline = fs.existsSync(BASELINE_PATH)
  ? JSON.parse(fs.readFileSync(BASELINE_PATH, 'utf8'))
  : {};
const observed = {};

function record(rel, kind, count) {
  if (!count) return;
  observed[rel] ??= {};
  observed[rel][kind] = count;
  const allowed = baseline[rel]?.[kind] ?? 0;
  if (count > allowed) {
    fail(
      `${rel}: ${kind} ${count}건 (기준선 ${allowed}건 초과)`
      + (allowed ? '' : ' — 새 위반이다'),
    );
  }
}

// ---------------------------------------------------------------- 6. 누락
const expoMapSource = fs.readFileSync(EXPO_ASSET_MAP, 'utf8');
for (const id of requiredIds) {
  const rel = assetPaths[id];
  if (!rel) {
    fail(`core에 경로가 없는 에셋 식별자: ${id}`);
    continue;
  }
  if (!fs.existsSync(path.join(WEB_ASSETS, rel))) {
    fail(`웹 에셋 파일 없음: character/${rel}`);
  }
  if (!fs.existsSync(path.join(EXPO_ASSETS, rel))) {
    fail(`Expo 에셋 파일 없음: character/${rel} — sync-assets.js 실행 필요`);
  }
  if (!new RegExp(`\\b${id}\\s*:\\s*require\\(`).test(expoMapSource)) {
    fail(`Expo assetMap.ts에 require가 없음: ${id} (Metro는 정적 경로만 담는다)`);
  }
}

// ---------------------------------------------------------------- 파일 순회
function walk(dir, base = dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, base, out);
    else if (entry.name.endsWith('.png')) {
      out.push(path.relative(base, full).split(path.sep).join('/'));
    }
  }
  return out;
}

const webFiles = walk(WEB_ASSETS).sort();
const idByPath = Object.fromEntries(Object.entries(assetPaths).map(([id, rel]) => [rel, id]));

for (const rel of webFiles) {
  const file = path.join(WEB_ASSETS, rel);
  const img = decode(file);
  const assetId = idByPath[rel];
  const isMask = rel.startsWith('masks/');
  const isBackground = rel.startsWith('items/background/');

  // 1. 캔버스 크기
  if (img.width !== 64 || img.height !== 64) {
    fail(`${rel}: 64×64 필요, 실제 ${img.width}×${img.height}`);
    continue;
  }

  const hex = (x, y) => {
    const i = (y * 64 + x) * 4;
    if (img.rgba[i + 3] === 0) return null;
    return '#' + [img.rgba[i], img.rgba[i + 1], img.rgba[i + 2]]
      .map((v) => v.toString(16).padStart(2, '0')).join('');
  };

  // 2. 알파 이진 / 3. 팔레트
  // 팔레트는 파일 이름이 아니라 상품 기준으로 조회한다
  // (face_headphones_back → face_headphones의 램프를 쓴다).
  const allowed = allowedColors(spriteKeyOf[assetId] ?? assetId);
  const badAlpha = new Set();
  const badColors = new Map();
  for (let i = 0; i < 64 * 64; i++) {
    const a = img.rgba[i * 4 + 3];
    if (a !== 0 && a !== 255) badAlpha.add(a);
    if (a === 0) continue;
    const h = '#' + [0, 1, 2].map((k) => img.rgba[i * 4 + k].toString(16).padStart(2, '0')).join('');
    if (!allowed.has(h)) badColors.set(h, (badColors.get(h) ?? 0) + 1);
  }
  if (badAlpha.size) {
    fail(`${rel}: 반투명 픽셀 (알파 ${[...badAlpha].join(', ')}) — 0 또는 255만 허용`);
  }
  if (badColors.size) {
    for (const [h, count] of badColors) {
      const hint = count <= 2 ? ' (리샘플링 잔여 픽셀 의심)' : '';
      // 배경은 색이 많아 팔레트 등록 대상이 아니다. 경고만 남긴다.
      if (isBackground) warn(`${rel}: 미등록 색 ${h} ×${count} (배경은 경고만)`);
      else warn(`${rel}: 미등록 색 ${h} ×${count}${hint} — 규격 §4에 등록하거나 제거한다`);
    }
    if (!isBackground) {
      record(rel, '미등록 색', [...badColors.values()].reduce((a, b) => a + b, 0));
    }
  }

  // 4. 대칭. 마스크는 상품에서 파생되므로 상품의 예외 여부를 따른다.
  const spriteKey = spriteKeyOf[assetId] ?? assetId ?? '';
  if (!SYMMETRY_EXCEPTIONS.has(spriteKey) && !isBackground) {
    let alphaBad = 0;
    let colorBad = 0;
    for (let y = 0; y < 64; y++) {
      for (let x = 0; x < 32; x++) {
        const a = hex(x, y);
        const b = hex(63 - x, y);
        if ((a === null) !== (b === null)) { alphaBad++; continue; }
        if (a === b) continue;
        if (SYMMETRY_ENFORCED_COLORS.has(a) || SYMMETRY_ENFORCED_COLORS.has(b)) colorBad++;
      }
    }
    record(rel, '실루엣 비대칭', alphaBad);
    record(rel, '잉크·볼 비대칭', colorBad);
  }

  // 5. 캔버스 경계
  if (!isBackground) {
    const edge = [];
    for (let x = 0; x < 64; x++) {
      if (hex(x, 0)) edge.push(`y0`);
      if (hex(x, 63)) edge.push(`y63`);
    }
    for (let y = 0; y < 64; y++) {
      if (hex(0, y)) edge.push(`x0`);
      if (hex(63, y)) edge.push(`x63`);
    }
    if (edge.length) {
      warn(`${rel}: 캔버스 경계에 픽셀이 닿는다 (${[...new Set(edge)].join(', ')}) — 잘려 보일 수 있다`);
    }
  }

  // 마스크는 단색
  if (isMask) {
    const colors = new Set();
    for (let i = 0; i < 64 * 64; i++) {
      if (img.rgba[i * 4 + 3] === 0) continue;
      colors.add([0, 1, 2].map((k) => img.rgba[i * 4 + k]).join(','));
    }
    if (colors.size > 1) fail(`${rel}: 가림 마스크는 단색이어야 한다 (${colors.size}색 발견)`);
  }
}

// ---------------------------------------------------------------- 7. 사본 일치
const expoFiles = walk(EXPO_ASSETS).sort();
if (webFiles.join('|') !== expoFiles.join('|')) {
  fail('웹과 Expo의 에셋 파일 목록이 다르다 — node scripts/character/sync-assets.js 실행');
} else {
  for (const rel of webFiles) {
    const a = fs.readFileSync(path.join(WEB_ASSETS, rel));
    const b = fs.readFileSync(path.join(EXPO_ASSETS, rel));
    if (!a.equals(b)) fail(`웹과 Expo의 ${rel} 내용이 다르다 — sync-assets.js 실행`);
  }
}

// ---------------------------------------------------------------- 진행 상황
const pending = [...core.PENDING_ASSET_KEYS];
const ready = Object.keys(core.CHARACTER_ITEMS).length;

console.log(`검사 대상 ${webFiles.length}개 파일 / 상품 에셋 ${ready}종 배포`);
if (pending.length) {
  console.log(
    `\n· 에셋 미배포 ${pending.length}종 (규격 §11 3·4단계 예정)\n  ${pending.join(', ')}`,
  );
}
const maskCount = Object.values(core.CHARACTER_ITEMS)
  .filter((definition) => definition.assets.occlusion).length;
console.log(`· 가림 마스크를 쓰는 상품 ${maskCount}종`);

const baselineFiles = Object.keys(baseline);
if (baselineFiles.length) {
  const remaining = baselineFiles.reduce(
    (sum, rel) => sum + Object.values(baseline[rel]).reduce((a, b) => a + b, 0),
    0,
  );
  console.log(
    `\n· 기존 부채 기준선: ${baselineFiles.length}개 파일 / ${remaining}건`
    + ' (규격 §11 2단계에서 해소)',
  );
  // 기준선보다 좋아졌으면 알려준다. 방치하면 기준선이 실제보다 느슨해진다.
  for (const rel of baselineFiles) {
    for (const [kind, allowed] of Object.entries(baseline[rel])) {
      const now = observed[rel]?.[kind] ?? 0;
      if (now < allowed) {
        warn(
          `${rel}: ${kind}이 ${allowed} → ${now}건으로 줄었다.`
          + ' scripts/character/known-issues.json을 갱신한다',
        );
      }
    }
  }
}

if (process.argv.includes('--write-baseline')) {
  fs.writeFileSync(BASELINE_PATH, `${JSON.stringify(observed, null, 2)}\n`);
  console.log(`\n기준선을 다시 기록했다: ${path.relative(ROOT, BASELINE_PATH)}`);
  process.exit(0);
}

for (const message of warnings) console.log(`\n⚠ ${message}`);
for (const message of errors) console.error(`\n✗ ${message}`);

if (errors.length) {
  console.error(`\n실패: ${errors.length}건`);
  process.exit(1);
}
console.log(`\n✅ 통과 (경고 ${warnings.length}건)`);
