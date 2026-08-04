import type { CosmeticDTO, Theme } from '../types';

interface SheetSpec {
  src: string;
  width: number;
  x: number[];
  y: number[];
  cardWidth: number;
  keys: string[];
  columns: number;
}

const SHEETS: Record<Theme, SheetSpec> = {
  BASIC: {
    src: '/reference/basic.png',
    width: 1626,
    x: [36, 350, 665, 979, 1294],
    y: [108, 511],
    cardWidth: 300,
    columns: 5,
    keys: [
      'hat_red_cap', 'hat_beanie', 'face_sunglasses', 'face_headphones', 'top_blue_tee',
      'top_yellow_hoodie', 'top_green_vest', 'top_overalls', 'shoes_sneakers', 'back_backpack',
    ],
  },
  SEASONAL: {
    src: '/reference/seasonal.png',
    width: 1536,
    x: [35, 283, 530, 777, 1024, 1271],
    y: [90, 374, 657],
    cardWidth: 239,
    columns: 6,
    keys: [
      'hat_straw', 'hat_beach', 'hat_pom_beanie', 'face_goggles', 'face_ski_goggles', 'neck_scarf',
      'neck_knit_scarf', 'top_swimsuit', 'top_padding', 'shoes_red_boots', 'shoes_sandals', 'shoes_aqua',
      'shoes_snow_boots', 'effect_swim_tube', 'bg_spring', 'bg_summer_sea', 'bg_autumn', 'bg_winter',
    ],
  },
  SPORTS: {
    src: '/reference/sports.png',
    width: 1672,
    x: [55, 316, 577, 838, 1098, 1359],
    y: [113, 490],
    cardWidth: 252,
    columns: 6,
    keys: [
      'hat_baseball_blue', 'hat_baseball_red', 'hat_baseball_navy', 'hat_baseball_green',
      'hat_baseball_yellow', 'top_soccer', 'top_baseball_pinstripe', 'top_baseball_red',
      'top_baseball_blue', 'top_baseball_navy', 'top_baseball_gray', 'hand_baseball_glove',
    ],
  },
  CAREER: {
    src: '/reference/career.png',
    width: 1774,
    x: [27, 325, 621, 918, 1215, 1512],
    y: [103, 470],
    cardWidth: 282,
    columns: 6,
    keys: [
      'hat_chef', 'hat_safari', 'hat_space_helmet', 'hat_safety_police', 'hat_pilot', 'top_spacesuit',
      'top_safety_police', 'top_legal_professional', 'top_pilot', 'shoes_space_boots',
      'hand_briefcase', 'bg_space',
    ],
  },
  FANTASY: {
    src: '/reference/fantasy.png',
    width: 1717,
    x: [23, 308, 593, 878, 1163, 1447],
    y: [92, 463],
    cardWidth: 274,
    columns: 6,
    keys: [
      'hat_crown', 'hat_wizard', 'hat_knight', 'hat_dinosaur', 'top_wizard_robe',
      'top_fantasy_armor', 'top_dinosaur', 'back_fairy_wings', 'back_dinosaur_tail',
      'hand_magic_wand', 'hand_toy_sword', 'effect_fluffy_cloud',
    ],
  },
  EVENT: {
    src: '/reference/event.png',
    width: 1536,
    x: [53, 424, 777, 1130],
    y: [95, 527],
    cardWidth: 354,
    columns: 4,
    keys: [
      'hat_party', 'hat_veil', 'neck_bowtie', 'neck_necklace',
      'top_suit', 'top_hanbok', 'top_wedding_dress', 'hand_bouquet',
    ],
  },
};

const FALLBACK_SYMBOL: Record<CosmeticDTO['slot'], string> = {
  BACKGROUND: '▧',
  EFFECT: '✦',
  BACK: '▰',
  BOTTOM: '▥',
  SHOES: '▱',
  TOP: '▣',
  NECK: '◇',
  FACE: '◉',
  HAND: '✧',
  HAT: '⌒',
};

export function ProductPreview({ item }: { item: CosmeticDTO }) {
  const sheet = SHEETS[item.theme];
  const index = sheet.keys.indexOf(item.spriteKey);

  if (index < 0) {
    return (
      <div className="product-visual product-visual--fallback" aria-label={`${item.name} 미리보기`}>
        <span>{FALLBACK_SYMBOL[item.slot]}</span>
      </div>
    );
  }

  const column = index % sheet.columns;
  const row = Math.floor(index / sheet.columns);
  const scale = 100 / sheet.cardWidth;

  return (
    <div className="product-visual" aria-label={`${item.name} 착용 미리보기`}>
      <img
        src={sheet.src}
        alt=""
        draggable={false}
        style={{
          width: `${sheet.width * scale}%`,
          left: `${-sheet.x[column] * scale}%`,
          top: `${-sheet.y[row] * scale}%`,
        }}
      />
    </div>
  );
}
