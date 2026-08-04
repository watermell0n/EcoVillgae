#!/usr/bin/env node
/**
 * 정면 대칭 스프라이트의 좌우 구조를 맞춘다.
 *
 *   node scripts/character/normalize-symmetry.js items/hat/x.png [--ref=left|right] [--dry]
 *
 * 알파(실루엣)와 잉크 외곽선만 대칭화하고 **명암 램프는 건드리지 않는다.**
 * 전체를 미러링하면 좌상단 광원 표현이 사라져 그림이 평평해진다(규격 §2.2).
 *
 * 기준 절반은 불투명 픽셀이 더 많은 쪽을 자동 선택한다. 작가가 한쪽을 그린 뒤
 * 반대쪽을 옮기다 빠뜨린 경우가 대부분이어서, 더 채워진 쪽이 의도한 형태다.
 * --ref로 직접 지정할 수도 있다.
 *
 * 새로 불투명해지는 칸의 색은 **같은 쪽 이웃**에서 가져온다. 그래야 그쪽 면의
 * 밝기가 유지된다. 이웃이 없으면 기준 쪽 색을 쓴다.
 */
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./lib/png');
const { SHARED } = require('./lib/palette');

const ROOT = path.join(__dirname, '../..');
const ASSETS = path.join(ROOT, 'student-app/public/character');
const W = 64;

const argv = process.argv.slice(2);
const DRY = argv.includes('--dry');
const refArg = (argv.find((a) => a.startsWith('--ref=')) ?? '').split('=')[1];
const targets = argv.filter((a) => !a.startsWith('--'));

if (!targets.length) {
  console.error('대상 파일을 지정한다. 예: items/hat/hat_teal_beanie.png');
  process.exit(1);
}

const INK = SHARED.ink;

function run(rel) {
  const file = path.join(ASSETS, rel);
  const img = decode(file);
  if (img.width !== W || img.height !== W) {
    throw new Error(`${rel}: 64x64가 아니다`);
  }

  const idx = (x, y) => (y * W + x) * 4;
  const isClear = (x, y) => img.rgba[idx(x, y) + 3] === 0;
  const hex = (x, y) => {
    if (isClear(x, y)) return null;
    const i = idx(x, y);
    return '#' + [img.rgba[i], img.rgba[i + 1], img.rgba[i + 2]]
      .map((v) => v.toString(16).padStart(2, '0')).join('');
  };
  const put = (x, y, value) => {
    const i = idx(x, y);
    if (value === null) {
      img.rgba[i] = 0; img.rgba[i + 1] = 0; img.rgba[i + 2] = 0; img.rgba[i + 3] = 0;
      return;
    }
    img.rgba[i] = parseInt(value.slice(1, 3), 16);
    img.rgba[i + 1] = parseInt(value.slice(3, 5), 16);
    img.rgba[i + 2] = parseInt(value.slice(5, 7), 16);
    img.rgba[i + 3] = 255;
  };

  // 기준 절반 선택
  let leftCount = 0;
  let rightCount = 0;
  for (let y = 0; y < W; y++) {
    for (let x = 0; x < W / 2; x++) {
      if (!isClear(x, y)) leftCount++;
      if (!isClear(W - 1 - x, y)) rightCount++;
    }
  }
  const ref = refArg ?? (rightCount >= leftCount ? 'right' : 'left');

  /** 같은 쪽에서 색을 가져온다. 가로로 가장 가까운 불투명 비잉크 픽셀. */
  function neighbourColour(x, y, towardCentre) {
    for (let step = 1; step < W; step++) {
      const candidate = x + step * towardCentre;
      if (candidate < 0 || candidate >= W) break;
      const colour = hex(candidate, y);
      if (colour && colour !== INK) return colour;
    }
    return null;
  }

  let changed = 0;
  const log = [];

  for (let y = 0; y < W; y++) {
    for (let x = 0; x < W / 2; x++) {
      const mx = W - 1 - x;
      const srcX = ref === 'left' ? x : mx;
      const dstX = ref === 'left' ? mx : x;
      const src = hex(srcX, y);
      const dst = hex(dstX, y);

      // 구조가 같으면 손대지 않는다 (명암 차이는 허용)
      const srcInk = src === INK;
      const dstInk = dst === INK;
      if ((src === null) === (dst === null) && srcInk === dstInk) continue;

      let next;
      if (src === null) {
        next = null; // 기준이 비어 있으면 지운다
      } else if (srcInk) {
        next = INK; // 기준이 외곽선이면 외곽선
      } else {
        // 기준은 채워진 면. 대상이 이미 면이면 유지, 아니면 같은 쪽 이웃 색으로 채운다
        const towardCentre = dstX < W / 2 ? 1 : -1;
        next = (dst && !dstInk) ? dst : (neighbourColour(dstX, y, towardCentre) ?? src);
      }

      if (next === dst) continue;
      put(dstX, y, next);
      changed++;
      log.push(`y=${y} x${dstX}: ${dst ?? '없음'} → ${next ?? '없음'}`);
    }
  }

  console.log(`\n${rel}`);
  console.log(`  기준 절반: ${ref} (좌 ${leftCount}px / 우 ${rightCount}px)`);
  console.log(`  ${changed}px ${DRY ? '수정 예정' : '수정'}`);
  for (const line of log) console.log(`    ${line}`);

  if (changed && !DRY) encode(img, file);
  return changed;
}

let total = 0;
for (const rel of targets) total += run(rel);
console.log(`\n총 ${total}px ${DRY ? '수정 예정' : '수정 완료'}`);
