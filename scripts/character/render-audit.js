#!/usr/bin/env node
/**
 * 렌더 감사 스냅샷. 규격 docs/design/CHARACTER_ASSET_SPEC.md §10.7
 *
 *   node scripts/character/render-audit.js
 *
 * core의 planCharacterLayers로 레이어 순서를 계산하므로 웹·Expo와 같은 합성 결과다.
 * artifacts/character-audit/ 에 확대 시트를 남겨 PR에서 눈으로 확인한다.
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { decode, encode, blank, blit } = require('./lib/png');

const ROOT = path.join(__dirname, '../..');
const ASSETS = path.join(ROOT, 'student-app/public/character');
const CORE = path.join(ROOT, 'student-app/expo/src/features/character/core/index.ts');
const OUT = path.join(ROOT, 'artifacts/character-audit');

function loadCore() {
  const esbuild = path.join(ROOT, 'student-app/node_modules/esbuild/bin/esbuild');
  const tmp = path.join(require('os').tmpdir(), `character-core-audit-${process.pid}.cjs`);
  execFileSync('node', [
    esbuild, CORE, '--bundle', '--format=cjs', '--platform=node', '--outfile=' + tmp,
  ], { stdio: ['ignore', 'ignore', 'inherit'] });
  try {
    return require(tmp);
  } finally {
    fs.rmSync(tmp, { force: true });
  }
}

const core = loadCore();
const spriteCache = new Map();

function sprite(assetId) {
  if (spriteCache.has(assetId)) return spriteCache.get(assetId);
  const rel = core.CHARACTER_ASSET_PATHS[assetId];
  const image = rel && fs.existsSync(path.join(ASSETS, rel))
    ? decode(path.join(ASSETS, rel))
    : null;
  spriteCache.set(assetId, image);
  return image;
}

/** core가 계산한 레이어 순서대로 합성한다. 가림 마스크는 destination-out. */
function compose(equipped) {
  const { layers } = core.planCharacterLayers(equipped);
  const out = blank(core.CHARACTER_CANVAS_WIDTH, core.CHARACTER_CANVAS_HEIGHT);
  for (const layer of layers) {
    if (layer.occlusionAssetId) {
      const mask = sprite(layer.occlusionAssetId);
      if (mask) {
        for (let i = 0; i < out.rgba.length; i += 4) {
          if (mask.rgba[i + 3] === 0) continue;
          out.rgba[i] = 0; out.rgba[i + 1] = 0; out.rgba[i + 2] = 0; out.rgba[i + 3] = 0;
        }
      }
    }
    const image = sprite(layer.assetId);
    if (image) blit(out, image, 0, 0, 1);
  }
  return out;
}

const { CATALOG, EMPTY_EQUIPPED, CHARACTER_ITEMS } = core;

/** 대표 조합. 에셋이 없는 상품은 core가 알아서 건너뛴다. */
const FULL_SETS = {
  basic: { HAT: 'hat_red_cap', FACE: 'face_sunglasses', TOP: 'top_blue_tshirt', SHOES: 'shoes_sneakers', BACK: 'back_backpack' },
  summer: { HAT: 'hat_straw', FACE: 'face_goggles', TOP: 'top_swimsuit', SHOES: 'shoes_sandals', EFFECT: 'effect_swim_tube', BACKGROUND: 'bg_summer_sea' },
  winter: { HAT: 'hat_pom_beanie', NECK: 'neck_knit_scarf', TOP: 'top_padding', SHOES: 'shoes_snow_boots', BACKGROUND: 'bg_winter' },
  baseball: { HAT: 'hat_baseball_blue', TOP: 'top_baseball_blue', SHOES: 'shoes_sneakers', HAND: 'hand_baseball_glove' },
  space: { HAT: 'hat_space_helmet', TOP: 'top_spacesuit', SHOES: 'shoes_space_boots', BACKGROUND: 'bg_space' },
  wizard: { HAT: 'hat_wizard', TOP: 'top_wizard_robe', HAND: 'hand_magic_wand', BACK: 'back_fairy_wings' },
  knight: { HAT: 'hat_knight', TOP: 'top_fantasy_armor', HAND: 'hand_toy_sword' },
  dinosaur: { HAT: 'hat_dinosaur', TOP: 'top_dinosaur', BACK: 'back_dinosaur_tail' },
};

fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(OUT, { recursive: true });

const cells = [];

const baseImage = compose({ ...EMPTY_EQUIPPED });
encode(baseImage, path.join(OUT, 'base.png'));
cells.push({ label: 'base', img: baseImage, ready: true });

for (const item of CATALOG) {
  const ready = Boolean(CHARACTER_ITEMS[item.spriteKey]);
  const image = compose({ ...EMPTY_EQUIPPED, [item.slot]: item.id });
  if (ready) encode(image, path.join(OUT, `item_${item.spriteKey}.png`));
  cells.push({ label: item.spriteKey, img: image, ready });
}

for (const [name, set] of Object.entries(FULL_SETS)) {
  const image = compose({ ...EMPTY_EQUIPPED, ...set });
  encode(image, path.join(OUT, `fullset_${name}.png`));
  cells.push({ label: `set:${name}`, img: image, ready: true });
}

// ------------------------------------------------------------- 확대 시트
const S = 3;
const COLS = 9;
const GAP = 4;
const cw = 64 * S + GAP;
const ch = 64 * S + GAP;
const rows = Math.ceil(cells.length / COLS);
const sheet = blank(COLS * cw + GAP, rows * ch + GAP, [22, 26, 32, 255]);

cells.forEach((cell, index) => {
  const cx = GAP + (index % COLS) * cw;
  const cy = GAP + Math.floor(index / COLS) * ch;
  for (let y = 0; y < 64 * S; y++) {
    for (let x = 0; x < 64 * S; x++) {
      // 에셋이 없는 상품 칸은 배경을 붉게 물들여 한눈에 구분한다
      const checker = ((x >> 3) + (y >> 3)) % 2;
      const base = cell.ready ? [checker ? 64 : 52, checker ? 64 : 52, checker ? 70 : 58]
        : [checker ? 92 : 74, checker ? 52 : 42, checker ? 60 : 50];
      const di = ((cy + y) * sheet.width + cx + x) * 4;
      sheet.rgba[di] = base[0]; sheet.rgba[di + 1] = base[1];
      sheet.rgba[di + 2] = base[2]; sheet.rgba[di + 3] = 255;
    }
  }
  blit(sheet, cell.img, cx, cy, S);
});

encode(sheet, path.join(OUT, 'sheet.png'));
fs.writeFileSync(
  path.join(OUT, 'sheet-order.txt'),
  cells.map((c, i) => `${i}\t${c.ready ? 'ready  ' : 'PENDING'}\t${c.label}`).join('\n') + '\n',
);

const ready = cells.filter((c) => c.ready).length;
console.log(`${cells.length}칸 렌더 (에셋 있음 ${ready} / 없음 ${cells.length - ready})`);
console.log(`시트: ${path.relative(ROOT, path.join(OUT, 'sheet.png'))} (${sheet.width}×${sheet.height})`);
console.log('붉은 배경 칸 = 64×64 에셋 미배포 상품');
