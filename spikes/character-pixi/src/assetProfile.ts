import rig from './character-rig.json';
import { CATALOG, CATALOG_BY_ID } from './catalog';
import type { BodyState, CosmeticDTO, EquippedMap, Slot } from './types';

export type RigSlot =
  | 'SCENE'
  | 'FLOOR'
  | 'BACK'
  | 'TAIL'
  | 'BASE'
  | 'TORSO'
  | 'FACE'
  | 'HAND_LEFT'
  | 'HAND_RIGHT'
  | 'HEAD'
  | 'EFFECT';

export type RigAnchor = keyof typeof rig.anchors;
export type AssetFitMode = 'COMMON' | 'TIER_HANDDRAWN' | 'NINE_SLICE';

export interface AssetProfile {
  fitMode: AssetFitMode;
  rigSlot: RigSlot;
  anchor: RigAnchor;
  pivot: { x: number; y: number };
  coversSlots: Slot[];
  occlusionMask: keyof typeof rig.occlusionMasks | null;
  availableTiers: BodyState[];
}

export const CHARACTER_RIG = rig;
export const BODY_STATES: BodyState[] = ['CHUBBY', 'BASE', 'SLIM', 'VERY_SLIM'];

const FULL_BODY_TOP_KEYS = new Set([
  'top_wizard_robe',
  'top_hanbok',
  'top_spacesuit',
  'top_fantasy_armor',
  'top_dinosaur',
  'top_wedding_dress',
]);

const NINE_SLICE_KEYS = new Set([
  'top_wizard_robe',
  'top_fantasy_armor',
  'top_padding',
  'effect_swim_tube',
]);

const HOOD_KEYS = new Set([
  'hat_space_helmet',
  'hat_knight',
  'hat_dinosaur',
  'hat_veil',
]);

function rigSlotFor(item: CosmeticDTO): RigSlot {
  if (item.slot === 'BACKGROUND') return 'SCENE';
  if (item.slot === 'EFFECT') return item.spriteKey === 'effect_fluffy_cloud' ? 'FLOOR' : 'EFFECT';
  if (item.slot === 'BACK') return item.spriteKey.includes('tail') ? 'TAIL' : 'BACK';
  if (item.slot === 'TOP' || item.slot === 'BOTTOM' || item.slot === 'NECK') return 'TORSO';
  if (item.slot === 'SHOES') return 'FLOOR';
  if (item.slot === 'FACE') return 'FACE';
  if (item.slot === 'HAND') return 'HAND_RIGHT';
  return 'HEAD';
}

function anchorFor(rigSlot: RigSlot): RigAnchor {
  const anchors: Record<RigSlot, RigAnchor> = {
    SCENE: 'CANVAS',
    FLOOR: 'FLOOR',
    BACK: 'BACK_CENTER',
    TAIL: 'TAIL',
    BASE: 'TORSO_CENTER',
    TORSO: 'TORSO_CENTER',
    FACE: 'FACE_CENTER',
    HAND_LEFT: 'HAND_LEFT',
    HAND_RIGHT: 'HAND_RIGHT',
    HEAD: 'HEAD_TOP',
    EFFECT: 'EFFECT_CENTER',
  };
  return anchors[rigSlot];
}

function profileFor(item: CosmeticDTO): AssetProfile {
  const rigSlot = rigSlotFor(item);
  const anchor = anchorFor(rigSlot);
  const anchorPoint = rig.anchors[anchor];

  let fitMode: AssetFitMode = 'COMMON';
  if (item.slot === 'TOP' || item.slot === 'BOTTOM') fitMode = 'TIER_HANDDRAWN';
  if (NINE_SLICE_KEYS.has(item.spriteKey)) fitMode = 'NINE_SLICE';

  return {
    fitMode,
    rigSlot,
    anchor,
    // 현재 프로시저럴 텍스처는 전체 캔버스이므로 pivot과 anchor가 같아 위치 결과가 항상 0이다.
    // 실제 잘린 PNG를 도입할 때 pivot만 JSON 에셋 메타데이터 값으로 교체한다.
    pivot: { x: anchorPoint.x, y: anchorPoint.y },
    coversSlots: FULL_BODY_TOP_KEYS.has(item.spriteKey) ? ['BOTTOM'] : [],
    occlusionMask: HOOD_KEYS.has(item.spriteKey) ? 'hood' : null,
    availableTiers: fitMode === 'TIER_HANDDRAWN' ? BODY_STATES : [],
  };
}

export const ASSET_PROFILE_BY_KEY: Record<string, AssetProfile> = Object.fromEntries(
  CATALOG.map((item) => [item.spriteKey, profileFor(item)]),
);

export function assetProfileFor(item: CosmeticDTO): AssetProfile {
  return ASSET_PROFILE_BY_KEY[item.spriteKey];
}

const TIER_NAME: Record<BodyState, string> = {
  CHUBBY: 't3',
  BASE: 't2',
  SLIM: 't1',
  VERY_SLIM: 't0',
};

function itemSlug(item: CosmeticDTO): string {
  return item.spriteKey
    .replace(/^(hat|face|neck|top|bottom|shoes|back|hand|effect|bg)_/, '')
    .replaceAll('_', '');
}

export function resolveAssetTier(item: CosmeticDTO, requested: BodyState): BodyState | null {
  const profile = assetProfileFor(item);
  if (profile.fitMode !== 'TIER_HANDDRAWN') return null;
  if (profile.availableTiers.includes(requested)) return requested;

  const requestedIndex = BODY_STATES.indexOf(requested);
  const fallback = profile.availableTiers
    .slice()
    .sort((a, b) => (
      Math.abs(BODY_STATES.indexOf(a) - requestedIndex)
      - Math.abs(BODY_STATES.indexOf(b) - requestedIndex)
    ))[0];

  if (fallback) {
    console.warn(`[character-rig] ${item.spriteKey}: ${requested} 누락, ${fallback} 티어로 폴백`);
    return fallback;
  }
  console.warn(`[character-rig] ${item.spriteKey}: 사용 가능한 체형 에셋이 없음`);
  return null;
}

export function frameKeyFor(item: CosmeticDTO, body: BodyState): string {
  const profile = assetProfileFor(item);
  const slot = profile.rigSlot.toLowerCase();
  const slug = itemSlug(item);
  if (profile.fitMode === 'TIER_HANDDRAWN') {
    const tier = resolveAssetTier(item, body) ?? body;
    return `${slot}_${slug}_${TIER_NAME[tier]}_00.png`;
  }
  if (profile.fitMode === 'NINE_SLICE') return `${slot}_${slug}_9slice_00.png`;
  return `${slot}_${slug}_common_00.png`;
}

export function placementFor(item: CosmeticDTO): { x: number; y: number } {
  const profile = assetProfileFor(item);
  const anchor = rig.anchors[profile.anchor];
  return {
    x: Math.round(anchor.x - profile.pivot.x),
    y: Math.round(anchor.y - profile.pivot.y),
  };
}

export function conflictsFor(item: CosmeticDTO, equipped: EquippedMap): Slot[] {
  const conflicts = new Set<Slot>();
  if (item.slot === 'TOP' && FULL_BODY_TOP_KEYS.has(item.spriteKey)) conflicts.add('BOTTOM');
  if (item.slot === 'BOTTOM') {
    const currentTop = equipped.TOP ? CATALOG_BY_ID[equipped.TOP] : null;
    if (currentTop && FULL_BODY_TOP_KEYS.has(currentTop.spriteKey)) conflicts.add('TOP');
  }
  return [...conflicts];
}

export const PRODUCT_SPRITE_FILE_COUNT = CATALOG.reduce((count, item) => {
  const mode = assetProfileFor(item).fitMode;
  return count + (mode === 'TIER_HANDDRAWN' ? BODY_STATES.length : 1);
}, 0);

export function validateRig(): string[] {
  const errors: string[] = [];
  for (const [name, anchor] of Object.entries(rig.anchors)) {
    if (!Number.isInteger(anchor.x) || !Number.isInteger(anchor.y)) {
      errors.push(`${name} 앵커가 정수 좌표가 아님`);
    }
  }

  const widths = BODY_STATES.map((body) => rig.bodyTiers[body].torsoWidth);
  for (let index = 1; index < widths.length; index += 1) {
    if (Math.abs(widths[index - 1] - widths[index]) % 2 !== 0) {
      errors.push(`${BODY_STATES[index - 1]} → ${BODY_STATES[index]} 폭 차이가 짝수가 아님`);
    }
  }
  return errors;
}
