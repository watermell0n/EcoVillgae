import type { BodyState, CosmeticDTO } from './types';

interface CatalogSheet {
  src: string;
  x: number[];
  y: number[];
  cardWidth: number;
  artHeight: number;
  columns: number;
  keys: string[];
}

export interface CatalogArtFrame {
  src: string;
  x: number;
  y: number;
  width: number;
  height: number;
  morphCenterX?: number;
}

/*
 * The reference sheets remain the visual source of truth until the 64x72
 * transparent production sprites are delivered. Both the catalog cards and
 * the large try-on view resolve through this one table so they cannot drift.
 */
const CATALOG_SHEETS: CatalogSheet[] = [
  {
    src: '/character/reference/basic.png',
    x: [36, 350, 665, 979, 1294],
    y: [108, 511],
    cardWidth: 300,
    artHeight: 316,
    columns: 5,
    keys: [
      'hat_red_cap', 'hat_beanie', 'face_sunglasses', 'face_headphones', 'top_blue_tee',
      'top_yellow_hoodie', 'top_green_vest', 'top_overalls', 'shoes_sneakers', 'back_backpack',
    ],
  },
  {
    src: '/character/reference/seasonal.png',
    x: [35, 283, 530, 777, 1024, 1271],
    y: [90, 374, 657],
    cardWidth: 239,
    artHeight: 244,
    columns: 6,
    keys: [
      'hat_straw', 'hat_beach', 'hat_pom_beanie', 'face_goggles', 'face_ski_goggles', 'neck_scarf',
      'neck_knit_scarf', 'top_swimsuit', 'top_padding', 'shoes_red_boots', 'shoes_sandals', 'shoes_aqua',
      'shoes_snow_boots', 'effect_swim_tube', 'bg_spring', 'bg_summer_sea', 'bg_autumn', 'bg_winter',
    ],
  },
  {
    src: '/character/reference/sports.png',
    x: [55, 316, 577, 838, 1098, 1359],
    y: [113, 490],
    cardWidth: 252,
    artHeight: 282,
    columns: 6,
    keys: [
      'hat_baseball_blue', 'hat_baseball_red', 'hat_baseball_navy', 'hat_baseball_green',
      'hat_baseball_yellow', 'top_soccer', 'top_baseball_pinstripe', 'top_baseball_red',
      'top_baseball_blue', 'top_baseball_navy', 'top_baseball_gray', 'hand_baseball_glove',
    ],
  },
  {
    src: '/character/reference/career.png',
    x: [27, 325, 621, 918, 1215, 1512],
    y: [103, 470],
    cardWidth: 282,
    artHeight: 300,
    columns: 6,
    keys: [
      'hat_chef', 'hat_safari', 'hat_space_helmet', 'hat_safety_police', 'hat_pilot', 'top_spacesuit',
      'top_safety_police', 'top_legal_professional', 'top_pilot', 'shoes_space_boots',
      'hand_briefcase', 'bg_space',
    ],
  },
  {
    src: '/character/reference/fantasy.png',
    x: [23, 308, 593, 878, 1163, 1447],
    y: [92, 463],
    cardWidth: 274,
    artHeight: 292,
    columns: 6,
    keys: [
      'hat_crown', 'hat_wizard', 'hat_knight', 'hat_dinosaur', 'top_wizard_robe',
      'top_fantasy_armor', 'top_dinosaur', 'back_fairy_wings', 'back_dinosaur_tail',
      'hand_magic_wand', 'hand_toy_sword', 'effect_fluffy_cloud',
    ],
  },
  {
    src: '/character/reference/event.png',
    x: [53, 424, 777, 1130],
    y: [95, 527],
    cardWidth: 354,
    artHeight: 340,
    columns: 4,
    keys: [
      'hat_party', 'hat_veil', 'neck_bowtie', 'neck_necklace',
      'top_suit', 'top_hanbok', 'top_wedding_dress', 'hand_bouquet',
    ],
  },
  {
    src: '/character/reference/bottoms-v1.png',
    x: [18, 373, 720, 1054, 1393],
    y: [176],
    cardWidth: 330,
    artHeight: 500,
    columns: 5,
    keys: [
      'bottom_striped_pants', 'bottom_jeans', 'bottom_shorts', 'bottom_skirt', 'bottom_slacks',
    ],
  },
];

const ART_BY_KEY = new Map<string, CatalogArtFrame>();

// A few generated reference cards are not centered inside their crop. Keep
// the character's body axis explicit so row morphing cannot pull it sideways.
const MORPH_CENTER_X_BY_KEY: Record<string, number> = {
  top_wizard_robe: 104,
};

for (const sheet of CATALOG_SHEETS) {
  sheet.keys.forEach((key, index) => {
    const column = index % sheet.columns;
    const row = Math.floor(index / sheet.columns);
    ART_BY_KEY.set(key, {
      src: sheet.src,
      x: sheet.x[column],
      y: sheet.y[row],
      width: sheet.cardWidth,
      height: sheet.artHeight,
      morphCenterX: MORPH_CENTER_X_BY_KEY[key],
    });
  });
}

export function catalogArtFor(item: Pick<CosmeticDTO, 'spriteKey'>): CatalogArtFrame | null {
  return ART_BY_KEY.get(item.spriteKey) ?? null;
}

export function missingCatalogArtKeys(items: readonly Pick<CosmeticDTO, 'spriteKey'>[]): string[] {
  return items
    .map((item) => item.spriteKey)
    .filter((spriteKey) => !ART_BY_KEY.has(spriteKey));
}

export const BODY_WIDTH_RATIO: Record<BodyState, number> = {
  CHUBBY: 30 / 26,
  BASE: 1,
  // Keep the slim tiers on whole, symmetric logical-cell widths.
  SLIM: 22 / 26,
  VERY_SLIM: 18 / 26,
};
