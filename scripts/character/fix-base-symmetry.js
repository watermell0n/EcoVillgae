#!/usr/bin/env node
/**
 * BASE 캐릭터와 그에 딸린 상품 스프라이트의 좌우 대칭 결함을 고친다.
 * docs/design/CHARACTER_ASSET_SPEC.md §3.2 의 수정 목록을 그대로 코드로 옮긴 것이다.
 *
 * 일회성 아트 수정이지만, 무엇을 왜 바꿨는지 남기기 위해 스크립트로 관리한다.
 * 이미 수정된 에셋에 다시 실행해도 결과는 같다(멱등).
 *
 *   node scripts/character/fix-base-symmetry.js [--dry]
 */
const path = require('path');
const { decode, encode } = require('./lib/png');
const { SHARED } = require('./lib/palette');

const ASSETS = path.join(__dirname, '../../student-app/public/character');
const DRY = process.argv.includes('--dry');

const rgba = (hex) => [
  parseInt(hex.slice(1, 3), 16),
  parseInt(hex.slice(3, 5), 16),
  parseInt(hex.slice(5, 7), 16),
  255,
];
const CLEAR = [0, 0, 0, 0];

function range(a, b) {
  return Array.from({ length: b - a + 1 }, (_, i) => a + i);
}

/**
 * 각 항목: { rows, xs, to, why }
 * `to`가 null이면 투명으로 지운다.
 */
const BASE_EDITS = [
  {
    why: '§3.2-6 머리 좌측 안쪽 테두리 1px이 우측에는 잉크로 들어가 외곽선 두께가 달랐다',
    rows: [8, 9], xs: [42], to: SHARED.rim,
  },
  {
    why: '§3.2-1 얼굴 폭이 x14..48(합 62)이라 좌측이 1px 더 나갔다 → x15..48(합 63)',
    rows: range(13, 25), xs: [14], to: null,
  },
  {
    why: '§3.2-2 얼굴 우측 잉크가 2px, 좌측이 1px+테두리였다 → 양쪽 잉크 1px',
    rows: range(14, 25), xs: [47], to: SHARED.rim,
  },
  {
    why: '§3.2-2 y24만 안쪽 테두리 자리에 음영이 들어가 있었다',
    rows: [24], xs: [16], to: SHARED.rim,
  },
  {
    why: '§3.2-4 우측 볼이 2행(y24,25)뿐이고 좌측만 3행이었다 → 양쪽 3행',
    rows: [23], xs: [42, 43, 44], to: SHARED.cheek,
  },
  {
    why: '§3.2-2 입 행의 우측 잉크가 4px, 좌측이 3px+테두리였다',
    rows: [26], xs: [47], to: SHARED.rim,
  },
  {
    why: '§3.2-5 입이 x28..36(9px, 합 64)으로 홀수 폭이라 대칭 불가 → x28..35',
    rows: [26, 27], xs: [36], to: SHARED.body,
  },
  {
    why: '§3.2-5 입 좌측의 음영 1px이 우측에 대응이 없었다',
    rows: [27], xs: [27], to: SHARED.body,
  },
  {
    why: '§3.2-3 몸통이 x9..55(합 64)이라 우측이 1px 더 나갔다 → x9..54',
    rows: range(34, 43), xs: [55], to: null,
  },
  {
    why: '§3.2-2 허리 우측 잉크가 4px, 좌측이 3px였다',
    rows: [51], xs: [47], to: SHARED.shade,
  },
  {
    why: '§3.2-7 다리 구간이 x18..46(합 64)이라 우측이 1px 더 나갔다 → x18..45',
    rows: range(56, 58), xs: [46], to: null,
  },
  {
    why: '§3.2-8 우측 다리가 x34..46이라 좌측 x18..30과 어긋났다 → x33..45',
    rows: [57, 58], xs: [33], to: SHARED.ink,
  },
  {
    why: '§3.2-9 다리 사이 음영이 좌 8px / 우 9px이었다',
    rows: [56], xs: [44], to: SHARED.body,
  },
];

/**
 * BASE 실루엣 축소로 몸에 밀착한 채 1px 삐져나오게 된 상품 픽셀.
 *
 * 주의: 상품이 몸보다 넓은 것은 정상이다(모자·헤드폰·신발). 몸 실루엣 밖에 있다는
 * 이유만으로 지우면 상품 내부에 구멍이 난다. 여기에는 **원래 몸 외곽선과 좌표가
 * 겹쳐 있던 픽셀**만 넣는다. 상품 자체의 대칭 정리는 규격 §11 2단계에서 다룬다.
 */
const ITEM_EDITS = [
  {
    file: 'layers/basic/top_blue_tshirt.png',
    why: '티셔츠 우측 외곽선이 몸통 구 우측 끝 x55와 같은 좌표였다 → x54로',
    edits: [{ rows: range(33, 39), xs: [55], to: null }],
  },
];

function apply(file, edits) {
  const full = path.join(ASSETS, file);
  const img = decode(full);
  if (img.width !== 64 || img.height !== 64) {
    throw new Error(`${file}: 64x64가 아니다 (${img.width}x${img.height})`);
  }
  let changed = 0;
  for (const edit of edits) {
    const value = edit.to === null ? CLEAR : rgba(edit.to);
    for (const y of edit.rows) {
      for (const x of edit.xs) {
        const i = (y * 64 + x) * 4;
        const same = value.every((v, k) => img.rgba[i + k] === v);
        if (same) continue;
        for (let k = 0; k < 4; k++) img.rgba[i + k] = value[k];
        changed++;
      }
    }
  }
  if (changed && !DRY) encode(img, full);
  return changed;
}

console.log(DRY ? '[미리보기] 실제로 쓰지 않는다\n' : '');

let total = apply('base/base_char_base.png', BASE_EDITS);
console.log(`base/base_char_base.png  ${total}px 수정`);
for (const edit of BASE_EDITS) console.log(`  · ${edit.why}`);

for (const item of ITEM_EDITS) {
  const n = apply(item.file, item.edits);
  total += n;
  console.log(`\n${item.file}  ${n}px 수정`);
  console.log(`  · ${item.why}`);
}

console.log(`\n총 ${total}px ${DRY ? '수정 예정' : '수정 완료'}`);
