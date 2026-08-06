#!/usr/bin/env node
/**
 * 배경 원본 이미지 → 64×64 배경 에셋.
 *
 *   node scripts/character/convert-background.js bg_spring
 *   node scripts/character/convert-background.js --all
 *   node scripts/character/convert-background.js bg_spring --dry
 *
 * 입력: assets/characters/incoming/<spriteKey>.png (정사각형 권장)
 * 출력: student-app/public/character/items/background/<spriteKey>.png
 *
 * ## 왜 배경은 단순 축소로 되는가
 *
 * 캐릭터·상품은 단순 축소가 실패한다. 1px 외곽선이 평균에 묻히고, 좌우 대칭이 깨지고,
 * 몸과 좌표를 맞춰야 하기 때문이다. 배경에는 그 셋이 전부 해당되지 않는다.
 *
 *   - 보존할 얇은 외곽선이 없다
 *   - 좌우 대칭 검사 대상이 아니다 (check-assets.js가 배경을 제외한다)
 *   - 캔버스 전체를 채우므로 정렬할 기준점이 없다
 *   - 팔레트도 경고만 낸다
 *
 * 그래서 면적 평균 축소 + 알파 255로 충분하다. 다만 두 가지는 지켜야 한다.
 *   1) 알파는 255로 고정한다. 배경에 투명 픽셀이 있으면 뒤가 비어 보인다
 *   2) 색 수를 줄인다. 수십만 색을 그대로 두면 도트 느낌이 사라지고 파일도 커진다
 */
const fs = require('fs');
const path = require('path');
const { decode, encode, blank, blit } = require('./lib/png');

const ROOT = path.join(__dirname, '../..');
const SRC = path.join(ROOT, 'assets/characters/incoming');
const DST = path.join(ROOT, 'student-app/public/character/items/background');
const AUDIT = path.join(ROOT, 'artifacts/character-import');
const N = 64;

/** 카탈로그의 배경 5종 */
const KEYS = ['bg_spring', 'bg_summer_sea', 'bg_autumn', 'bg_winter', 'bg_space'];

const argv = process.argv.slice(2);
const DRY = argv.includes('--dry');
const LEVELS = Number((argv.find((a) => a.startsWith('--levels=')) ?? '').split('=')[1]) || 6;
const targets = argv.includes('--all')
  ? KEYS.filter((k) => fs.existsSync(path.join(SRC, `${k}.png`)))
  : argv.filter((a) => !a.startsWith('--'));

if (!targets.length) {
  console.error('대상이 없다. 원본을 assets/characters/incoming/<spriteKey>.png 로 저장한다.');
  console.error(`가능한 이름: ${KEYS.join(', ')}`);
  const present = KEYS.filter((k) => fs.existsSync(path.join(SRC, `${k}.png`)));
  console.error(present.length ? `현재 있는 원본: ${present.join(', ')}` : '현재 있는 원본: 없음');
  process.exit(1);
}

/** 정사각형이 아니면 가운데를 정사각형으로 잘라낸다. 배경은 중앙이 캐릭터 뒤라 중요하다. */
function squareBox(img) {
  const side = Math.min(img.width, img.height);
  return {
    x0: Math.floor((img.width - side) / 2),
    y0: Math.floor((img.height - side) / 2),
    w: side,
    h: side,
  };
}

/** 면적 평균 축소. 알파는 가중치로만 쓰고 결과는 항상 불투명하게 만든다. */
function downsample(img, box) {
  const out = blank(N, N);
  for (let cy = 0; cy < N; cy++) {
    for (let cx = 0; cx < N; cx++) {
      const sx0 = Math.floor(box.x0 + (cx * box.w) / N);
      const sx1 = Math.max(sx0 + 1, Math.floor(box.x0 + ((cx + 1) * box.w) / N));
      const sy0 = Math.floor(box.y0 + (cy * box.h) / N);
      const sy1 = Math.max(sy0 + 1, Math.floor(box.y0 + ((cy + 1) * box.h) / N));
      let r = 0, g = 0, b = 0, w = 0;
      for (let y = sy0; y < sy1; y++) {
        for (let x = sx0; x < sx1; x++) {
          const i = (y * img.width + x) * 4;
          const a = img.rgba[i + 3] / 255;
          if (a === 0) continue;
          r += img.rgba[i] * a; g += img.rgba[i + 1] * a; b += img.rgba[i + 2] * a;
          w += a;
        }
      }
      const o = (cy * N + cx) * 4;
      if (w === 0) { out.rgba[o + 3] = 255; continue; } // 투명 구멍은 검정 대신 불투명 처리
      out.rgba[o] = Math.round(r / w);
      out.rgba[o + 1] = Math.round(g / w);
      out.rgba[o + 2] = Math.round(b / w);
      out.rgba[o + 3] = 255;
    }
  }
  return out;
}

/**
 * 채널별 균등 양자화로 색 수를 줄인다.
 * k-means보다 결과가 예측 가능하고, 그라데이션이 계단으로 정리되어 도트답게 보인다.
 */
function posterise(img, levels) {
  const step = 255 / (levels - 1);
  for (let i = 0; i < img.rgba.length; i += 4) {
    for (let k = 0; k < 3; k++) {
      img.rgba[i + k] = Math.round(Math.round(img.rgba[i + k] / step) * step);
    }
  }
  return img;
}

function colourCount(img) {
  const set = new Set();
  for (let i = 0; i < img.rgba.length; i += 4) {
    set.add(`${img.rgba[i]},${img.rgba[i + 1]},${img.rgba[i + 2]}`);
  }
  return set.size;
}

fs.mkdirSync(AUDIT, { recursive: true });
const results = [];

for (const key of targets) {
  const from = path.join(SRC, `${key}.png`);
  if (!fs.existsSync(from)) {
    console.error(`✗ 원본 없음: assets/characters/incoming/${key}.png`);
    continue;
  }
  const img = decode(from);
  const box = squareBox(img);
  const out = posterise(downsample(img, box), LEVELS);
  const colours = colourCount(out);

  console.log(
    `${key.padEnd(16)} ${img.width}x${img.height}`
    + `${box.w === img.width && box.h === img.height ? '' : ` → 중앙 ${box.w}x${box.h} 크롭`}`
    + ` → 64x64 · ${colours}색`,
  );

  if (!DRY) {
    fs.mkdirSync(DST, { recursive: true });
    encode(out, path.join(DST, `${key}.png`));
  }
  results.push({ key, out });
}

// ---- 검수 시트: 배경 단독 + BASE 얹은 모습
if (results.length) {
  const basePath = path.join(ROOT, 'student-app/public/character/base/base_default.png');
  const base = fs.existsSync(basePath) ? decode(basePath) : null;
  const Z = 6, GAP = 5;
  const cols = results.length * 2;
  const sheet = blank(cols * (N * Z + GAP) + GAP, N * Z + GAP * 2, [26, 28, 34, 255]);
  results.forEach((r, i) => {
    blit(sheet, r.out, GAP + (i * 2) * (N * Z + GAP), GAP, Z);
    const worn = blank(N, N);
    blit(worn, r.out, 0, 0, 1);
    if (base) blit(worn, base, 0, 0, 1);
    blit(sheet, worn, GAP + (i * 2 + 1) * (N * Z + GAP), GAP, Z);
  });
  const sheetPath = path.join(AUDIT, 'backgrounds.png');
  if (!DRY) encode(sheet, sheetPath);
  console.log(`\n검수 시트: ${path.relative(ROOT, sheetPath)} (칸마다 배경 단독 / BASE 얹음)`);
}

if (!DRY) {
  console.log('\n다음: node scripts/character/sync-assets.js && node scripts/character/check-assets.js');
}
