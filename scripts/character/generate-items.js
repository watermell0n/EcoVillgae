#!/usr/bin/env node
'use strict';
/**
 * 미배포 상품의 64×64 스프라이트를 생성한다. 규격 §11 3·4단계.
 *
 *   node scripts/character/generate-items.js            전체 생성
 *   node scripts/character/generate-items.js hat_chef …  일부만 생성
 *
 * 좌표는 전부 규격 §3.1 실루엣과 §3.4 앵커를 기준으로 잡았다. 48×56 절차적 렌더러
 * (docs/legacy/pixel-renderer-48x56)의 형태를 참고했지만 배율 변환이 아니라 재작화다.
 *
 * 대칭은 draw.js의 `sym`(left + right === 63 강제)과 `pair`(미러 쌍 동시 작화)로
 * 보장한다. 그래서 check-assets.js의 대칭 검사에 새 위반이 생길 수 없다.
 * 좌우가 다른 것이 디자인인 상품만 rect를 직접 쓰고 check-assets.js의
 * SYMMETRY_EXCEPTIONS에 등록되어 있다.
 */
const fs = require('fs');
const path = require('path');
const { Sprite } = require('./lib/draw');
const { SHARED, ITEM_RAMPS } = require('./lib/palette');

const ROOT = path.join(__dirname, '../..');
const OUT = path.join(ROOT, 'student-app/public/character/items');

const INK = SHARED.ink;
const WHITE = SHARED.body;
const RIM = SHARED.rim;
const CHEEK = SHARED.cheek;

/** 상품 램프. [base, mid, dark, ...포인트] */
function ramp(key) {
  const colors = ITEM_RAMPS[key];
  if (!colors) throw new Error(`팔레트에 램프가 없다: ${key} — lib/palette.js에 등록한다`);
  return colors;
}

// ─────────────────────────────────────────────────────────── 공용 실루엣 조각

/** 야구모자·캡 크라운. 머리(y4..12)를 1px 여유로 덮는다. */
const CAP_CROWN = [
  [3, 28, 35], [4, 25, 38], [5, 22, 41], [6, 20, 43], [7, 19, 44],
  [8, 18, 45], [9, 17, 46], [10, 16, 47], [11, 16, 47], [12, 16, 47],
];
/** 정면 챙. 좌우 대칭이므로 앞으로 뻗은 모습이 된다. */
const CAP_BRIM = [[13, 14, 49], [14, 12, 51], [15, 13, 50]];

const BEANIE_CROWN = [
  [5, 25, 38], [6, 22, 41], [7, 20, 43], [8, 18, 45],
  [9, 17, 46], [10, 16, 47], [11, 16, 47], [12, 16, 47],
];
const BEANIE_CUFF = [[13, 15, 48], [14, 15, 48], [15, 15, 48], [16, 15, 48]];

/** 정모(경찰·파일럿) 크라운 + 굽은 챙. */
const PEAK_CROWN = [
  [5, 25, 38], [6, 22, 41], [7, 20, 43], [8, 19, 44],
  [9, 18, 45], [10, 17, 46], [11, 17, 46],
];
const PEAK_BAND = [[12, 16, 47], [13, 16, 47]];
const PEAK_VISOR = [[14, 14, 49], [15, 12, 51], [16, 14, 49]];

/** y0..y1 사이 행을 같은 폭으로 채우는 행 목록. */
function block(fromY, toY, left, right) {
  const rows = [];
  for (let y = fromY; y <= toY; y += 1) rows.push([y, left, right]);
  return rows;
}

// ─────────────────────────────────────────────────────────── 의상 공용

/** 반팔·유니폼. 엉덩이 위에서 끝난다. */
function shirtBase(sprite, color) {
  sprite.garment(28, 49, 0, color);
  sprite.neckline(28, 30, 25, 38);
}

/** 겉옷. 몸보다 1px 두껍고 엉덩이까지 내려온다. */
function jacketBase(sprite, color) {
  sprite.garment(27, 51, 1, color);
  sprite.neckline(27, 30, 25, 38);
}

/** 전신 의상. coversSlots: ['BOTTOM']과 함께 쓴다. */
function fullBodyBase(sprite, color, { toY = 55 } = {}) {
  sprite.garment(27, toY, 1, color);
  sprite.neckline(27, 29, 25, 38);
}

/** 야구 유니폼 공통: 앞단추 라인 + 벨트. */
function baseballJersey(sprite, key, { placket = WHITE } = {}) {
  const [base, mid, dark] = ramp(key);
  shirtBase(sprite, base);
  sprite.shadeRamp(base, mid, dark);
  // 앞단추 라인
  sprite.symRect(31, 30, 33, 13, placket);
  sprite.symRect(31, 31, 32, 13, dark);
  // 벨트
  sprite.symRect(44, 10, 53, 3, dark);
  sprite.sym(44, 12, 51, mid);
  sprite.outline(INK);
}

// ─────────────────────────────────────────────────────────── 상품 정의
// key → { slot, parts: { main?|behind?|front?: (sprite) => void } }

const ITEMS = {};

function item(key, slot, parts) {
  ITEMS[key] = { slot, parts };
}

// ── 모자 17종 ──────────────────────────────────────────────

/** 야구 모자 5색. 앞면에 흰 로고 패치를 둔다. */
for (const key of [
  'hat_baseball_blue', 'hat_baseball_red', 'hat_baseball_navy',
  'hat_baseball_green', 'hat_baseball_yellow',
]) {
  item(key, 'HAT', {
    main(sprite) {
      const [base, mid, dark] = ramp(key);
      sprite.symRows(CAP_CROWN, base);
      sprite.symRows(CAP_BRIM, base);
      sprite.shadeRamp(base, mid, dark);
      // 크라운 이음선과 로고
      sprite.symRect(6, 31, 32, 6, mid);
      sprite.symRect(7, 29, 34, 4, WHITE);
      sprite.symRect(13, 15, 48, 2, mid);
      sprite.outline(INK);
    },
  });
}

item('hat_straw', 'HAT', {
  main(sprite) {
    const [base, mid, dark, band] = ramp('hat_straw');
    sprite.symRows([
      [4, 26, 37], [5, 24, 39], [6, 23, 40], [7, 22, 41], [8, 22, 41], [9, 22, 41],
    ], base);
    sprite.symRows([[10, 21, 42], [11, 21, 42]], base);
    sprite.symRows([[12, 14, 49], [13, 11, 52], [14, 9, 54], [15, 11, 52], [16, 14, 49]], base);
    sprite.shadeRamp(base, mid, dark);
    sprite.symRect(10, 21, 42, 2, band);
    sprite.sym(13, 14, 49, mid);
    sprite.outline(INK);
  },
});

item('hat_beach', 'HAT', {
  main(sprite) {
    const [base, mid, dark, band] = ramp('hat_beach');
    sprite.symRows([
      [4, 26, 37], [5, 24, 39], [6, 22, 41], [7, 22, 41], [8, 22, 41], [9, 22, 41],
    ], base);
    sprite.symRows([[10, 20, 43], [11, 20, 43]], base);
    sprite.symRows([[12, 13, 50], [13, 10, 53], [14, 9, 54], [15, 10, 53], [16, 13, 50]], base);
    sprite.shadeRamp(base, mid, dark);
    sprite.symRect(10, 20, 43, 2, band);
    sprite.sym(14, 12, 51, band);
    sprite.outline(INK);
  },
});

item('hat_pom_beanie', 'HAT', {
  main(sprite) {
    const [base, mid, dark] = ramp('hat_pom_beanie');
    // 방울
    sprite.symRows([[1, 30, 33], [2, 29, 34], [3, 29, 34], [4, 30, 33]], WHITE);
    sprite.symRows(BEANIE_CROWN, base);
    sprite.symRows(BEANIE_CUFF, base);
    sprite.shadeRamp(base, mid, dark);
    // 니트 결
    for (const x of [19, 25, 31]) {
      sprite.pairRect(7, x, x + 1, 5, mid);
    }
    sprite.symRect(13, 15, 48, 3, dark);
    sprite.sym(13, 17, 46, mid);
    sprite.outline(INK);
  },
});

item('hat_chef', 'HAT', {
  main(sprite) {
    const [base, mid, dark] = ramp('hat_chef');
    sprite.symRows([
      [1, 28, 35], [2, 25, 38], [3, 22, 41], [4, 20, 43],
      [5, 19, 44], [6, 19, 44], [7, 20, 43], [8, 22, 41],
    ], base);
    sprite.symRows(block(9, 14, 17, 46), base);
    sprite.shadeRamp(base, mid, dark);
    // 주름
    for (const x of [24, 30]) sprite.pairRect(3, x, x + 1, 5, mid);
    sprite.symRect(11, 17, 46, 2, mid);
    sprite.outline(INK);
  },
});

item('hat_safari', 'HAT', {
  main(sprite) {
    const [base, mid, dark, band] = ramp('hat_safari');
    sprite.symRows([
      [4, 26, 37], [5, 24, 39], [6, 22, 41], [7, 21, 42], [8, 20, 43], [9, 19, 44],
    ], base);
    sprite.symRows([[10, 19, 44], [11, 19, 44]], base);
    sprite.symRows([[12, 13, 50], [13, 11, 52], [14, 13, 50]], base);
    sprite.shadeRamp(base, mid, dark);
    sprite.symRect(10, 19, 44, 2, band);
    // 통기구멍
    sprite.pairRect(7, 22, 23, 2, band);
    sprite.outline(INK);
  },
});

item('hat_space_helmet', 'HAT', {
  main(sprite) {
    const [base, mid, dark, accent] = ramp('hat_space_helmet');
    sprite.symRows([
      [2, 28, 35], [3, 24, 39], [4, 21, 42], [5, 18, 45],
      [6, 16, 47], [7, 15, 48], [8, 14, 49],
    ], base);
    sprite.symRows(block(9, 21, 13, 50), base);
    sprite.symRows([
      [22, 14, 49], [23, 15, 48], [24, 17, 46], [25, 19, 44], [26, 22, 41],
    ], base);
    sprite.shadeRamp(base, mid, dark);
    // 목 링
    sprite.symRect(25, 19, 44, 2, accent);
    // 좌상단 광원 하이라이트. 램프이므로 좌우가 달라도 된다(규격 §2.2).
    sprite.rect(22, 4, 8, 3, WHITE);
    // 바이저를 둥글게 파낸다. BASE의 눈·볼이 그대로 보이므로 캐릭터가 사라지지 않는다.
    sprite.symRows([
      [10, 22, 41], [11, 20, 43], [12, 19, 44],
      ...block(13, 21, 18, 45),
      [22, 19, 44], [23, 21, 42],
    ], null);
    sprite.outline(INK);
  },
});

item('hat_safety_police', 'HAT', {
  main(sprite) {
    const [base, mid, dark, gold] = ramp('hat_safety_police');
    sprite.symRows(PEAK_CROWN, base);
    sprite.symRows(PEAK_BAND, base);
    sprite.symRows(PEAK_VISOR, base);
    sprite.shadeRamp(base, mid, dark);
    sprite.symRect(12, 16, 47, 2, gold);
    // 모표
    sprite.symRect(7, 29, 34, 3, gold);
    sprite.sym(8, 30, 33, WHITE);
    sprite.outline(INK);
  },
});

item('hat_pilot', 'HAT', {
  main(sprite) {
    const [base, mid, dark, navy, gold] = ramp('hat_pilot');
    sprite.symRows(PEAK_CROWN, base);
    sprite.shadeRamp(base, mid, dark);
    sprite.symRows(PEAK_BAND, navy);
    sprite.symRows(PEAK_VISOR, navy);
    // 금장
    sprite.symRect(14, 16, 47, 1, gold);
    sprite.symRect(7, 28, 35, 3, gold);
    sprite.sym(8, 30, 33, navy);
    sprite.outline(INK);
  },
});

item('hat_crown', 'HAT', {
  main(sprite) {
    const [base, mid, dark, jewel] = ramp('hat_crown');
    sprite.sym(3, 30, 33, base);
    sprite.sym(4, 29, 34, base);
    sprite.pair(4, 24, 25, base);
    sprite.sym(5, 28, 35, base);
    sprite.pair(5, 23, 26, base);
    sprite.sym(6, 20, 43, base);
    sprite.symRows(block(7, 12, 19, 44), base);
    sprite.shadeRamp(base, mid, dark);
    sprite.symRect(11, 19, 44, 2, mid);
    // 보석 3개
    sprite.pairRect(8, 24, 25, 2, jewel);
    sprite.symRect(8, 31, 32, 2, jewel);
    sprite.outline(INK);
  },
});

item('hat_wizard', 'HAT', {
  main(sprite) {
    const [base, mid, dark, gold] = ramp('hat_wizard');
    // 한쪽으로 기운 고깔. SYMMETRY_EXCEPTIONS 등록 상품이다.
    const cone = [
      [2, 37, 40], [3, 35, 40], [4, 33, 40], [5, 31, 39], [6, 29, 38],
      [7, 27, 38], [8, 25, 39], [9, 23, 41], [10, 21, 43], [11, 19, 45],
    ];
    for (const [y, left, right] of cone) sprite.rect(left, y, right - left + 1, 1, base);
    for (const [y, left, right] of [[12, 13, 50], [13, 11, 52], [14, 13, 50]]) {
      sprite.rect(left, y, right - left + 1, 1, base);
    }
    sprite.shadeRamp(base, mid, dark);
    sprite.rect(12, 12, 39, 1, gold);
    sprite.rect(33, 6, 3, 3, gold);
    sprite.outline(INK);
  },
});

item('hat_knight', 'HAT', {
  main(sprite) {
    const [base, mid, dark, light] = ramp('hat_knight');
    sprite.symRows([
      [3, 26, 37], [4, 22, 41], [5, 19, 44], [6, 17, 46], [7, 16, 47],
    ], base);
    sprite.symRows(block(8, 21, 15, 48), base);
    sprite.symRows([
      [22, 16, 47], [23, 17, 46], [24, 19, 44], [25, 22, 41],
    ], base);
    sprite.shadeRamp(base, mid, dark);
    // 정수리 능선
    sprite.symRect(4, 30, 33, 4, light);
    // 바이저 슬릿과 통기 살
    sprite.symRect(16, 18, 45, 4, dark);
    sprite.symRect(17, 20, 43, 2, INK);
    for (const x of [21, 26, 30] ) sprite.pairRect(17, x, x + 1, 2, dark);
    sprite.symRect(22, 18, 45, 1, light);
    sprite.outline(INK);
  },
});

item('hat_dinosaur', 'HAT', {
  main(sprite) {
    const [base, mid, dark, spike] = ramp('hat_dinosaur');
    sprite.symRows([
      [3, 25, 38], [4, 21, 42], [5, 18, 45], [6, 16, 47], [7, 15, 48],
    ], base);
    sprite.symRows(block(8, 24, 14, 49), base);
    sprite.symRows([[25, 15, 48], [26, 17, 46], [27, 20, 43]], base);
    sprite.shadeRamp(base, mid, dark);
    // 등 지느러미
    sprite.pairRect(2, 22, 25, 3, spike);
    sprite.symRect(1, 30, 33, 4, spike);
    // 후드 테두리
    sprite.symRect(8, 14, 49, 2, mid);
    // 얼굴 창을 파내 BASE 얼굴이 보이게 한다.
    sprite.neckline(12, 26, 18, 45);
    sprite.outline(INK);
  },
});

// ── 얼굴 2종 ───────────────────────────────────────────────

item('face_goggles', 'FACE', {
  main(sprite) {
    const [base, mid, dark, glass] = ramp('face_goggles');
    // 머리끈
    sprite.pairRect(19, 14, 18, 3, dark);
    // 렌즈 두 짝 (눈 x24..25 / x38..39, y19..23을 덮는다)
    sprite.pairRect(17, 19, 29, 8, base);
    // 코 연결부
    sprite.symRect(20, 30, 33, 2, dark);
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(18, 21, 25, 2, glass);
    sprite.outline(INK);
  },
});

item('face_ski_goggles', 'FACE', {
  main(sprite) {
    const [base, mid, dark, light] = ramp('face_ski_goggles');
    // 머리끈
    sprite.symRect(18, 13, 50, 4, dark);
    // 고글 프레임
    sprite.symRect(16, 17, 46, 9, dark);
    // 렌즈
    sprite.symRect(18, 19, 44, 5, base);
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(19, 21, 26, 2, light);
    sprite.outline(INK);
  },
});

// ── 목 장식 2종 ────────────────────────────────────────────
// 꼬리가 한쪽으로 내려오므로 SYMMETRY_EXCEPTIONS 등록 상품이다.

for (const key of ['neck_scarf', 'neck_knit_scarf']) {
  item(key, 'NECK', {
    main(sprite) {
      const [base, mid, dark, light] = ramp(key);
      sprite.symRows([
        [24, 18, 45], [25, 16, 47], [26, 15, 48], [27, 15, 48], [28, 16, 47], [29, 18, 45],
      ], base);
      // 꼬리 (규격 §9.6: 몸통 측면을 따라 내려오고 캔버스를 넘지 않는다)
      sprite.rect(44, 30, 7, 15, base);
      sprite.rect(44, 45, 7, 2, dark);
      sprite.shadeRamp(base, mid, dark);
      sprite.symRect(26, 17, 46, 2, light);
      sprite.rect(45, 34, 2, 8, light);
      sprite.outline(INK);
    },
  });
}

// ── 상의 15종 ──────────────────────────────────────────────

item('top_swimsuit', 'TOP', {
  main(sprite) {
    const [base, mid, dark] = ramp('top_swimsuit');
    // 어깨끈
    sprite.pairRect(28, 18, 21, 4, base);
    sprite.garment(32, 47, 0, base);
    sprite.shadeRamp(base, mid, dark);
    sprite.symRect(37, 9, 54, 2, WHITE);
    sprite.outline(INK);
  },
});

item('top_padding', 'TOP', {
  main(sprite) {
    const [base, mid, dark, light] = ramp('top_padding');
    jacketBase(sprite, base);
    sprite.shadeRamp(base, mid, dark);
    // 퀼팅 가로선
    for (const y of [33, 38, 43, 48]) sprite.symRect(y, 9, 54, 1, dark);
    for (const y of [34, 39, 44]) sprite.symRect(y, 11, 52, 1, light);
    // 지퍼
    sprite.symRect(31, 31, 32, 20, mid);
    sprite.outline(INK);
  },
});

item('top_soccer', 'TOP', {
  main(sprite) {
    const [base, mid, dark] = ramp('top_soccer');
    shirtBase(sprite, base);
    sprite.shadeRamp(base, mid, dark);
    // V넥과 등번호
    sprite.symRect(30, 26, 37, 2, WHITE);
    sprite.symRect(36, 28, 35, 8, WHITE);
    sprite.symRect(38, 30, 33, 4, base);
    sprite.symRect(46, 9, 54, 2, dark);
    sprite.outline(INK);
  },
});

item('top_baseball_pinstripe', 'TOP', {
  main(sprite) {
    const [base, mid, dark, stripe] = ramp('top_baseball_pinstripe');
    shirtBase(sprite, base);
    sprite.shadeRamp(base, mid, dark);
    for (const x of [16, 22, 28]) sprite.pairRect(31, x, x, 13, stripe);
    sprite.symRect(31, 30, 33, 13, stripe);
    sprite.symRect(44, 10, 53, 3, stripe);
    sprite.sym(44, 12, 51, base);
    sprite.outline(INK);
  },
});

for (const key of ['top_baseball_red', 'top_baseball_blue', 'top_baseball_navy']) {
  item(key, 'TOP', { main: (sprite) => baseballJersey(sprite, key) });
}

item('top_baseball_gray', 'TOP', {
  main(sprite) {
    baseballJersey(sprite, 'top_baseball_gray', { placket: ramp('top_baseball_gray')[3] });
  },
});

item('top_spacesuit', 'TOP', {
  main(sprite) {
    const [base, mid, dark, panel, light] = ramp('top_spacesuit');
    fullBodyBase(sprite, base);
    sprite.shadeRamp(base, mid, dark);
    // 가슴 제어판
    sprite.symRect(34, 24, 39, 8, dark);
    sprite.symRect(35, 26, 37, 6, panel);
    sprite.pairRect(36, 27, 28, 2, light);
    // 허리 링과 다리 이음선
    sprite.symRect(45, 11, 52, 2, mid);
    sprite.symRect(50, 13, 50, 2, mid);
    sprite.outline(INK);
  },
});

item('top_safety_police', 'TOP', {
  main(sprite) {
    const [base, mid, dark, gold] = ramp('top_safety_police');
    jacketBase(sprite, base);
    sprite.shadeRamp(base, mid, dark);
    // 셔츠 깃과 넥타이
    sprite.symRect(29, 26, 37, 3, WHITE);
    sprite.symRect(31, 30, 33, 12, dark);
    // 배지와 벨트
    sprite.pairRect(35, 15, 18, 4, gold);
    sprite.symRect(45, 10, 53, 3, dark);
    sprite.symRect(45, 29, 34, 3, gold);
    sprite.outline(INK);
  },
});

item('top_legal_professional', 'TOP', {
  main(sprite) {
    const [base, mid, dark, tie] = ramp('top_legal_professional');
    jacketBase(sprite, base);
    sprite.shadeRamp(base, mid, dark);
    // 셔츠와 넥타이
    sprite.symRect(29, 25, 38, 3, WHITE);
    sprite.symRect(32, 28, 35, 12, WHITE);
    sprite.symRect(32, 30, 33, 11, tie);
    // 라펠
    sprite.pairRect(30, 20, 24, 6, mid);
    sprite.outline(INK);
  },
});

item('top_pilot', 'TOP', {
  main(sprite) {
    const [base, mid, dark, navy, gold] = ramp('top_pilot');
    jacketBase(sprite, base);
    sprite.shadeRamp(base, mid, dark);
    // 넥타이
    sprite.symRect(29, 26, 37, 2, mid);
    sprite.symRect(31, 30, 33, 13, navy);
    // 견장
    sprite.pairRect(30, 13, 19, 3, gold);
    // 벨트
    sprite.symRect(45, 10, 53, 3, navy);
    sprite.symRect(45, 29, 34, 3, gold);
    sprite.outline(INK);
  },
});

item('top_wizard_robe', 'TOP', {
  main(sprite) {
    const [base, mid, dark, gold] = ramp('top_wizard_robe');
    fullBodyBase(sprite, base);
    // 아래로 퍼지는 자락
    sprite.symRows([[52, 13, 50], [53, 12, 51], [54, 11, 52], [55, 11, 52]], base);
    sprite.shadeRamp(base, mid, dark);
    // 금색 트임과 허리끈
    sprite.symRect(31, 30, 33, 20, gold);
    sprite.symRect(43, 8, 55, 2, gold);
    // 별
    sprite.pairRect(36, 16, 17, 2, gold);
    sprite.pairRect(48, 18, 19, 2, gold);
    sprite.outline(INK);
  },
});

item('top_fantasy_armor', 'TOP', {
  main(sprite) {
    const [base, mid, dark, gold, leather] = ramp('top_fantasy_armor');
    fullBodyBase(sprite, base, { toY: 51 });
    sprite.shadeRamp(base, mid, dark);
    // 가슴판
    sprite.symRect(31, 22, 41, 10, mid);
    sprite.symRect(32, 24, 39, 8, base);
    sprite.symRect(33, 30, 33, 7, gold);
    // 어깨 갑주
    sprite.pairRect(30, 8, 15, 5, mid);
    // 가죽 벨트
    sprite.symRect(43, 8, 55, 3, leather);
    sprite.symRect(43, 29, 34, 3, gold);
    // 치마 갑주
    sprite.symRows([[48, 11, 52], [49, 11, 52], [50, 12, 51], [51, 12, 51]], mid);
    sprite.outline(INK);
  },
});

item('top_dinosaur', 'TOP', {
  main(sprite) {
    const [base, mid, dark, belly, spike] = ramp('top_dinosaur');
    fullBodyBase(sprite, base);
    sprite.shadeRamp(base, mid, dark);
    // 배
    sprite.symRect(33, 22, 41, 14, belly);
    sprite.symRect(47, 15, 48, 3, belly);
    // 등 지느러미가 어깨 위로 솟는다
    sprite.pairRect(29, 10, 13, 3, spike);
    sprite.pairRect(38, 8, 10, 3, spike);
    sprite.outline(INK);
  },
});

// ── 신발 5종 ───────────────────────────────────────────────

/** 발(y57..60, x19..28 / x35..44)을 감싸는 기본 발등·바닥. */
function shoeSole(sprite, color, { left = 15, right = 29 } = {}) {
  sprite.pair(56, left + 4, right - 1, color);
  sprite.pair(57, left + 2, right, color);
  sprite.pairRect(58, left + 1, right, 1, color);
  sprite.pairRect(59, left, right, 3, color);
}

item('shoes_red_boots', 'SHOES', {
  main(sprite) {
    const [base, mid, dark, light] = ramp('shoes_red_boots');
    sprite.pairRect(50, 18, 28, 6, base);
    shoeSole(sprite, base, { left: 16, right: 29 });
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(50, 18, 28, 2, light);
    sprite.pairRect(60, 16, 29, 2, dark);
    sprite.outline(INK);
  },
});

item('shoes_sandals', 'SHOES', {
  main(sprite) {
    const [base, mid, dark] = ramp('shoes_sandals');
    // 밑창 4px (규격 §9.4: 최소 3px)
    sprite.pairRect(58, 16, 29, 1, base);
    sprite.pairRect(59, 15, 29, 3, base);
    // 발등 스트랩
    sprite.pair(55, 19, 27, base);
    sprite.pair(56, 18, 28, base);
    sprite.pair(57, 17, 29, base);
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(55, 20, 26, 2, WHITE);
    sprite.pairRect(60, 15, 29, 2, dark);
    sprite.outline(INK);
  },
});

item('shoes_aqua', 'SHOES', {
  main(sprite) {
    const [base, mid, dark, light] = ramp('shoes_aqua');
    shoeSole(sprite, base, { left: 16, right: 29 });
    sprite.pair(55, 20, 27, base);
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(57, 18, 26, 2, light);
    sprite.pairRect(61, 16, 29, 1, dark);
    sprite.outline(INK);
  },
});

item('shoes_snow_boots', 'SHOES', {
  main(sprite) {
    const [base, mid, dark] = ramp('shoes_snow_boots');
    sprite.pairRect(49, 17, 29, 7, base);
    shoeSole(sprite, base, { left: 14, right: 29 });
    sprite.shadeRamp(base, mid, dark);
    // 털 커프
    sprite.pairRect(49, 17, 29, 3, WHITE);
    sprite.pairRect(60, 14, 29, 2, dark);
    sprite.outline(INK);
  },
});

item('shoes_space_boots', 'SHOES', {
  main(sprite) {
    const [base, mid, dark, accent] = ramp('shoes_space_boots');
    sprite.pairRect(51, 17, 29, 5, base);
    shoeSole(sprite, base, { left: 15, right: 29 });
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(51, 17, 29, 2, accent);
    sprite.pairRect(57, 16, 27, 2, accent);
    sprite.pairRect(60, 15, 29, 2, dark);
    sprite.outline(INK);
  },
});

// ── 등 장식 3종 ────────────────────────────────────────────

item('back_fairy_wings', 'BACK', {
  behind(sprite) {
    const [base, mid, dark, light] = ramp('back_fairy_wings');
    // 규격 §9.3: 몸통 좌우로 각각 8px 이상 노출되고 3단 램프 + 잉크 외곽선을 갖는다.
    sprite.pairRows([
      [26, 6, 12], [27, 4, 13], [28, 3, 14], [29, 2, 14],
      [30, 1, 15], [31, 1, 15], [32, 1, 15], [33, 2, 15],
      [34, 2, 14], [35, 3, 14], [36, 4, 13], [37, 5, 12],
      [38, 6, 11], [39, 7, 10],
    ], base);
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(29, 4, 8, 4, light);
    sprite.pairRect(30, 10, 13, 3, mid);
    sprite.outline(INK);
  },
});

item('back_dinosaur_tail', 'BACK', {
  behind(sprite) {
    const [base, mid, dark, spike] = ramp('back_dinosaur_tail');
    // 규격 §9.2: 접합부 TAIL_ROOT(x52, y42), 꼬리 끝은 몸통 최외곽 x54에서 8px 밖으로.
    // 몸에 가려지는 x49..54는 두께만 담당하고 실제로 보이는 부분은 x55..62다.
    for (const [y, left, right] of [
      [35, 52, 57], [36, 50, 60], [37, 48, 62], [38, 47, 62], [39, 47, 62],
      [40, 47, 62], [41, 48, 62], [42, 49, 61], [43, 51, 58], [44, 53, 56],
    ]) {
      sprite.rect(left, y, right - left + 1, 1, base);
    }
    sprite.shadeRamp(base, mid, dark);
    // 등 지느러미. 몸 밖(x55 이상)에 두어 확실히 보이게 한다.
    for (const [x, y] of [[55, 33], [58, 34], [60, 36]]) sprite.rect(x, y, 3, 3, spike);
    sprite.outline(INK);
  },
});

item('back_briefcase', 'BACK', {
  front(sprite) {
    const [base, mid, dark, gold] = ramp('back_briefcase');
    // 손잡이
    sprite.rect(49, 36, 9, 2, mid);
    sprite.rect(49, 38, 2, 3, mid);
    sprite.rect(56, 38, 2, 3, mid);
    // 가방 본체
    sprite.rect(47, 40, 13, 13, base);
    sprite.shadeRamp(base, mid, dark);
    sprite.rect(47, 45, 13, 2, dark);
    sprite.rect(51, 44, 4, 3, gold);
    sprite.outline(INK);
  },
});

// ── 손 소품 3종 ────────────────────────────────────────────

item('hand_baseball_glove', 'HAND', {
  front(sprite) {
    const [base, mid, dark, light] = ramp('hand_baseball_glove');
    for (const [y, left, right] of [
      [36, 50, 57], [37, 48, 59], [38, 47, 60], [39, 47, 60], [40, 47, 60],
      [41, 47, 60], [42, 47, 60], [43, 47, 60], [44, 48, 59], [45, 48, 58],
      [46, 49, 57], [47, 50, 55],
    ]) {
      sprite.rect(left, y, right - left + 1, 1, base);
    }
    sprite.shadeRamp(base, mid, dark);
    for (const x of [51, 54, 57]) sprite.rect(x, 38, 1, 6, dark);
    sprite.rect(49, 43, 8, 2, light);
    sprite.outline(INK);
  },
});

item('hand_magic_wand', 'HAND', {
  front(sprite) {
    const [wood, woodMid, woodDark, gold, light] = ramp('hand_magic_wand');
    // 손잡이. 2px로 그리면 outline이 전부 잉크로 덮어 막대가 사라진다.
    sprite.rect(51, 33, 4, 18, wood);
    sprite.shadeRamp(wood, woodMid, woodDark);
    // 별 (x47..58, y25..34)
    for (const [y, left, right] of [
      [25, 52, 53], [26, 51, 54], [27, 51, 54], [28, 47, 58],
      [29, 48, 57], [30, 49, 56], [31, 50, 55],
    ]) {
      sprite.rect(left, y, right - left + 1, 1, gold);
    }
    sprite.rect(49, 32, 3, 2, gold);
    sprite.rect(54, 32, 3, 2, gold);
    sprite.rect(51, 28, 4, 2, light);
    sprite.outline(INK);
  },
});

item('hand_toy_sword', 'HAND', {
  front(sprite) {
    const [base, mid, dark, gold, grip] = ramp('hand_toy_sword');
    // 칼날
    sprite.rect(52, 27, 4, 13, base);
    sprite.rect(53, 25, 2, 2, base);
    sprite.shadeRamp(base, mid, dark);
    // 가드와 손잡이
    sprite.rect(49, 40, 10, 3, gold);
    sprite.rect(53, 43, 2, 7, grip);
    sprite.rect(52, 49, 4, 2, gold);
    sprite.outline(INK);
  },
});

// ── 효과 2종 ───────────────────────────────────────────────

item('effect_swim_tube', 'EFFECT', {
  // 링 위쪽 절반은 몸 뒤, 아래쪽 절반과 좌우 덩어리는 몸 앞이다. 몸통 최외곽(x9..54)
  // 밖으로 6px 이상 나가야 튜브로 읽힌다.
  behind(sprite) {
    const [base, mid, dark] = ramp('effect_swim_tube');
    sprite.symRows([
      [33, 18, 45], [34, 13, 50], [35, 9, 54], [36, 6, 57],
      [37, 4, 59], [38, 3, 60], [39, 3, 60], [40, 3, 60],
    ], base);
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(37, 4, 8, 3, WHITE);
    sprite.symRect(34, 26, 37, 2, WHITE);
    sprite.outline(INK);
  },
  front(sprite) {
    const [base, mid, dark] = ramp('effect_swim_tube');
    sprite.pairRect(41, 3, 14, 3, base);
    sprite.pair(44, 3, 15, base);
    sprite.symRows([
      [45, 4, 59], [46, 4, 59], [47, 6, 57], [48, 9, 54], [49, 13, 50], [50, 18, 45],
    ], base);
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(41, 4, 9, 3, WHITE);
    sprite.pairRect(45, 18, 25, 3, WHITE);
    sprite.symRect(48, 28, 35, 2, WHITE);
    sprite.outline(INK);
  },
});

item('effect_fluffy_cloud', 'EFFECT', {
  behind(sprite) {
    const [base, mid, dark] = ramp('effect_fluffy_cloud');
    sprite.symRows([
      [53, 20, 43], [54, 15, 48], [55, 12, 51], [56, 9, 54], [57, 7, 56], [58, 5, 58],
    ], base);
    sprite.shadeRamp(base, mid, dark);
    sprite.outline(INK);
  },
  front(sprite) {
    const [base, mid, dark] = ramp('effect_fluffy_cloud');
    sprite.symRows([
      [59, 6, 57], [60, 4, 59], [61, 3, 60], [62, 5, 58],
    ], base);
    sprite.shadeRamp(base, mid, dark);
    sprite.pairRect(60, 8, 15, 2, WHITE);
    sprite.symRect(60, 28, 35, 2, WHITE);
    sprite.outline(INK);
  },
});

// ── 배경 5종 ───────────────────────────────────────────────
// 배경은 캔버스를 전부 채우며 외곽선·대칭 검사 대상이 아니다(규격 §10.4, §10.5).

item('bg_spring', 'BACKGROUND', {
  main(sprite) {
    const [sky, grass, grassDark, trunk, trunkDark, blossom, blossomLight, petal] = ramp('bg_spring');
    sprite.rect(0, 0, 64, 41, sky);
    sprite.rect(0, 41, 64, 23, grass);
    sprite.rect(0, 55, 64, 9, grassDark);
    for (const x of [5, 53]) {
      sprite.rect(x, 16, 5, 30, trunk);
      sprite.rect(x + 3, 16, 2, 30, trunkDark);
    }
    for (const [x, y, w, h] of [
      [0, 4, 18, 12], [2, 2, 13, 5], [12, 8, 12, 9],
      [46, 5, 18, 12], [49, 2, 13, 5], [40, 9, 12, 8],
      [22, 0, 20, 7],
    ]) {
      sprite.rect(x, y, w, h, blossom);
      sprite.rect(x + 2, y + 1, Math.max(2, w - 6), 3, blossomLight);
    }
    for (const [x, y] of [[20, 20], [30, 14], [44, 24], [12, 30], [50, 34], [26, 38]]) {
      sprite.rect(x, y, 2, 2, petal);
    }
    sprite.rect(0, 41, 64, 2, petal);
  },
});

item('bg_summer_sea', 'BACKGROUND', {
  main(sprite) {
    const [sky, sea, seaDark, foam, sand, sandDark] = ramp('bg_summer_sea');
    sprite.rect(0, 0, 64, 28, sky);
    sprite.rect(0, 28, 64, 18, sea);
    sprite.rect(0, 40, 64, 6, seaDark);
    sprite.rect(0, 46, 64, 18, sand);
    sprite.rect(0, 58, 64, 6, sandDark);
    for (const [x, y, w] of [[3, 32, 18], [34, 36, 20], [12, 43, 16], [44, 30, 14]]) {
      sprite.rect(x, y, w, 2, foam);
    }
    for (const [x, y, w, h] of [[5, 6, 16, 4], [9, 4, 9, 3], [40, 10, 14, 4], [44, 8, 8, 3]]) {
      sprite.rect(x, y, w, h, foam);
    }
    for (const [x, y] of [[8, 52], [22, 56], [40, 51], [54, 55], [30, 49]]) {
      sprite.rect(x, y, 2, 2, sandDark);
    }
  },
});

item('bg_autumn', 'BACKGROUND', {
  main(sprite) {
    const [sky, ground, groundDark, trunk, leafRed, leafOrange, leafGold] = ramp('bg_autumn');
    sprite.rect(0, 0, 64, 41, sky);
    sprite.rect(0, 41, 64, 23, ground);
    sprite.rect(0, 55, 64, 9, groundDark);
    for (const x of [4, 52]) sprite.rect(x, 12, 6, 34, trunk);
    for (const [x, y, w, h, color] of [
      [0, 2, 18, 8, leafRed], [3, 8, 16, 7, leafOrange], [10, 3, 14, 8, leafGold],
      [44, 2, 20, 8, leafRed], [46, 9, 16, 7, leafOrange], [38, 5, 14, 7, leafGold],
      [24, 0, 16, 6, leafOrange],
    ]) {
      sprite.rect(x, y, w, h, color);
    }
    for (const [x, y, color] of [
      [20, 22, leafRed], [34, 18, leafGold], [46, 28, leafOrange],
      [14, 32, leafGold], [50, 36, leafRed], [28, 34, leafOrange],
    ]) {
      sprite.rect(x, y, 3, 2, color);
    }
  },
});

item('bg_winter', 'BACKGROUND', {
  main(sprite) {
    const [sky, snow, snowShade, pineDark, pine, snowCap] = ramp('bg_winter');
    sprite.rect(0, 0, 64, 41, sky);
    sprite.rect(0, 41, 64, 23, snow);
    sprite.rect(0, 56, 64, 8, snowShade);
    for (const x of [2, 54]) {
      sprite.rect(x + 3, 22, 3, 22, pineDark);
      for (const [dy, w] of [[10, 8], [16, 10], [22, 12]]) {
        sprite.rect(x, dy + 8, w, 4, pine);
        sprite.rect(x, dy + 8, w, 2, snowCap);
      }
    }
    for (const [x, y] of [
      [8, 6], [20, 14], [30, 4], [42, 16], [52, 8], [14, 26], [46, 30], [34, 24],
    ]) {
      sprite.rect(x, y, 2, 2, snowCap);
    }
  },
});

item('bg_space', 'BACKGROUND', {
  main(sprite) {
    const [deep, ground, crater, band, star, starWhite] = ramp('bg_space');
    sprite.rect(0, 0, 64, 40, deep);
    sprite.rect(0, 40, 64, 24, ground);
    for (const [x, y, w, h] of [[6, 46, 12, 4], [30, 52, 14, 4], [46, 44, 10, 3]]) {
      sprite.rect(x, y, w, h, crater);
    }
    for (let step = 0; step < 26; step += 1) {
      sprite.rect(2 + step * 2, 36 - step, 3, 2, band);
    }
    for (const [x, y, size, color] of [
      [6, 8, 2, starWhite], [16, 20, 1, star], [26, 6, 2, star], [38, 14, 1, starWhite],
      [48, 6, 2, starWhite], [56, 22, 1, star], [10, 30, 1, starWhite], [44, 28, 2, star],
      [32, 24, 1, starWhite],
    ]) {
      sprite.rect(x, y, size, size, color);
    }
  },
});

// ─────────────────────────────────────────────────────────── 실행

const selected = process.argv.slice(2).filter((arg) => !arg.startsWith('-'));
const keys = selected.length
  ? selected.filter((key) => {
      if (!ITEMS[key]) throw new Error(`정의가 없는 상품: ${key}`);
      return true;
    })
  : Object.keys(ITEMS);

let written = 0;
const summary = [];
for (const key of keys) {
  const { slot, parts } = ITEMS[key];
  const dir = path.join(OUT, slot.toLowerCase());
  fs.mkdirSync(dir, { recursive: true });
  const split = Object.keys(parts).length > 1;
  for (const [part, draw] of Object.entries(parts)) {
    // 규격 §7.1: 파일을 앞·뒤로 나눈 상품만 접미사를 붙인다. 단일 파일이면 spriteKey
    // 그대로다(back_backpack이 front 밴드를 쓰면서 접미사가 없는 것과 같은 규칙).
    const assetId = split ? `${key}_${part}` : key;
    const sprite = new Sprite(assetId);
    draw(sprite);
    const colors = sprite.usedColors();
    if (colors.size === 0) throw new Error(`${assetId}: 빈 스프라이트다`);
    sprite.save(path.join(dir, `${assetId}.png`));
    written += 1;
    summary.push(`  ${slot.toLowerCase()}/${assetId}.png  ${colors.size}색 ${
      [...colors.values()].reduce((a, b) => a + b, 0)}px`);
  }
}

console.log(`${written}개 파일 생성 (${keys.length}종)`);
console.log(summary.join('\n'));
console.log(
  '\n다음: node scripts/character/sync-assets.js && node scripts/character/check-assets.js',
);

module.exports = { ITEMS };
