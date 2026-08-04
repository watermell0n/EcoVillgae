import rig from './character-rig.json';
import { CATALOG, CATALOG_BY_ID } from './catalog';
import type { CosmeticDTO, EquippedMap, Slot } from './types';

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
export type AssetFitMode = 'COMMON' | 'BODY_VARIANT' | 'POSITION_VARIANT';

export interface PixelOffset {
  x: number;
  y: number;
}

export interface AssetProfile {
  fitMode: AssetFitMode;
  rigSlot: RigSlot;
  anchor: RigAnchor;
  pivot: PixelOffset;
  offset: Readonly<PixelOffset>;
  coversSlots: Slot[];
}

export const CHARACTER_RIG = rig;

/** 리그가 정한 레이어 순서. 렌더러는 이 순서대로만 겹친다. */
export const RIG_LAYER_ORDER: readonly RigSlot[] = [...rig.layers]
  .sort((a, b) => a.z - b.z)
  .map((layer) => layer.slot as RigSlot);

const ZERO_OFFSET: Readonly<PixelOffset> = { x: 0, y: 0 };

const FULL_BODY_TOP_KEYS = new Set([
  'top_wizard_robe',
  'top_spacesuit',
  'top_fantasy_armor',
  'top_dinosaur',
]);

/** 체형 폭에 맞춰 다시 그려야 하는 장비. */
const BODY_VARIANT_KEYS = new Set([
  'hat_space_helmet',
  'hat_knight',
  'hat_dinosaur',
  'neck_scarf',
  'neck_knit_scarf',
  'back_dinosaur_tail',
  'effect_swim_tube',
]);

/** 손·등 위치에 맞춰 좌표만 옮기면 되는 장비. */
const POSITION_VARIANT_KEYS = new Set([
  'back_backpack',
  'back_fairy_wings',
  'back_briefcase',
  'hand_baseball_glove',
  'hand_magic_wand',
  'hand_toy_sword',
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

const ANCHOR_BY_RIG_SLOT: Record<RigSlot, RigAnchor> = {
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

function profileFor(item: CosmeticDTO): AssetProfile {
  const rigSlot = rigSlotFor(item);
  const anchor = ANCHOR_BY_RIG_SLOT[rigSlot];
  const anchorPoint = rig.anchors[anchor];

  let fitMode: AssetFitMode = 'COMMON';
  if (
    item.slot === 'TOP'
    || item.slot === 'BOTTOM'
    || item.slot === 'SHOES'
    || BODY_VARIANT_KEYS.has(item.spriteKey)
  ) {
    fitMode = 'BODY_VARIANT';
  } else if (POSITION_VARIANT_KEYS.has(item.spriteKey)) {
    fitMode = 'POSITION_VARIANT';
  }

  return {
    fitMode,
    rigSlot,
    anchor,
    // 배포된 레이어 스프라이트는 캔버스 전체를 덮는 48x56 PNG라 pivot이 anchor와 같고
    // placementFor는 항상 (0,0)을 돌려준다. 잘라낸 스프라이트를 도입하면 pivot만
    // 에셋 메타데이터 값으로 교체하면 되고, 그때 anchor 좌표도 48x56 기준으로 다시 잰다.
    pivot: { x: anchorPoint.x, y: anchorPoint.y },
    offset: ZERO_OFFSET,
    coversSlots: FULL_BODY_TOP_KEYS.has(item.spriteKey) ? ['BOTTOM'] : [],
  };
}

export const ASSET_PROFILE_BY_KEY: Record<string, AssetProfile> = Object.fromEntries(
  CATALOG.map((item) => [item.spriteKey, profileFor(item)]),
);

export function assetProfileFor(item: CosmeticDTO): AssetProfile {
  return ASSET_PROFILE_BY_KEY[item.spriteKey];
}

/** 리그 앵커에서 스프라이트 pivot을 뺀 정수 배치 좌표. */
export function placementFor(
  item: CosmeticDTO,
  offsetOverride?: PixelOffset,
): PixelOffset {
  const profile = assetProfileFor(item);
  const anchor = rig.anchors[profile.anchor];
  const offset = offsetOverride ?? profile.offset;
  return {
    x: Math.round(anchor.x - profile.pivot.x + offset.x),
    y: Math.round(anchor.y - profile.pivot.y + offset.y),
  };
}

/**
 * 같은 슬롯은 교체하고 다른 슬롯은 유지한다.
 * 전신 의상과 하의처럼 실제로 겹칠 수 없는 조합만 명시적으로 충돌시킨다.
 */
export function conflictsFor(item: CosmeticDTO, equipped: EquippedMap): Slot[] {
  const conflicts = new Set<Slot>();
  if (item.slot === 'TOP' && FULL_BODY_TOP_KEYS.has(item.spriteKey)) {
    conflicts.add('BOTTOM');
  }
  if (item.slot === 'BOTTOM') {
    const currentTop = equipped.TOP ? CATALOG_BY_ID[equipped.TOP] : null;
    if (currentTop && FULL_BODY_TOP_KEYS.has(currentTop.spriteKey)) {
      conflicts.add('TOP');
    }
  }
  return [...conflicts];
}

/** 리그가 정수 좌표 규약을 지키는지 확인한다. 개발 중 회귀 감지용이다. */
export function validateRig(): string[] {
  const errors: string[] = [];
  for (const [name, anchor] of Object.entries(rig.anchors)) {
    if (!Number.isInteger(anchor.x) || !Number.isInteger(anchor.y)) {
      errors.push(`${name} 앵커가 정수 좌표가 아님`);
    }
    if (anchor.x > rig.canvas.logicalWidth || anchor.y > rig.canvas.logicalHeight) {
      errors.push(`${name} 앵커가 캔버스를 벗어남`);
    }
  }
  for (const [spriteKey, profile] of Object.entries(ASSET_PROFILE_BY_KEY)) {
    if (!Number.isInteger(profile.offset.x) || !Number.isInteger(profile.offset.y)) {
      errors.push(`${spriteKey} 위치 보정이 정수 좌표가 아님`);
    }
  }
  return errors;
}
