'use strict';
/**
 * 64×64 도트 스프라이트 작화 헬퍼.
 * 규격: docs/design/CHARACTER_ASSET_SPEC.md §1(캔버스) §2(대칭) §3(BASE 좌표) §4(팔레트)
 *
 * 설계 의도
 * - 좌우 대칭을 "그리는 방식"으로 보장한다. `sym`은 left + right === 63을 강제하고
 *   `pair`는 미러 쌍을 함께 찍는다. 그래서 check-assets.js의 대칭 검사에 걸릴 수 없다.
 * - 외곽선은 손으로 그리지 않고 `outline()`이 알파 경계에서 파생한다. 실루엣이 대칭이면
 *   잉크도 자동으로 대칭이 된다(규격 §2.2가 강제하는 두 채널이 한 번에 해결된다).
 * - 명암은 `shadeRamp()`가 우하단 기준으로 넣는다. 좌상단 광원(규격 §3.3)을 지키면서
 *   램프는 대칭 대상이 아니므로(§2.2) 자유롭게 비대칭이 된다.
 */
const { encode } = require('./png');

const SIZE = 64;
/** 미러 축은 x=31과 x=32의 경계다. 대칭 런은 left + right === 63을 만족한다(규격 §2.1). */
const MIRROR_SUM = SIZE - 1;

function toRgb(hex) {
  const value = hex.replace('#', '');
  if (value.length !== 6) throw new Error(`색은 #rrggbb 형식이어야 한다: ${hex}`);
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16),
  ];
}

/** BASE 캐릭터 실루엣 정본(규격 §3.1). 상품은 이 값을 기준으로 부풀리거나 줄인다. */
function bodySpan(y) {
  if (y < 4) return null;
  if (y <= 5) return [22, 41];
  if (y <= 7) return [20, 43];
  if (y <= 9) return [18, 45];
  if (y <= 12) return [17, 46];
  if (y <= 25) return [15, 48];
  if (y <= 29) return [13, 50];
  if (y <= 33) return [11, 52];
  if (y <= 44) return [9, 54];
  if (y <= 48) return [11, 52];
  if (y <= 51) return [13, 50];
  if (y <= 53) return [15, 48];
  if (y <= 55) return [17, 46];
  if (y === 56) return [18, 45];
  return null;
}

class Sprite {
  constructor(name) {
    this.name = name;
    this.rgba = Buffer.alloc(SIZE * SIZE * 4);
  }

  static get SIZE() {
    return SIZE;
  }

  index(x, y) {
    return (y * SIZE + x) * 4;
  }

  inside(x, y) {
    return x >= 0 && y >= 0 && x < SIZE && y < SIZE;
  }

  opaque(x, y) {
    return this.inside(x, y) && this.rgba[this.index(x, y) + 3] === 255;
  }

  colorAt(x, y) {
    if (!this.opaque(x, y)) return null;
    const i = this.index(x, y);
    return '#' + [0, 1, 2]
      .map((k) => this.rgba[i + k].toString(16).padStart(2, '0'))
      .join('');
  }

  /** color가 null이면 지운다(투명 창을 파낼 때 쓴다). */
  set(x, y, color) {
    if (!this.inside(x, y)) return this;
    const i = this.index(x, y);
    if (color === null) {
      this.rgba.fill(0, i, i + 4);
      return this;
    }
    const [r, g, b] = toRgb(color);
    this.rgba[i] = r;
    this.rgba[i + 1] = g;
    this.rgba[i + 2] = b;
    this.rgba[i + 3] = 255;
    return this;
  }

  rect(x, y, width, height, color) {
    for (let dy = 0; dy < height; dy += 1) {
      for (let dx = 0; dx < width; dx += 1) this.set(x + dx, y + dy, color);
    }
    return this;
  }

  /** 좌우 대칭 런. left + right === 63을 어기면 즉시 실패한다. */
  sym(y, left, right, color) {
    if (left + right !== MIRROR_SUM) {
      throw new Error(
        `${this.name} y${y}: 대칭 런은 left + right === 63이어야 한다 (${left}..${right})`,
      );
    }
    return this.rect(left, y, right - left + 1, 1, color);
  }

  /** 좌측 조각과 그 미러 쌍을 함께 찍는다. 두 짝으로 존재하는 부위(신발·렌즈·날개)에 쓴다. */
  pair(y, left, right, color) {
    this.rect(left, y, right - left + 1, 1, color);
    return this.rect(MIRROR_SUM - right, y, right - left + 1, 1, color);
  }

  symRows(rows, color) {
    for (const [y, left, right] of rows) this.sym(y, left, right, color);
    return this;
  }

  pairRows(rows, color) {
    for (const [y, left, right] of rows) this.pair(y, left, right, color);
    return this;
  }

  /** 좌우 대칭 사각형. left + right === 63. */
  symRect(y, left, right, height, color) {
    for (let dy = 0; dy < height; dy += 1) this.sym(y + dy, left, right, color);
    return this;
  }

  /** 좌측 사각형과 미러 쌍. */
  pairRect(y, left, right, height, color) {
    for (let dy = 0; dy < height; dy += 1) this.pair(y + dy, left, right, color);
    return this;
  }

  /** BASE 실루엣을 따라가는 의상 몸통. grow만큼 좌우로 부풀린다. */
  garment(fromY, toY, grow, color) {
    for (let y = fromY; y <= toY; y += 1) {
      const span = bodySpan(y);
      if (!span) continue;
      this.sym(y, span[0] - grow, span[1] + grow, color);
    }
    return this;
  }

  /** 목둘레를 파낸다. 파낸 자리에는 BASE의 목·얼굴이 그대로 보인다. */
  neckline(fromY, toY, left, right) {
    for (let y = fromY; y <= toY; y += 1) this.sym(y, left, right, null);
    return this;
  }

  /**
   * 우하단에 3단 램프를 넣는다. base 색 픽셀만 대상이며 경계까지의 거리로 단을 정한다.
   * 좌상단 광원을 유지하므로 램프는 좌우가 다르다 — 규격 §2.2가 허용하는 비대칭이다.
   */
  shadeRamp(base, mid, dark, { depth = 2 } = {}) {
    const changes = [];
    for (let y = 0; y < SIZE; y += 1) {
      for (let x = 0; x < SIZE; x += 1) {
        if (this.colorAt(x, y) !== base) continue;
        // 광원 반대쪽(우하단) 경계까지의 대각 거리로 단을 정한다. 가로·세로를 따로 재면
        // 밑면이 평평한 상품에서 아래 전체가 한 단으로 눌려 명암이 띠처럼 보인다.
        let reach = 0;
        while (reach < depth * 2 && this.opaque(x + reach + 1, y + reach + 1)) reach += 1;
        if (reach < depth) changes.push([x, y, dark]);
        else if (reach < depth * 2) changes.push([x, y, mid]);
      }
    }
    for (const [x, y, color] of changes) this.set(x, y, color);
    return this;
  }

  /** 알파 경계를 잉크로 덮는다. 실루엣이 대칭이면 잉크도 대칭이 된다. */
  outline(ink) {
    const edge = [];
    for (let y = 0; y < SIZE; y += 1) {
      for (let x = 0; x < SIZE; x += 1) {
        if (!this.opaque(x, y)) continue;
        if (
          !this.opaque(x - 1, y) || !this.opaque(x + 1, y)
          || !this.opaque(x, y - 1) || !this.opaque(x, y + 1)
        ) {
          edge.push([x, y]);
        }
      }
    }
    for (const [x, y] of edge) this.set(x, y, ink);
    return this;
  }

  /** 이 스프라이트가 쓴 색 전체. 팔레트 등록 누락을 생성 시점에 잡는다. */
  usedColors() {
    const colors = new Map();
    for (let y = 0; y < SIZE; y += 1) {
      for (let x = 0; x < SIZE; x += 1) {
        const color = this.colorAt(x, y);
        if (color) colors.set(color, (colors.get(color) ?? 0) + 1);
      }
    }
    return colors;
  }

  save(file) {
    encode({ width: SIZE, height: SIZE, rgba: this.rgba }, file);
  }
}

module.exports = { Sprite, bodySpan, SIZE, MIRROR_SUM };
