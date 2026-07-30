import { Graphics } from 'pixi.js';
import type { BodyState, Slot } from '../types';

export const CELL = 5;
export const GRID_W = 28;
export const GRID_H = 32;
export const DISPLAY_SCALE = 2;

const CX = 14;
const P = {
  ink: 0x272d33,
  white: 0xffffff,
  blush: 0xf3a7b5,
  light: 0xf1f2f3,
  gold: 0xf3c447,
};

const COLORS: Record<string, number> = {
  hat_red_cap: 0xe85445,
  hat_party: 0xf0649b,
  hat_crown: 0xf3c447,
  hat_straw: 0xd5a853,
  hat_beanie: 0x168e96,
  hat_beach: 0xe2bd65,
  hat_pom_beanie: 0x5e884e,
  hat_wizard: 0x553886,
  hat_chef: 0xf7f7f7,
  hat_safari: 0xb5874f,
  hat_baseball_blue: 0x3974c5,
  hat_baseball_red: 0xd94c43,
  hat_baseball_navy: 0x30476b,
  hat_baseball_green: 0x55784a,
  hat_baseball_yellow: 0xe0ad3e,
  hat_space_helmet: 0xe9eef2,
  hat_knight: 0xaeb8c2,
  hat_dinosaur: 0x69a557,
  hat_safety_police: 0x233e67,
  hat_pilot: 0x1d2734,
  top_blue_tee: 0x3f78c8,
  top_yellow_hoodie: 0xf0bc3c,
  top_green_vest: 0x548e45,
  top_overalls: 0x45669b,
  top_swimsuit: 0x2c78bf,
  top_padding: 0xd94c43,
  top_suit: 0x303944,
  top_wizard_robe: 0x583887,
  top_hanbok: 0xefa1b6,
  top_soccer: 0xf18724,
  top_baseball_pinstripe: 0xf5f5f3,
  top_baseball_red: 0xc94b43,
  top_baseball_blue: 0x3974c5,
  top_baseball_navy: 0x30476b,
  top_baseball_gray: 0xbac0c4,
  top_spacesuit: 0xdce3e8,
  top_fantasy_armor: 0xaab4be,
  top_dinosaur: 0x62a052,
  top_safety_police: 0x273f66,
  top_legal_professional: 0x303944,
  top_pilot: 0xf3f3f1,
  top_wedding_dress: 0xfff7fa,
  bottom_striped_pants: 0x416294,
  bottom_jeans: 0x38547e,
  bottom_shorts: 0x4e8c74,
  bottom_skirt: 0xe89ab0,
  bottom_slacks: 0x343b46,
  shoes_sneakers: 0xf7f7f7,
  shoes_red_boots: 0xd94c43,
  shoes_sandals: 0x8b653f,
  shoes_aqua: 0x43b7c7,
  shoes_snow_boots: 0xd9e1e7,
  shoes_space_boots: 0xd9e1e7,
  face_sunglasses: 0x25292e,
  face_goggles: 0x278fce,
  face_ski_goggles: 0xe39b29,
  face_headphones: 0x59616a,
  neck_scarf: 0xc84842,
  neck_knit_scarf: 0x557b4d,
  neck_bowtie: 0xc84a4a,
  neck_necklace: 0xd4a631,
  back_backpack: 0x9a683e,
  back_fairy_wings: 0xcfe8f6,
  back_dinosaur_tail: 0x65a153,
  hand_baseball_glove: 0x9c6637,
  hand_magic_wand: 0xe6b641,
  hand_toy_sword: 0xc6ccd2,
  hand_briefcase: 0x8d5d36,
  hand_bouquet: 0xe77b9d,
  effect_swim_tube: 0xf2a244,
  effect_fluffy_cloud: 0xe8f1f7,
  bg_spring: 0xf7cad5,
  bg_summer_sea: 0x55b6dc,
  bg_autumn: 0xcf7445,
  bg_winter: 0xcfe4ef,
  bg_space: 0x111a33,
};

interface RowBounds {
  left: number;
  right: number;
}

function colorFor(key: string): number {
  if (COLORS[key] !== undefined) return COLORS[key];
  let hash = 0;
  for (let index = 0; index < key.length; index += 1) {
    hash = (hash * 31 + key.charCodeAt(index)) & 0xffff;
  }
  const palette = [0xd9544d, 0xe0ad3e, 0x548e45, 0x3974c5, 0x695099, 0xd56c8e, 0x8d6e63];
  return palette[hash % palette.length];
}

function darken(color: number, factor = 0.76): number {
  const red = Math.round(((color >> 16) & 0xff) * factor);
  const green = Math.round(((color >> 8) & 0xff) * factor);
  const blue = Math.round((color & 0xff) * factor);
  return (red << 16) | (green << 8) | blue;
}

function px(g: Graphics, x: number, y: number, width: number, height: number, color: number, alpha = 1) {
  g.rect(x * CELL, y * CELL, width * CELL, height * CELL).fill({ color, alpha });
}

function outlineBox(g: Graphics, x: number, y: number, width: number, height: number, fill: number) {
  px(g, x, y, width, height, P.ink);
  if (width > 2 && height > 2) px(g, x + 1, y + 1, width - 2, height - 2, fill);
}

function bodyBounds(body: BodyState, y: number): RowBounds {
  if (y === 3) return { left: 11, right: 17 };
  if (y === 4) return { left: 10, right: 18 };
  if (y === 5) return { left: 9, right: 19 };
  if (y <= 10) return { left: 8, right: 20 };

  const widest: Record<BodyState, RowBounds> = {
    CHUBBY: { left: 4, right: 24 },
    BASE: { left: 6, right: 22 },
    SLIM: { left: 8, right: 20 },
    VERY_SLIM: { left: 9, right: 19 },
  };
  const target = widest[body];
  if (y === 11) return { left: 8, right: 20 };
  if (y === 12) return { left: 7, right: 21 };
  if (y === 13) return { left: target.left + 1, right: target.right - 1 };
  if (y <= 18) return target;
  if (y === 19) return { left: target.left + 1, right: target.right - 1 };
  if (y === 20) return { left: target.left + 2, right: target.right - 2 };
  if (y === 21) return { left: target.left + 3, right: target.right - 3 };
  return { left: target.left + 4, right: target.right - 4 };
}

function legBounds(_body: BodyState) {
  return {
    left: { start: 9, end: 13 },
    right: { start: 15, end: 19 },
  };
}

function fillProfile(g: Graphics, body: BodyState, startY: number, endY: number, color: number, inset = 1) {
  for (let y = startY; y <= endY; y += 1) {
    const { left, right } = bodyBounds(body, y);
    const width = right - left + 1 - inset * 2;
    if (width > 0) px(g, left + inset, y, width, 1, color);
  }
}

export function drawBase(g: Graphics, body: BodyState) {
  for (let y = 3; y <= 22; y += 1) {
    const { left, right } = bodyBounds(body, y);
    px(g, left, y, right - left + 1, 1, P.ink);
  }
  fillProfile(g, body, 4, 21, P.white);

  for (let y = 14; y <= 20; y += 1) {
    const { right } = bodyBounds(body, y);
    px(g, right - 1, y, 1, 1, P.light);
  }

  px(g, 11, 6, 1, 2, P.ink);
  px(g, 17, 6, 1, 2, P.ink);
  px(g, 9, 8, 1, 1, P.blush);
  px(g, 19, 8, 1, 1, P.blush);
  px(g, 14, 9, 2, 1, P.ink);

  const legs = legBounds(body);
  outlineBox(g, legs.left.start, 21, legs.left.end - legs.left.start + 1, 5, P.white);
  outlineBox(g, legs.right.start, 21, legs.right.end - legs.right.start + 1, 5, P.white);
}

function drawHat(g: Graphics, key: string) {
  const color = colorFor(key);
  if (key.includes('crown')) {
    px(g, 9, 3, 11, 3, P.ink);
    px(g, 10, 3, 2, 2, color); px(g, 13, 2, 3, 3, color); px(g, 17, 3, 2, 2, color);
    px(g, 10, 5, 9, 1, color);
    px(g, 14, 3, 1, 1, 0xe44f4f);
    return;
  }
  if (key.includes('wizard')) {
    px(g, 12, 0, 4, 1, P.ink); px(g, 11, 1, 6, 1, P.ink);
    px(g, 10, 2, 8, 2, P.ink); px(g, 8, 4, 13, 2, P.ink);
    px(g, 12, 1, 4, 1, color); px(g, 11, 2, 6, 2, color); px(g, 9, 4, 11, 1, color);
    px(g, 14, 2, 1, 1, P.gold);
    return;
  }
  if (key.includes('party')) {
    px(g, 13, 0, 3, 1, P.ink); px(g, 12, 1, 5, 1, P.ink);
    px(g, 11, 2, 7, 2, P.ink); px(g, 9, 4, 11, 2, P.ink);
    px(g, 14, 0, 1, 1, P.white); px(g, 13, 1, 3, 1, color);
    px(g, 12, 2, 5, 1, 0xf7bf47); px(g, 11, 3, 7, 1, 0x4ea4c8); px(g, 10, 4, 9, 1, color);
    return;
  }
  if (key.includes('beanie')) {
    px(g, 9, 1, 11, 5, P.ink);
    px(g, 10, 2, 9, 3, color);
    px(g, 10, 4, 9, 1, darken(color));
    if (key.includes('pom')) {
      px(g, 13, 0, 3, 2, P.ink); px(g, 14, 0, 1, 1, P.white);
    }
    return;
  }
  if (key.includes('chef')) {
    px(g, 9, 0, 11, 6, P.ink);
    px(g, 10, 1, 3, 3, P.white); px(g, 13, 0, 3, 4, P.white); px(g, 16, 1, 3, 3, P.white);
    px(g, 10, 4, 9, 1, P.white);
    return;
  }
  if (key.includes('helmet') || key.includes('knight')) {
    px(g, 8, 1, 13, 6, P.ink);
    px(g, 9, 2, 11, 4, color);
    px(g, 10, 4, 9, 1, darken(color));
    return;
  }
  if (key.includes('dinosaur')) {
    px(g, 8, 1, 13, 6, P.ink);
    px(g, 9, 2, 11, 4, color);
    px(g, 11, 2, 1, 1, P.white); px(g, 17, 2, 1, 1, P.white);
    px(g, 14, 0, 2, 2, darken(color));
    return;
  }
  if (key.includes('straw') || key.includes('safari') || key.includes('beach')) {
    px(g, 7, 3, 15, 3, P.ink);
    px(g, 10, 1, 9, 3, P.ink);
    px(g, 11, 2, 7, 2, color);
    px(g, 8, 4, 13, 1, color);
    px(g, 10, 3, 9, 1, darken(color));
    return;
  }

  // 캡과 제복 모자.
  px(g, 9, 2, 11, 4, P.ink);
  px(g, 10, 3, 9, 2, color);
  px(g, 8, 5, 14, 1, P.ink);
  px(g, 9, 5, 12, 1, color);
  px(g, 10, 4, 9, 1, darken(color));
}

function drawTop(g: Graphics, key: string, body: BodyState) {
  const color = colorFor(key);
  fillProfile(g, body, 12, 20, color);
  const shoulders = bodyBounds(body, 13);
  px(g, shoulders.left, 14, 2, 4, darken(color, 0.9));
  px(g, shoulders.right - 1, 14, 2, 4, darken(color, 0.9));

  if (key.includes('hoodie')) {
    px(g, CX - 3, 11, 7, 2, darken(color));
    px(g, CX - 2, 17, 5, 2, darken(color, 0.88));
    px(g, CX - 1, 12, 1, 3, P.white); px(g, CX + 1, 12, 1, 3, P.white);
  } else if (key.includes('overalls')) {
    px(g, CX - 3, 12, 1, 8, darken(color));
    px(g, CX + 3, 12, 1, 8, darken(color));
    px(g, CX - 3, 16, 7, 4, darken(color, 0.9));
  } else if (
    key.includes('suit') || key.includes('police') || key.includes('pilot') ||
    key.includes('legal')
  ) {
    px(g, CX - 1, 12, 3, 7, P.white);
    px(g, CX, 13, 1, 5, 0x202735);
    const waist = bodyBounds(body, 18);
    px(g, waist.left + 1, 18, waist.right - waist.left - 1, 1, darken(color));
  } else if (key.includes('wizard')) {
    const waist = bodyBounds(body, 18);
    px(g, waist.left + 1, 14, waist.right - waist.left - 1, 1, P.gold);
    px(g, waist.left + 1, 18, waist.right - waist.left - 1, 1, P.gold);
  } else if (key.includes('hanbok')) {
    const chest = bodyBounds(body, 13);
    px(g, chest.left + 1, 12, Math.floor((chest.right - chest.left) / 2), 4, P.white);
    px(g, CX, 12, 1, 8, 0xd95472);
  } else if (key.includes('wedding')) {
    const skirt = bodyBounds(body, 19);
    px(g, skirt.left, 17, skirt.right - skirt.left + 1, 4, 0xfff7fa);
    px(g, skirt.left + 1, 18, skirt.right - skirt.left - 1, 1, 0xf1bdca);
  } else if (key.includes('soccer') || key.includes('baseball')) {
    const row = bodyBounds(body, 16);
    px(g, row.left + 1, 16, row.right - row.left - 1, 1, P.white);
    px(g, CX, 13, 1, 5, darken(color));
  }
}

function drawBottom(g: Graphics, key: string, body: BodyState) {
  const color = colorFor(key);
  const legs = legBounds(body);
  const waist = bodyBounds(body, 19);
  px(g, waist.left + 1, 19, waist.right - waist.left - 1, 3, color);
  px(g, legs.left.start + 1, 21, Math.max(1, legs.left.end - legs.left.start - 1), 5, color);
  px(g, legs.right.start + 1, 21, Math.max(1, legs.right.end - legs.right.start - 1), 5, color);
  if (key.includes('striped')) {
    px(g, waist.left + 1, 20, waist.right - waist.left - 1, 1, P.white);
    px(g, legs.left.start + 1, 23, Math.max(1, legs.left.end - legs.left.start - 1), 1, P.white);
    px(g, legs.right.start + 1, 23, Math.max(1, legs.right.end - legs.right.start - 1), 1, P.white);
  } else if (key.includes('skirt')) {
    px(g, waist.left, 19, waist.right - waist.left + 1, 4, color);
  }
}

function drawShoes(g: Graphics, key: string, body: BodyState) {
  const color = colorFor(key);
  const legs = legBounds(body);
  const leftWidth = legs.left.end - legs.left.start + 1;
  const rightWidth = legs.right.end - legs.right.start + 1;
  if (key.includes('boots')) {
    px(g, legs.left.start, 22, leftWidth, 5, color);
    px(g, legs.right.start, 22, rightWidth, 5, color);
  } else {
    px(g, legs.left.start - 1, 25, leftWidth + 1, 2, color);
    px(g, legs.right.start, 25, rightWidth + 1, 2, color);
  }
  px(g, legs.left.start - 1, 26, leftWidth + 1, 1, darken(color));
  px(g, legs.right.start, 26, rightWidth + 1, 1, darken(color));
  if (key.includes('sneakers')) {
    px(g, legs.left.start, 25, leftWidth - 1, 1, P.white);
    px(g, legs.right.start + 1, 25, rightWidth - 1, 1, P.white);
    px(g, legs.left.start - 1, 26, leftWidth + 1, 1, 0xd9544d);
    px(g, legs.right.start, 26, rightWidth + 1, 1, 0xd9544d);
  }
}

function drawAccessory(g: Graphics, key: string) {
  const color = colorFor(key);
  if (key.includes('sunglasses') || key.includes('goggles')) {
    px(g, 9, 6, 11, 3, P.ink);
    px(g, 10, 7, 3, 1, color); px(g, 16, 7, 3, 1, color);
    px(g, 13, 7, 3, 1, P.ink);
  } else if (key.includes('headphones')) {
    px(g, 8, 5, 2, 4, P.ink); px(g, 19, 5, 2, 4, P.ink);
    px(g, 9, 2, 11, 2, color);
    px(g, 9, 6, 1, 3, color); px(g, 19, 6, 1, 3, color);
  } else if (key.includes('scarf')) {
    px(g, 8, 10, 13, 3, P.ink);
    px(g, 9, 11, 11, 1, color);
    px(g, 16, 12, 3, 5, color); px(g, 17, 16, 3, 1, darken(color));
  } else if (key.includes('bowtie')) {
    px(g, 11, 11, 7, 3, P.ink);
    px(g, 12, 12, 2, 1, color); px(g, 15, 12, 2, 1, color);
    px(g, 14, 12, 1, 2, darken(color));
  } else if (key.includes('necklace')) {
    px(g, 10, 11, 1, 1, color); px(g, 18, 11, 1, 1, color);
    px(g, 11, 12, 1, 1, color); px(g, 17, 12, 1, 1, color);
    px(g, 12, 13, 5, 1, color); px(g, 14, 14, 1, 2, darken(color));
  } else if (key.includes('backpack')) {
    px(g, 4, 12, 4, 9, P.ink); px(g, 5, 13, 2, 7, color);
    px(g, 21, 12, 4, 9, P.ink); px(g, 22, 13, 2, 7, color);
  } else if (key.includes('wings')) {
    px(g, 3, 11, 5, 9, color, 0.78); px(g, 21, 11, 5, 9, color, 0.78);
    px(g, 5, 9, 3, 4, P.white, 0.75); px(g, 21, 9, 3, 4, P.white, 0.75);
  } else if (key.includes('tail')) {
    px(g, 21, 16, 3, 7, color); px(g, 23, 20, 3, 3, color);
    px(g, 25, 20, 1, 1, P.white);
  } else if (key.includes('tube')) {
    px(g, 3, 17, 23, 5, P.ink);
    px(g, 4, 18, 21, 3, color);
    px(g, 9, 18, 11, 3, P.white);
  } else if (key.includes('cloud')) {
    px(g, 4, 24, 20, 4, color);
    px(g, 6, 22, 6, 4, color); px(g, 14, 21, 7, 5, color);
    px(g, 7, 27, 15, 1, 0xcbdfea);
  } else if (key.includes('wand')) {
    px(g, 21, 10, 2, 12, 0x8d5d36);
    px(g, 20, 8, 1, 5, P.gold); px(g, 18, 10, 5, 1, P.gold);
  } else if (key.includes('sword')) {
    px(g, 21, 9, 2, 12, color);
    px(g, 19, 19, 6, 2, P.gold);
  } else if (key.includes('briefcase')) {
    outlineBox(g, 20, 16, 7, 6, color);
    px(g, 22, 15, 3, 2, darken(color));
  } else if (key.includes('bouquet')) {
    px(g, 20, 12, 5, 5, color); px(g, 22, 16, 1, 6, 0x4c8b55);
    px(g, 19, 13, 2, 2, P.white); px(g, 23, 11, 2, 2, 0xf3c447);
  } else if (key.includes('glove')) {
    outlineBox(g, 21, 14, 5, 6, color);
  }
}

function drawBackground(g: Graphics, key: string) {
  const color = colorFor(key);
  px(g, 0, 0, GRID_W, GRID_H, color);
  if (key.includes('space')) {
    const stars = [[3, 4], [7, 9], [12, 3], [20, 6], [24, 12], [5, 18], [22, 20], [16, 25], [9, 27]];
    for (const [x, y] of stars) px(g, x, y, 1, 1, P.white);
    px(g, 21, 4, 3, 3, P.gold);
  } else if (key.includes('winter')) {
    const snow = [[4, 6], [10, 10], [18, 5], [24, 14], [8, 20], [15, 24], [22, 27]];
    for (const [x, y] of snow) px(g, x, y, 1, 1, P.white);
    px(g, 0, 27, GRID_W, 5, P.white);
  } else if (key.includes('summer')) {
    px(g, 0, 0, GRID_W, 12, 0x87d8ef);
    px(g, 0, 12, GRID_W, 13, 0x4badd6);
    for (let y = 14; y < 25; y += 4) px(g, 0, y, GRID_W, 1, P.white, 0.7);
    px(g, 0, 25, GRID_W, 7, 0xf2d394);
  } else if (key.includes('autumn')) {
    px(g, 0, 24, GRID_W, 8, 0x9e6339);
    px(g, 2, 8, 3, 17, 0x6f452c); px(g, 23, 7, 3, 18, 0x6f452c);
    px(g, 0, 5, 10, 8, color); px(g, 19, 4, 9, 9, 0xe09a3e);
  } else if (key.includes('spring')) {
    px(g, 0, 24, GRID_W, 8, 0x8fc876);
    px(g, 2, 7, 3, 17, 0x76553c); px(g, 23, 6, 3, 18, 0x76553c);
    px(g, 0, 4, 10, 9, color); px(g, 19, 3, 9, 10, color);
    const petals = [[5, 6], [12, 4], [19, 8], [24, 14], [8, 18]];
    for (const [x, y] of petals) px(g, x, y, 1, 1, P.white);
  }
}

export function drawCosmetic(g: Graphics, slot: Slot, key: string, body: BodyState) {
  switch (slot) {
    case 'HAT': return drawHat(g, key);
    case 'TOP': return drawTop(g, key, body);
    case 'BOTTOM': return drawBottom(g, key, body);
    case 'SHOES': return drawShoes(g, key, body);
    case 'FACE':
    case 'NECK':
    case 'BACK':
    case 'HAND':
    case 'EFFECT':
      return drawAccessory(g, key);
    case 'BACKGROUND': return drawBackground(g, key);
  }
}
