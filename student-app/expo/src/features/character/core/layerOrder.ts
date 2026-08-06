/**
 * 합성 레이어 순서. 웹·Expo가 같은 배열을 쓰므로 두 플랫폼의 겹침이 어긋날 수 없다.
 * 규격: docs/design/CHARACTER_ASSET_SPEC.md §5
 */

import type { Slot } from './types';

/** 아래(먼저 그림) → 위(나중에 그림) */
export const CHARACTER_LAYER_ORDER = [
  'BACKGROUND',
  'BACK_BEHIND',
  'EFFECT_BEHIND',
  'BASE_BODY',
  'SHOES',
  'BOTTOM',
  'TOP',
  'NECK',
  'FACE',
  'HAT',
  'HAND_BEHIND',
  'HAND_FRONT',
  'BACK_FRONT',
  'EFFECT_FRONT',
] as const;

export type CharacterLayerBand = (typeof CHARACTER_LAYER_ORDER)[number];

/** 상품 한 종이 가질 수 있는 레이어 부위. */
export type ItemLayerPart = 'behind' | 'main' | 'front';

const BAND_INDEX: Record<CharacterLayerBand, number> = Object.fromEntries(
  CHARACTER_LAYER_ORDER.map((band, index) => [band, index]),
) as Record<CharacterLayerBand, number>;

/**
 * 슬롯과 부위를 레이어 밴드로 매핑한다.
 * BASE보다 뒤로 가야 하는 부위는 `behind`, 의상 위로 올라가야 하는 부위는 `front`다.
 */
const BAND_BY_SLOT: Record<Slot, Record<ItemLayerPart, CharacterLayerBand>> = {
  BACKGROUND: { behind: 'BACKGROUND', main: 'BACKGROUND', front: 'BACKGROUND' },
  EFFECT: { behind: 'EFFECT_BEHIND', main: 'EFFECT_BEHIND', front: 'EFFECT_FRONT' },
  BACK: { behind: 'BACK_BEHIND', main: 'BACK_BEHIND', front: 'BACK_FRONT' },
  SHOES: { behind: 'SHOES', main: 'SHOES', front: 'SHOES' },
  BOTTOM: { behind: 'BOTTOM', main: 'BOTTOM', front: 'BOTTOM' },
  TOP: { behind: 'TOP', main: 'TOP', front: 'TOP' },
  NECK: { behind: 'NECK', main: 'NECK', front: 'NECK' },
  FACE: { behind: 'BACK_BEHIND', main: 'FACE', front: 'FACE' },
  HAND: { behind: 'HAND_BEHIND', main: 'HAND_FRONT', front: 'HAND_FRONT' },
  HAT: { behind: 'BACK_BEHIND', main: 'HAT', front: 'HAT' },
};

export function bandFor(slot: Slot, part: ItemLayerPart): CharacterLayerBand {
  return BAND_BY_SLOT[slot][part];
}

/** 정렬용 z값. 낮을수록 먼저(아래에) 그린다. */
export function bandZ(band: CharacterLayerBand): number {
  return BAND_INDEX[band];
}

export const BASE_BODY_Z = BAND_INDEX.BASE_BODY;
