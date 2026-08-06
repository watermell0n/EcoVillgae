import type { EquippedMap, Slot } from './types';

export const CHARACTER_PIXEL_WIDTH = 48;
export const CHARACTER_PIXEL_HEIGHT = 56;

const INK = '#273038';
const WHITE = '#fffdf8';
const CHEEK = '#f3a6b8';
const GOLD = '#f6c64f';

type PixelContext = CanvasRenderingContext2D;
export type CharacterLayerSprites = Partial<Record<Slot, CanvasImageSource>>;

function drawSprite(context: PixelContext, sprite: CanvasImageSource) {
  context.drawImage(sprite, 0, 0, CHARACTER_PIXEL_WIDTH, CHARACTER_PIXEL_HEIGHT);
}

function rect(
  context: PixelContext,
  color: string,
  x: number,
  y: number,
  width: number,
  height: number,
) {
  context.fillStyle = color;
  context.fillRect(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
}

function dot(context: PixelContext, color: string, x: number, y: number, size = 1) {
  rect(context, color, x, y, size, size);
}

function diagonal(
  context: PixelContext,
  color: string,
  x: number,
  y: number,
  length: number,
  direction: 1 | -1,
  thickness = 1,
) {
  for (let step = 0; step < length; step += 1) {
    rect(context, color, x + step, y + step * direction, thickness, thickness);
  }
}

type SilhouetteRow = readonly [y: number, left: number, right: number];

function connectSilhouetteRows(
  context: PixelContext,
  color: string,
  rows: readonly SilhouetteRow[],
) {
  for (let index = 1; index < rows.length; index += 1) {
    const [previousY, previousLeft, previousRight] = rows[index - 1];
    const [currentY, currentLeft, currentRight] = rows[index];

    if (currentLeft < previousLeft) {
      rect(context, color, currentLeft, currentY, previousLeft - currentLeft + 1, 1);
    } else if (currentLeft > previousLeft) {
      rect(context, color, previousLeft, previousY, currentLeft - previousLeft + 1, 1);
    }

    if (currentRight > previousRight) {
      rect(context, color, previousRight, currentY, currentRight - previousRight + 1, 1);
    } else if (currentRight < previousRight) {
      rect(context, color, currentRight, previousY, previousRight - currentRight + 1, 1);
    }
  }
}

function fillSilhouetteRows(
  context: PixelContext,
  rows: readonly SilhouetteRow[],
  fill: string,
  border = INK,
) {
  for (const [y, left, right] of rows) {
    rect(context, border, left, y, right - left + 1, 1);
    if (right - left > 1) {
      rect(context, fill, left + 1, y, right - left - 1, 1);
    }
  }
  connectSilhouetteRows(context, border, rows);
}

function mirrorRows(rows: readonly SilhouetteRow[]): SilhouetteRow[] {
  return rows.map(([y, left, right]) => [y, 47 - right, 47 - left]);
}

function drawBackground(context: PixelContext, key: string | null) {
  if (!key) return;

  if (key === 'bg_space') {
    rect(context, '#111a3d', 0, 0, 48, 56);
    rect(context, '#26235c', 0, 34, 48, 22);
    diagonal(context, '#634a91', 2, 40, 28, -1, 2);
    diagonal(context, '#3d69a4', 15, 49, 25, -1, 1);
    [[5, 8], [13, 17], [22, 6], [32, 13], [42, 7], [39, 27], [7, 31]].forEach(
      ([x, y], index) => dot(context, index % 2 ? '#f7d86d' : '#ffffff', x, y, index % 3 ? 1 : 2),
    );
    return;
  }

  if (key === 'bg_summer_sea') {
    rect(context, '#8fd5ef', 0, 0, 48, 28);
    rect(context, '#f8f4dc', 0, 43, 48, 13);
    rect(context, '#3db8d1', 0, 27, 48, 16);
    rect(context, '#a8e2e5', 0, 31, 48, 2);
    rect(context, '#ffffff', 2, 36, 17, 2);
    rect(context, '#ffffff', 29, 39, 14, 2);
    rect(context, '#ffffff', 5, 8, 12, 3);
    rect(context, '#ffffff', 9, 6, 7, 2);
    rect(context, '#ffffff', 31, 12, 11, 3);
    return;
  }

  if (key === 'bg_winter') {
    rect(context, '#9fc9e8', 0, 0, 48, 39);
    rect(context, '#eef7fb', 0, 39, 48, 17);
    for (const [x, y] of [[4, 7], [12, 17], [22, 9], [34, 18], [43, 6], [7, 30], [39, 31]]) {
      dot(context, '#ffffff', x, y, 2);
    }
    rect(context, '#315b57', 2, 20, 3, 22);
    rect(context, '#4f8c72', 0, 22, 9, 3);
    rect(context, '#4f8c72', 0, 28, 11, 3);
    rect(context, '#f7fbfd', 0, 20, 9, 2);
    rect(context, '#f7fbfd', 0, 26, 11, 2);
    return;
  }

  if (key === 'bg_autumn') {
    rect(context, '#f4d7a2', 0, 0, 48, 40);
    rect(context, '#d58a46', 0, 40, 48, 16);
    rect(context, '#70462f', 3, 9, 4, 38);
    rect(context, '#70462f', 41, 12, 4, 35);
    for (const [x, y, color] of [
      [0, 5, '#c94f3a'], [6, 2, '#e87932'], [12, 8, '#d99a2b'], [38, 5, '#c94f3a'],
      [42, 2, '#e87932'], [31, 10, '#d99a2b'], [2, 18, '#e87932'], [42, 20, '#c94f3a'],
    ] as Array<[number, number, string]>) {
      rect(context, color, x, y, 7, 5);
    }
    return;
  }

  rect(context, '#9fdcf0', 0, 0, 48, 39);
  rect(context, '#8ad272', 0, 39, 48, 17);
  rect(context, '#72513d', 3, 13, 4, 31);
  rect(context, '#72513d', 41, 15, 4, 29);
  for (const [x, y] of [[0, 8], [5, 5], [10, 10], [37, 7], [42, 4], [32, 12]]) {
    rect(context, '#f6a9c2', x, y, 7, 4);
    dot(context, '#ffe4ed', x + 2, y + 1, 2);
  }
  rect(context, '#fff5f8', 0, 43, 48, 2);
}

function bodyBounds(y: number): [number, number] | null {
  if (y === 3) return [18, 29];
  if (y === 4) return [15, 32];
  if (y === 5) return [13, 34];
  if (y === 6) return [11, 36];
  if (y === 7) return [10, 37];
  if (y <= 11) return [9, 38];
  if (y <= 19) return [8, 39];
  if (y <= 24) return [7, 40];
  if (y <= 29) return [6, 41];
  if (y <= 37) return [4, 43];
  if (y <= 42) return [5, 42];
  if (y <= 45) return [6, 41];
  if (y === 46) return [8, 39];
  if (y === 47) return [10, 37];
  if (y === 48) return [13, 34];
  return null;
}

function drawBaseBody(context: PixelContext) {
  const bodyRows: SilhouetteRow[] = [];
  for (let y = 3; y <= 48; y += 1) {
    const bounds = bodyBounds(y);
    if (!bounds) continue;
    const [left, right] = bounds;
    bodyRows.push([y, left, right]);
    rect(context, INK, left, y, right - left + 1, 1);
    if (y >= 4 && right - left > 3) {
      rect(context, WHITE, left + 1, y, right - left - 1, 1);
    }
  }
  connectSilhouetteRows(context, INK, bodyRows);

  rect(context, INK, 10, 47, 13, 7);
  rect(context, INK, 25, 47, 13, 7);
  rect(context, WHITE, 11, 47, 11, 5);
  rect(context, WHITE, 26, 47, 11, 5);

  rect(context, INK, 14, 16, 2, 6);
  rect(context, INK, 32, 16, 2, 6);
  rect(context, INK, 20, 24, 8, 2);
  rect(context, CHEEK, 10, 22, 3, 2);
  rect(context, CHEEK, 35, 22, 3, 2);
  rect(context, '#eceff1', 39, 31, 2, 9);
  rect(context, '#eceff1', 34, 43, 4, 2);
}

function drawBack(context: PixelContext, key: string | null) {
  if (key === 'back_backpack') {
    rect(context, INK, 2, 29, 8, 17);
    rect(context, '#8d552d', 3, 30, 6, 15);
    rect(context, '#c17a38', 5, 32, 2, 11);
    rect(context, INK, 38, 29, 8, 17);
    rect(context, '#8d552d', 39, 30, 6, 15);
    rect(context, '#c17a38', 41, 32, 2, 11);
    rect(context, '#5d3825', 8, 27, 3, 14);
    rect(context, '#5d3825', 37, 27, 3, 14);
  }
  if (key === 'back_fairy_wings') {
    const leftWing: SilhouetteRow[] = [
      [18, 5, 10], [19, 3, 11], [20, 2, 12], [21, 1, 13], [22, 0, 13],
      [23, 0, 14], [24, 1, 14], [25, 1, 13], [26, 2, 13], [27, 2, 12],
      [28, 3, 12], [29, 4, 11], [30, 5, 11], [31, 6, 10],
    ];
    const rightWing = mirrorRows(leftWing);
    fillSilhouetteRows(context, leftWing, '#eaf8ff', '#8ec7e6');
    fillSilhouetteRows(context, rightWing, '#eaf8ff', '#8ec7e6');
    diagonal(context, '#b7dff2', 4, 21, 8, 1, 1);
    diagonal(context, '#b7dff2', 36, 28, 8, -1, 1);
    rect(context, '#ffffff', 5, 22, 3, 6);
    rect(context, '#ffffff', 40, 22, 3, 6);
  }
  if (key === 'back_dinosaur_tail') {
    const tailRows: SilhouetteRow[] = [
      [35, 37, 42], [36, 36, 44], [37, 36, 46], [38, 37, 47],
      [39, 38, 47], [40, 39, 46], [41, 40, 45], [42, 41, 44],
    ];
    fillSilhouetteRows(context, tailRows, '#4cae52');
    dot(context, '#d8e66d', 40, 34, 2);
    dot(context, '#d8e66d', 44, 36, 2);
    dot(context, '#d8e66d', 46, 39, 2);
  }
}

function topPalette(key: string): [string, string, string] {
  const palettes: Record<string, [string, string, string]> = {
    top_blue_tshirt: ['#497dc0', '#315b98', '#dbe9f8'],
    top_yellow_hoodie: ['#efc947', '#c99827', '#fff0a6'],
    top_green_vest: ['#5d963d', '#366f32', '#a8d278'],
    top_swimsuit: ['#2272b9', '#174d8f', '#ffffff'],
    top_padding: ['#d84438', '#a6242a', '#ef7b65'],
    top_soccer: ['#f28b27', '#d85a1f', '#ffffff'],
    top_baseball_pinstripe: ['#f4f2e8', '#b8bdc5', '#1c2732'],
    top_baseball_red: ['#d84539', '#a5262a', '#242d36'],
    top_baseball_blue: ['#357fcb', '#24569d', '#ffffff'],
    top_baseball_navy: ['#263e6d', '#172847', '#d7e4f4'],
    top_baseball_gray: ['#cbd0d3', '#8a969e', '#273038'],
    top_spacesuit: ['#eef3f6', '#aebdca', '#3876a8'],
    top_safety_police: ['#1f3f70', '#132846', '#f2c74e'],
    top_legal_professional: ['#252b32', '#11161b', '#ffffff'],
    top_pilot: ['#f2f2ed', '#c9ced2', '#1b2633'],
    top_wizard_robe: ['#6442a0', '#3c286e', '#f2cf4d'],
    top_fantasy_armor: ['#aeb9c2', '#66727d', '#e3ecf0'],
    top_dinosaur: ['#4da952', '#2c7d3d', '#c5df6d'],
  };
  return palettes[key] ?? ['#4f8fc9', '#2e66a0', '#ffffff'];
}

function drawTop(context: PixelContext, key: string | null) {
  if (!key) return;
  const [primary, dark, accent] = topPalette(key);

  const shirtRows: SilhouetteRow[] = [
    [29, 15, 32], [30, 12, 35], [31, 10, 37], [32, 9, 38],
    [33, 9, 38], [34, 9, 38], [35, 9, 38], [36, 9, 38],
    [37, 9, 38], [38, 9, 38], [39, 9, 38], [40, 9, 38],
    [41, 9, 38], [42, 9, 38], [43, 9, 38], [44, 9, 38],
    [45, 10, 37], [46, 11, 36],
  ];
  const jacketRows: SilhouetteRow[] = [
    [28, 15, 32], [29, 12, 35], [30, 10, 37], [31, 9, 38],
    [32, 8, 39], [33, 8, 39], [34, 8, 39], [35, 8, 39],
    [36, 8, 39], [37, 8, 39], [38, 8, 39], [39, 8, 39],
    [40, 8, 39], [41, 8, 39], [42, 8, 39], [43, 8, 39],
    [44, 9, 38], [45, 10, 37], [46, 11, 36],
  ];
  const fittedRows: SilhouetteRow[] = [
    [29, 15, 32], [30, 13, 34], [31, 11, 36], [32, 10, 37],
    [33, 9, 38], [34, 9, 38], [35, 9, 38], [36, 9, 38],
    [37, 9, 38], [38, 9, 38], [39, 9, 38], [40, 9, 38],
    [41, 9, 38], [42, 9, 38], [43, 10, 37], [44, 10, 37],
    [45, 11, 36], [46, 13, 34],
  ];
  const fullRows: SilhouetteRow[] = [
    [26, 18, 29], [27, 15, 32], [28, 14, 33], [29, 10, 37], [30, 8, 39], [31, 6, 41],
    [32, 5, 42], [33, 5, 42], [34, 5, 42], [35, 5, 42],
    [36, 5, 42], [37, 5, 42], [38, 5, 42], [39, 5, 42],
    [40, 6, 41], [41, 6, 41], [42, 6, 41], [43, 7, 40],
    [44, 7, 40], [45, 8, 39], [46, 9, 38], [47, 10, 37],
    [48, 11, 36], [49, 12, 35], [50, 13, 34],
  ];
  const isFullBody = ['top_spacesuit', 'top_wizard_robe', 'top_fantasy_armor', 'top_dinosaur'].includes(key);
  const isFitted = ['top_green_vest', 'top_swimsuit'].includes(key);
  const isShirt = ['top_blue_tshirt', 'top_soccer'].includes(key);
  const garmentRows = isFullBody ? fullRows : isFitted ? fittedRows : isShirt ? shirtRows : jacketRows;

  if (isShirt) {
    const sleeveRows: SilhouetteRow[] = [
      [31, 8, 12], [32, 6, 13], [33, 5, 13], [34, 4, 13],
      [35, 4, 13], [36, 4, 13], [37, 5, 13], [38, 6, 12],
      [39, 8, 11],
    ];
    fillSilhouetteRows(context, sleeveRows, primary);
    fillSilhouetteRows(context, mirrorRows(sleeveRows), primary);
  } else if (!isFullBody && !isFitted) {
    const sleeveRows: SilhouetteRow[] = [
      [31, 7, 12], [32, 5, 13], [33, 4, 13], [34, 3, 13],
      [35, 3, 13], [36, 3, 13], [37, 3, 13], [38, 3, 13],
      [39, 4, 13], [40, 4, 13], [41, 5, 12], [42, 6, 12],
      [43, 8, 11],
    ];
    fillSilhouetteRows(context, sleeveRows, primary);
    fillSilhouetteRows(context, mirrorRows(sleeveRows), primary);
  }
  fillSilhouetteRows(context, garmentRows, primary);

  if (key === 'top_blue_tshirt') {
    rect(context, WHITE, 20, 29, 8, 3);
    rect(context, dark, 5, 32, 8, 3);
    rect(context, dark, 35, 32, 8, 3);
    rect(context, '#6f9bd1', 11, 34, 26, 2);
    rect(context, dark, 9, 44, 30, 3);
  } else if (key === 'top_yellow_hoodie') {
    const hoodRows: SilhouetteRow[] = [
      [26, 17, 30], [27, 14, 33], [28, 12, 35], [29, 11, 36], [30, 12, 35],
    ];
    fillSilhouetteRows(context, hoodRows, primary);
    rect(context, dark, 12, 29, 4, 2);
    rect(context, dark, 15, 31, 4, 2);
    rect(context, dark, 29, 29, 7, 2);
    rect(context, dark, 27, 31, 4, 2);
    rect(context, WHITE, 20, 31, 1, 8);
    rect(context, WHITE, 27, 31, 1, 8);
    dot(context, WHITE, 19, 38, 2);
    dot(context, WHITE, 27, 38, 2);
    rect(context, dark, 14, 38, 20, 6);
    rect(context, primary, 15, 39, 18, 4);
    rect(context, '#f7dd7d', 10, 34, 2, 7);
    rect(context, '#f7dd7d', 36, 34, 2, 7);
  } else if (key === 'top_green_vest') {
    rect(context, WHITE, 18, 29, 12, 17);
    rect(context, dark, 12, 30, 3, 3);
    rect(context, dark, 14, 32, 3, 3);
    rect(context, dark, 16, 34, 3, 12);
    rect(context, dark, 33, 30, 3, 3);
    rect(context, dark, 31, 32, 3, 3);
    rect(context, dark, 29, 34, 3, 12);
    rect(context, dark, 10, 37, 8, 6);
    rect(context, '#78aa55', 11, 38, 6, 4);
    rect(context, dark, 30, 37, 8, 6);
    rect(context, '#78aa55', 31, 38, 6, 4);
    rect(context, '#a7ca79', 11, 33, 2, 3);
    rect(context, '#a7ca79', 35, 33, 2, 3);
  } else if (key === 'top_swimsuit') {
    rect(context, WHITE, 14, 29, 20, 3);
    rect(context, dark, 13, 31, 4, 3);
    rect(context, dark, 31, 31, 4, 3);
    rect(context, '#4e9ad0', 13, 35, 22, 2);
    rect(context, dark, 12, 43, 24, 3);
  } else if (key === 'top_padding') {
    const collarRows: SilhouetteRow[] = [[27, 17, 30], [28, 14, 33], [29, 12, 35]];
    fillSilhouetteRows(context, collarRows, dark);
    rect(context, dark, 23, 29, 2, 17);
    rect(context, '#f17768', 7, 33, 34, 2);
    rect(context, '#f17768', 6, 38, 36, 2);
    rect(context, '#f17768', 8, 43, 32, 2);
    rect(context, '#8e2829', 8, 44, 32, 2);
  } else if (key.startsWith('top_baseball_')) {
    rect(context, WHITE, 19, 28, 10, 7);
    rect(context, dark, 23, 31, 2, 14);
    dot(context, accent, 22, 34, 2);
    dot(context, accent, 22, 39, 2);
    rect(context, dark, 8, 42, 32, 3);
    rect(context, accent, 9, 42, 30, 1);
    if (key === 'top_baseball_pinstripe') {
      for (const x of [10, 15, 20, 27, 32, 37]) rect(context, '#b8bdc5', x, 31, 1, 11);
    }
  } else if (key === 'top_soccer') {
    rect(context, WHITE, 17, 29, 14, 4);
    rect(context, WHITE, 8, 32, 6, 3);
    rect(context, WHITE, 34, 32, 6, 3);
    rect(context, dark, 9, 42, 30, 3);
    rect(context, '#ffc46b', 12, 35, 24, 2);
  } else if (key === 'top_spacesuit') {
    rect(context, '#c7d3db', 5, 32, 7, 12);
    rect(context, '#eef4f6', 6, 33, 5, 9);
    rect(context, '#c7d3db', 36, 32, 7, 12);
    rect(context, '#eef4f6', 37, 33, 5, 9);
    rect(context, dark, 15, 32, 18, 10);
    rect(context, '#f5f7f6', 16, 33, 16, 8);
    rect(context, '#e95545', 18, 36, 3, 2);
    rect(context, '#3c8ec3', 23, 36, 6, 2);
    rect(context, '#9fb0bb', 10, 44, 28, 3);
    rect(context, '#8299a9', 13, 48, 9, 2);
    rect(context, '#8299a9', 26, 48, 9, 2);
  } else if (key === 'top_safety_police') {
    rect(context, WHITE, 17, 28, 14, 6);
    rect(context, '#1d2730', 22, 31, 4, 13);
    rect(context, '#f0f3f3', 23, 31, 2, 3);
    rect(context, accent, 32, 34, 4, 4);
    dot(context, accent, 12, 35, 2);
    dot(context, accent, 12, 40, 2);
    rect(context, '#111c2f', 8, 43, 32, 3);
    rect(context, GOLD, 22, 43, 4, 3);
  } else if (key === 'top_legal_professional') {
    rect(context, WHITE, 17, 28, 14, 8);
    rect(context, '#263b55', 22, 31, 4, 13);
    rect(context, dark, 12, 29, 4, 3);
    rect(context, dark, 15, 32, 4, 3);
    rect(context, dark, 32, 29, 4, 3);
    rect(context, dark, 29, 32, 4, 3);
    dot(context, '#4a5660', 15, 38, 2);
    dot(context, '#4a5660', 15, 42, 2);
  } else if (key === 'top_pilot') {
    rect(context, WHITE, 17, 28, 14, 5);
    rect(context, INK, 22, 31, 4, 13);
    rect(context, GOLD, 8, 30, 7, 3);
    rect(context, GOLD, 33, 30, 7, 3);
    rect(context, '#6c9bbd', 11, 37, 7, 5);
    rect(context, '#d6e0e5', 12, 38, 5, 3);
    rect(context, '#6c9bbd', 30, 37, 7, 5);
    rect(context, '#d6e0e5', 31, 38, 5, 3);
    rect(context, INK, 8, 43, 32, 3);
    rect(context, GOLD, 22, 43, 4, 3);
  } else if (key === 'top_wizard_robe') {
    rect(context, accent, 15, 28, 18, 2);
    rect(context, dark, 6, 43, 36, 5);
    rect(context, accent, 8, 43, 32, 2);
    for (const [x, y] of [[13, 34], [33, 35], [19, 42], [35, 45]]) {
      dot(context, accent, x, y, 2);
    }
  } else if (key === 'top_fantasy_armor') {
    rect(context, '#dce3e8', 13, 29, 22, 12);
    rect(context, dark, 16, 32, 16, 8);
    rect(context, '#eef3f5', 21, 33, 6, 5);
    rect(context, '#7b563b', 7, 41, 34, 4);
    rect(context, GOLD, 22, 41, 4, 4);
    rect(context, '#818d97', 7, 31, 7, 9);
    rect(context, '#818d97', 34, 31, 7, 9);
    rect(context, '#788792', 12, 46, 10, 3);
    rect(context, '#788792', 26, 46, 10, 3);
  } else if (key === 'top_dinosaur') {
    rect(context, accent, 14, 30, 20, 14);
    rect(context, '#a9d66f', 17, 32, 14, 10);
    rect(context, dark, 6, 43, 36, 3);
    dot(context, '#dbe96e', 40, 33, 2);
    dot(context, '#dbe96e', 42, 37, 2);
    dot(context, '#dbe96e', 40, 41, 2);
  }
}

function drawBottomBack(context: PixelContext, key: string | null) {
  if (key !== 'bottom_overalls') return;
  const trouserRows: SilhouetteRow[] = [
    [36, 8, 39], [37, 7, 40], [38, 6, 41], [39, 6, 41],
    [40, 6, 41], [41, 6, 41], [42, 6, 41], [43, 6, 41],
    [44, 7, 40], [45, 7, 40], [46, 9, 38], [47, 11, 36],
    [48, 13, 34],
  ];
  fillSilhouetteRows(context, trouserRows, '#3f608d');
  rect(context, '#263f69', 7, 43, 34, 4);
  rect(context, INK, 22, 44, 4, 6);
  rect(context, '#5877a0', 10, 47, 11, 2);
  rect(context, '#5877a0', 27, 47, 11, 2);
}

function drawBottomFront(context: PixelContext, key: string | null) {
  if (key !== 'bottom_overalls') return;
  rect(context, INK, 13, 27, 5, 13);
  rect(context, '#5173a2', 14, 28, 3, 11);
  rect(context, INK, 30, 27, 5, 13);
  rect(context, '#5173a2', 31, 28, 3, 11);
  const bibRows: SilhouetteRow[] = [
    [33, 14, 33], [34, 13, 34], [35, 13, 34], [36, 13, 34],
    [37, 13, 34], [38, 13, 34], [39, 13, 34], [40, 14, 33],
    [41, 14, 33], [42, 15, 32],
  ];
  fillSilhouetteRows(context, bibRows, '#486b9a');
  rect(context, '#6585ad', 16, 35, 16, 2);
  rect(context, '#2c4d78', 18, 38, 12, 5);
  rect(context, '#5e80aa', 19, 39, 10, 2);
  dot(context, '#e7c365', 15, 32, 2);
  dot(context, '#e7c365', 31, 32, 2);
}

function drawNeck(context: PixelContext, key: string | null) {
  if (!key) return;
  const knit = key === 'neck_knit_scarf';
  const primary = knit ? '#4e7848' : '#b82f2d';
  const light = knit ? '#7fa16f' : '#dc5a49';
  const dark = knit ? '#365635' : '#842527';
  const wrapRows: SilhouetteRow[] = [
    [26, 13, 34], [27, 10, 37], [28, 9, 38], [29, 9, 38],
    [30, 10, 37], [31, 12, 35], [32, 14, 33],
  ];
  fillSilhouetteRows(context, wrapRows, primary);
  rect(context, light, 12, 28, 24, 2);
  rect(context, INK, 33, 31, 8, 15);
  rect(context, primary, 34, 32, 6, 13);
  rect(context, light, 35, 35, 2, 7);
  rect(context, dark, 34, 43, 6, 2);
}

function drawShoes(context: PixelContext, key: string | null) {
  if (!key) return;

  if (key === 'shoes_sneakers') {
    const left: SilhouetteRow[] = [
      [47, 11, 19], [48, 9, 21], [49, 8, 22], [50, 7, 22],
      [51, 7, 22], [52, 7, 22], [53, 8, 22],
    ];
    const right = mirrorRows(left);
    fillSilhouetteRows(context, left, '#f8f4ed');
    fillSilhouetteRows(context, right, '#f8f4ed');
    rect(context, '#d64a3d', 9, 48, 10, 3);
    rect(context, '#d64a3d', 29, 48, 10, 3);
    rect(context, '#ffffff', 10, 50, 8, 1);
    rect(context, '#ffffff', 30, 50, 8, 1);
    rect(context, '#d64a3d', 8, 52, 14, 1);
    rect(context, '#d64a3d', 26, 52, 14, 1);
    rect(context, INK, 7, 53, 16, 2);
    rect(context, INK, 25, 53, 16, 2);
    return;
  }

  if (key === 'shoes_red_boots') {
    const left: SilhouetteRow[] = [
      [44, 11, 19], [45, 9, 20], [46, 8, 21], [47, 8, 21],
      [48, 8, 21], [49, 8, 21], [50, 7, 22], [51, 7, 22],
      [52, 7, 22], [53, 8, 22],
    ];
    const right = mirrorRows(left);
    fillSilhouetteRows(context, left, '#d44436');
    fillSilhouetteRows(context, right, '#d44436');
    rect(context, '#ef7460', 10, 46, 9, 2);
    rect(context, '#ef7460', 29, 46, 9, 2);
    rect(context, '#8e2829', 8, 51, 14, 2);
    rect(context, '#8e2829', 26, 51, 14, 2);
    return;
  }

  if (key === 'shoes_sandals') {
    const left: SilhouetteRow[] = [[49, 11, 19], [50, 9, 21], [51, 9, 21], [52, 8, 22], [53, 9, 22]];
    const right = mirrorRows(left);
    fillSilhouetteRows(context, left, '#8b5e38');
    fillSilhouetteRows(context, right, '#8b5e38');
    rect(context, WHITE, 11, 49, 8, 2);
    rect(context, WHITE, 29, 49, 8, 2);
    rect(context, '#5b3b27', 9, 52, 13, 2);
    rect(context, '#5b3b27', 26, 52, 13, 2);
    return;
  }

  if (key === 'shoes_aqua') {
    const left: SilhouetteRow[] = [
      [48, 12, 19], [49, 10, 21], [50, 8, 22], [51, 7, 22],
      [52, 6, 22], [53, 8, 21],
    ];
    const right = mirrorRows(left);
    fillSilhouetteRows(context, left, '#42b8cb');
    fillSilhouetteRows(context, right, '#42b8cb');
    rect(context, '#8de0e9', 10, 49, 7, 2);
    rect(context, '#8de0e9', 31, 49, 7, 2);
    rect(context, '#237e9c', 7, 52, 15, 2);
    rect(context, '#237e9c', 26, 52, 15, 2);
    return;
  }

  const bulkyLeft: SilhouetteRow[] = [
    [44, 11, 19], [45, 9, 21], [46, 8, 22], [47, 8, 22],
    [48, 7, 22], [49, 7, 22], [50, 6, 22], [51, 6, 22],
    [52, 6, 22], [53, 7, 22],
  ];
  const bulkyRight = mirrorRows(bulkyLeft);
  const isSpace = key === 'shoes_space_boots';
  fillSilhouetteRows(context, bulkyLeft, isSpace ? '#edf1f3' : '#dce4e8');
  fillSilhouetteRows(context, bulkyRight, isSpace ? '#edf1f3' : '#dce4e8');
  rect(context, WHITE, 9, 44, 12, 3);
  rect(context, WHITE, 27, 44, 12, 3);
  if (isSpace) {
    rect(context, '#a7b7c3', 9, 48, 12, 2);
    rect(context, '#a7b7c3', 27, 48, 12, 2);
    rect(context, '#8298a8', 7, 52, 15, 2);
    rect(context, '#8298a8', 26, 52, 15, 2);
  } else {
    rect(context, '#617d99', 9, 48, 12, 2);
    rect(context, '#617d99', 27, 48, 12, 2);
    rect(context, '#38587a', 6, 51, 16, 3);
    rect(context, '#38587a', 26, 51, 16, 3);
  }
}

function drawFaceAccessory(context: PixelContext, key: string | null) {
  if (!key) return;
  if (key === 'face_sunglasses') {
    rect(context, INK, 9, 15, 30, 3);
    const leftLens: SilhouetteRow[] = [
      [16, 10, 21], [17, 10, 21], [18, 10, 21], [19, 10, 21],
      [20, 11, 20], [21, 12, 19], [22, 13, 18],
    ];
    fillSilhouetteRows(context, leftLens, '#111820', '#111820');
    fillSilhouetteRows(context, mirrorRows(leftLens), '#111820', '#111820');
    rect(context, '#41505d', 11, 16, 6, 1);
    rect(context, '#41505d', 28, 16, 6, 1);
    rect(context, INK, 21, 17, 6, 2);
    rect(context, INK, 8, 16, 3, 2);
    rect(context, INK, 37, 16, 3, 2);
    return;
  }
  if (key === 'face_headphones') {
    rect(context, INK, 15, 4, 18, 2);
    rect(context, INK, 12, 5, 24, 2);
    rect(context, '#707983', 13, 6, 22, 2);
    rect(context, INK, 9, 7, 5, 16);
    rect(context, '#aeb5bb', 10, 8, 3, 14);
    rect(context, INK, 34, 7, 5, 16);
    rect(context, '#aeb5bb', 35, 8, 3, 14);
    rect(context, INK, 5, 15, 8, 13);
    rect(context, '#c2c7cb', 6, 16, 6, 11);
    rect(context, '#68737c', 8, 18, 3, 7);
    rect(context, INK, 35, 15, 8, 13);
    rect(context, '#c2c7cb', 36, 16, 6, 11);
    rect(context, '#68737c', 37, 18, 3, 7);
    return;
  }
  const ski = key === 'face_ski_goggles';
  const lens = ski ? '#ef9a24' : '#3d9bd3';
  rect(context, INK, 9, 15, 30, 9);
  rect(context, lens, 10, 16, 11, 6);
  rect(context, lens, 27, 16, 11, 6);
  rect(context, '#bce8f4', 12, 17, 5, 2);
  rect(context, '#bce8f4', 29, 17, 5, 2);
  rect(context, INK, 21, 18, 6, 2);
  rect(context, INK, 8, 17, 2, 4);
  rect(context, INK, 38, 17, 2, 4);
  if (ski) {
    rect(context, '#202d36', 5, 16, 4, 6);
    rect(context, '#202d36', 39, 16, 4, 6);
    rect(context, '#ffb13c', 11, 20, 26, 2);
  }
}

function baseballColor(key: string): string {
  if (key.endsWith('_red')) return '#d84438';
  if (key.endsWith('_navy')) return '#263e6d';
  if (key.endsWith('_green')) return '#487b45';
  if (key.endsWith('_yellow')) return '#e7b83f';
  return '#397fc4';
}

function drawCap(context: PixelContext, color: string, highlight: string) {
  const crownRows: SilhouetteRow[] = [
    [2, 19, 28], [3, 16, 31], [4, 14, 33], [5, 12, 35],
    [6, 11, 36], [7, 10, 37], [8, 10, 37], [9, 10, 37],
    [10, 10, 37],
  ];
  fillSilhouetteRows(context, crownRows, color);
  rect(context, highlight, 15, 4, 13, 2);
  rect(context, '#00000022', 12, 7, 3, 3);
  rect(context, INK, 10, 9, 28, 4);
  rect(context, color, 11, 10, 26, 2);
  rect(context, INK, 29, 10, 15, 4);
  rect(context, color, 30, 11, 13, 2);
  rect(context, highlight, 31, 11, 8, 1);
}

function drawBeanie(context: PixelContext, color: string, dark: string) {
  const beanieRows: SilhouetteRow[] = [
    [1, 21, 26], [2, 18, 29], [3, 15, 32], [4, 13, 34],
    [5, 12, 35], [6, 11, 36], [7, 10, 37], [8, 10, 37],
    [9, 10, 37], [10, 10, 37],
  ];
  fillSilhouetteRows(context, beanieRows, color);
  for (const x of [14, 19, 24, 29, 34]) rect(context, '#ffffff44', x, 5, 1, 5);
  rect(context, INK, 10, 10, 28, 5);
  rect(context, dark, 11, 11, 26, 3);
  rect(context, color, 12, 11, 24, 1);
}

function drawHat(context: PixelContext, key: string | null) {
  if (!key) return;

  if (key === 'hat_red_cap') {
    drawCap(context, '#d84438', '#ef6655');
    return;
  }

  if (key === 'hat_teal_beanie') {
    drawBeanie(context, '#13929b', '#0e6877');
    return;
  }

  if (key.startsWith('hat_baseball_')) {
    const color = baseballColor(key);
    drawCap(context, color, '#ffffff');
    rect(context, '#ffffff', 21, 5, 6, 2);
    return;
  }

  if (key === 'hat_straw' || key === 'hat_beach') {
    const band = key === 'hat_straw' ? '#b84234' : '#3f84ba';
    const crownRows: SilhouetteRow[] = [
      [1, 19, 28], [2, 16, 31], [3, 14, 33], [4, 13, 34],
      [5, 13, 34], [6, 13, 34], [7, 13, 34], [8, 13, 34],
    ];
    fillSilhouetteRows(context, crownRows, '#e4c57d');
    rect(context, '#f2d89d', 17, 3, 14, 2);
    rect(context, band, 13, 7, 22, 3);
    rect(context, INK, 3, 9, 42, 5);
    rect(context, '#d7ad5f', 4, 10, 40, 3);
    rect(context, '#f2d89d', 8, 10, 28, 1);
    return;
  }

  if (key === 'hat_pom_beanie') {
    drawBeanie(context, '#4f7849', '#355d36');
    const pomRows: SilhouetteRow[] = [
      [0, 22, 25], [1, 20, 27], [2, 19, 28], [3, 19, 28],
      [4, 20, 27], [5, 22, 25],
    ];
    fillSilhouetteRows(context, pomRows, '#e6ece8');
    rect(context, '#ffffff', 21, 1, 5, 2);
    return;
  }

  if (key === 'hat_chef') {
    const chefRows: SilhouetteRow[] = [
      [0, 22, 27], [1, 17, 30], [2, 14, 33], [3, 12, 35],
      [4, 10, 37], [5, 9, 38], [6, 9, 38], [7, 10, 37],
      [8, 11, 36], [9, 11, 36], [10, 11, 36], [11, 11, 36],
      [12, 11, 36],
    ];
    fillSilhouetteRows(context, chefRows, '#f8f7f1');
    rect(context, '#dfe4e5', 14, 5, 3, 6);
    rect(context, '#dfe4e5', 22, 3, 3, 8);
    rect(context, '#dfe4e5', 31, 5, 3, 6);
    rect(context, INK, 11, 11, 26, 4);
    rect(context, '#f4f3ec', 12, 12, 24, 2);
    return;
  }

  if (key === 'hat_safari') {
    const safariRows: SilhouetteRow[] = [
      [1, 18, 29], [2, 15, 32], [3, 13, 34], [4, 12, 35],
      [5, 12, 35], [6, 12, 35], [7, 12, 35], [8, 12, 35],
    ];
    fillSilhouetteRows(context, safariRows, '#c9a064');
    rect(context, '#e0ba7a', 16, 3, 16, 2);
    dot(context, '#70492d', 17, 6, 2);
    dot(context, '#70492d', 29, 6, 2);
    rect(context, INK, 4, 8, 40, 5);
    rect(context, '#b98a51', 5, 9, 38, 3);
    return;
  }

  if (key === 'hat_space_helmet') {
    const helmetRows: SilhouetteRow[] = [
      [1, 18, 29], [2, 14, 33], [3, 11, 36], [4, 9, 38],
      [5, 8, 39], [6, 7, 40], [7, 7, 40], [8, 7, 40],
      [9, 7, 40], [10, 7, 40], [11, 7, 40], [12, 7, 40],
      [13, 7, 40], [14, 7, 40], [15, 7, 40], [16, 7, 40],
      [17, 8, 39], [18, 8, 39], [19, 9, 38], [20, 10, 37],
      [21, 11, 36], [22, 12, 35], [23, 13, 34], [24, 14, 33],
    ];
    fillSilhouetteRows(context, helmetRows, '#eef3f5');
    rect(context, '#7f95a5', 8, 19, 32, 6);
    rect(context, INK, 10, 7, 28, 17);
    rect(context, '#4389b5', 11, 8, 26, 15);
    rect(context, '#dff4fb', 13, 9, 22, 13);
    rect(context, WHITE, 14, 11, 20, 10);
    rect(context, '#ffffffaa', 15, 9, 7, 2);
    rect(context, INK, 16, 13, 2, 5);
    rect(context, INK, 30, 13, 2, 5);
    rect(context, CHEEK, 14, 18, 2, 2);
    rect(context, CHEEK, 32, 18, 2, 2);
    rect(context, INK, 21, 19, 6, 2);
    return;
  }

  if (key === 'hat_safety_police' || key === 'hat_pilot') {
    const color = key === 'hat_pilot' ? '#151d27' : '#213f70';
    const uniformRows: SilhouetteRow[] = [
      [2, 18, 29], [3, 14, 33], [4, 12, 35], [5, 11, 36],
      [6, 10, 37], [7, 10, 37], [8, 10, 37], [9, 11, 36],
      [10, 12, 35],
    ];
    fillSilhouetteRows(context, uniformRows, color);
    rect(context, '#e4b83e', 12, 9, 24, 2);
    rect(context, INK, 10, 10, 30, 5);
    rect(context, color, 11, 11, 28, 3);
    if (key === 'hat_pilot') {
      rect(context, GOLD, 19, 5, 10, 2);
      rect(context, GOLD, 22, 3, 4, 5);
    } else {
      dot(context, GOLD, 22, 4, 4);
      dot(context, '#fff0a2', 23, 3, 2);
    }
    return;
  }

  if (key === 'hat_crown') {
    fillSilhouetteRows(context, [[1, 11, 13], [2, 10, 14], [3, 10, 15], [4, 11, 16]], GOLD);
    fillSilhouetteRows(context, [[0, 23, 25], [1, 22, 26], [2, 21, 27], [3, 20, 28], [4, 19, 29]], GOLD);
    fillSilhouetteRows(context, [[1, 35, 37], [2, 34, 38], [3, 33, 38], [4, 32, 37]], GOLD);
    rect(context, INK, 10, 4, 28, 9);
    rect(context, GOLD, 11, 5, 26, 7);
    rect(context, '#e5a82c', 12, 8, 24, 3);
    dot(context, '#d84c4c', 15, 7, 3);
    dot(context, '#3c78c4', 23, 7, 3);
    dot(context, '#62a96e', 31, 7, 3);
    return;
  }

  if (key === 'hat_wizard') {
    const coneRows: SilhouetteRow[] = [
      [0, 28, 33], [1, 24, 34], [2, 21, 35], [3, 19, 35],
      [4, 18, 34], [5, 17, 34], [6, 16, 35], [7, 15, 36],
      [8, 14, 37], [9, 13, 38], [10, 12, 39], [11, 11, 40],
    ];
    fillSilhouetteRows(context, coneRows, '#6541a0');
    rect(context, '#4a2c7d', 24, 1, 8, 2);
    dot(context, GOLD, 23, 6, 4);
    rect(context, INK, 4, 11, 40, 6);
    rect(context, '#55358c', 5, 12, 38, 4);
    rect(context, GOLD, 15, 12, 18, 2);
    return;
  }

  if (key === 'hat_knight') {
    const helmetRows: SilhouetteRow[] = [
      [1, 20, 27], [2, 16, 31], [3, 13, 34], [4, 11, 36],
      [5, 10, 37], [6, 9, 38], [7, 9, 38], [8, 9, 38],
      [9, 9, 38], [10, 9, 38], [11, 9, 38], [12, 9, 38],
      [13, 9, 38], [14, 9, 38], [15, 9, 38], [16, 9, 38],
      [17, 10, 37], [18, 11, 36], [19, 12, 35], [20, 13, 34],
    ];
    fillSilhouetteRows(context, helmetRows, '#aebac3');
    rect(context, '#e6edf0', 14, 4, 20, 5);
    rect(context, INK, 10, 10, 28, 5);
    rect(context, '#66737e', 11, 11, 26, 3);
    for (const x of [14, 20, 26, 32]) rect(context, '#56626d', x, 13, 2, 7);
    rect(context, '#dbe3e7', 12, 18, 24, 2);
    return;
  }

  if (key === 'hat_dinosaur') {
    const hoodRows: SilhouetteRow[] = [
      [2, 18, 29], [3, 14, 33], [4, 11, 36], [5, 9, 38],
      [6, 8, 39], [7, 8, 39], [8, 8, 39], [9, 8, 39],
      [10, 8, 39], [11, 8, 39], [12, 8, 39], [13, 8, 39],
      [14, 8, 39], [15, 8, 39], [16, 9, 38], [17, 10, 37],
      [18, 11, 36], [19, 12, 35], [20, 13, 34], [21, 14, 33],
    ];
    fillSilhouetteRows(context, hoodRows, '#54ad55');
    rect(context, '#8fd16e', 14, 1, 5, 4);
    rect(context, '#8fd16e', 23, 0, 5, 4);
    rect(context, '#8fd16e', 32, 2, 5, 4);
    dot(context, WHITE, 13, 8, 5);
    dot(context, INK, 15, 10, 2);
    dot(context, WHITE, 30, 8, 5);
    dot(context, INK, 32, 10, 2);
    rect(context, INK, 12, 15, 24, 7);
    rect(context, WHITE, 13, 16, 22, 6);
    rect(context, INK, 14, 16, 2, 6);
    rect(context, INK, 32, 16, 2, 6);
  }
}

function drawFrontBackItem(context: PixelContext, key: string | null) {
  if (key !== 'back_briefcase') return;
  diagonal(context, '#6d4027', 17, 27, 17, 1, 2);
  rect(context, INK, 31, 38, 15, 12);
  rect(context, '#8c552d', 32, 39, 13, 10);
  rect(context, '#c17a38', 34, 40, 9, 2);
  rect(context, '#5d3825', 32, 47, 13, 2);
  rect(context, GOLD, 37, 42, 3, 3);
}

function drawHand(context: PixelContext, key: string | null) {
  if (!key) return;
  if (key === 'hand_baseball_glove') {
    const gloveRows: SilhouetteRow[] = [
      [28, 39, 46], [29, 38, 47], [30, 37, 47], [31, 37, 47],
      [32, 37, 47], [33, 37, 47], [34, 37, 47], [35, 37, 47],
      [36, 38, 47], [37, 38, 46], [38, 39, 45], [39, 40, 44],
    ];
    fillSilhouetteRows(context, gloveRows, '#9b5a2f');
    for (const x of [40, 43, 46]) rect(context, '#573522', x, 29, 1, 7);
    rect(context, '#c17a38', 39, 36, 6, 2);
    return;
  }
  if (key === 'hand_magic_wand') {
    diagonal(context, '#704522', 35, 42, 12, -1, 2);
    rect(context, GOLD, 40, 22, 3, 11);
    rect(context, GOLD, 36, 26, 11, 3);
    dot(context, '#fff4a5', 39, 25, 5);
    dot(context, '#ffffff', 41, 24, 2);
    return;
  }
  if (key === 'hand_toy_sword') {
    diagonal(context, '#d7e1e6', 37, 38, 13, -1, 2);
    diagonal(context, '#8fa0aa', 39, 38, 10, -1, 1);
    rect(context, '#7d522d', 35, 38, 4, 10);
    rect(context, GOLD, 32, 37, 10, 3);
    rect(context, '#4d3020', 36, 46, 2, 3);
  }
}

function drawFloorEffect(context: PixelContext, key: string | null) {
  if (key === 'effect_swim_tube') {
    const backRingRows: SilhouetteRow[] = [
      [34, 12, 35], [35, 8, 39], [36, 5, 42], [37, 3, 44], [38, 2, 45],
    ];
    fillSilhouetteRows(context, backRingRows, '#ef9a35');
    rect(context, '#ffffff', 9, 35, 6, 3);
    rect(context, '#ffffff', 33, 35, 6, 3);
    return;
  }
  if (key !== 'effect_fluffy_cloud') return;
  rect(context, '#b7dbea', 5, 49, 38, 6);
  rect(context, '#eef9fd', 7, 47, 34, 7);
  rect(context, '#ffffff', 3, 50, 13, 5);
  rect(context, '#ffffff', 12, 47, 12, 7);
  rect(context, '#ffffff', 22, 48, 12, 6);
  rect(context, '#ffffff', 31, 49, 14, 6);
  rect(context, '#d7edf5', 8, 53, 31, 2);
}

function drawFrontEffect(context: PixelContext, key: string | null) {
  if (key !== 'effect_swim_tube') return;
  rect(context, INK, 3, 37, 10, 9);
  rect(context, '#ef9a35', 4, 38, 8, 7);
  rect(context, INK, 35, 37, 10, 9);
  rect(context, '#ef9a35', 36, 38, 8, 7);
  const frontRingRows: SilhouetteRow[] = [
    [42, 4, 43], [43, 5, 42], [44, 6, 41], [45, 8, 39],
    [46, 9, 38], [47, 12, 35],
  ];
  fillSilhouetteRows(context, frontRingRows, '#ef9a35');
  rect(context, '#ffffff', 4, 40, 8, 3);
  rect(context, '#ffffff', 36, 40, 8, 3);
  rect(context, '#ffffff', 11, 43, 5, 4);
  rect(context, '#ffffff', 32, 43, 5, 4);
  rect(context, '#f8bd69', 18, 43, 12, 2);
}

export function renderPixelCharacter(
  context: PixelContext,
  equipped: EquippedMap,
  baseSprite?: CanvasImageSource,
  layerSprites: CharacterLayerSprites = {},
) {
  context.imageSmoothingEnabled = false;
  context.clearRect(0, 0, CHARACTER_PIXEL_WIDTH, CHARACTER_PIXEL_HEIGHT);

  drawBackground(context, equipped.BACKGROUND);
  drawFloorEffect(context, equipped.EFFECT);
  if (layerSprites.BACK) {
    drawSprite(context, layerSprites.BACK);
  } else {
    drawBack(context, equipped.BACK);
  }
  if (baseSprite) {
    drawSprite(context, baseSprite);
  } else {
    drawBaseBody(context);
  }
  if (!layerSprites.BOTTOM) {
    drawBottomBack(context, equipped.BOTTOM);
  }
  if (layerSprites.TOP) {
    drawSprite(context, layerSprites.TOP);
  } else {
    drawTop(context, equipped.TOP);
  }
  if (layerSprites.BOTTOM) {
    drawSprite(context, layerSprites.BOTTOM);
  } else {
    drawBottomFront(context, equipped.BOTTOM);
  }
  if (layerSprites.SHOES) {
    drawSprite(context, layerSprites.SHOES);
  } else {
    drawShoes(context, equipped.SHOES);
  }
  drawNeck(context, equipped.NECK);
  if (!layerSprites.BACK) {
    drawFrontBackItem(context, equipped.BACK);
  }
  if (layerSprites.FACE) {
    drawSprite(context, layerSprites.FACE);
  } else {
    drawFaceAccessory(context, equipped.FACE);
  }
  if (layerSprites.HAT) {
    drawSprite(context, layerSprites.HAT);
  } else {
    drawHat(context, equipped.HAT);
  }
  drawHand(context, equipped.HAND);
  drawFrontEffect(context, equipped.EFFECT);
}
