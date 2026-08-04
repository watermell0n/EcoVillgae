import type { ImageSourcePropType } from 'react-native';

import type { Slot } from './types';

/**
 * Metro는 정적 require 경로만 번들에 담기 때문에 스프라이트를 URL이 아닌 맵으로 선언한다.
 * 웹 전용 경로(`/character/...`)를 쓰면 태블릿 네이티브 빌드에서 이미지가 비므로 여기서 갈린다.
 */
const SPRITES = {
  base: require('../../../assets/character/base/base_char_base_48x56.png') as ImageSourcePropType,
  back_backpack: require('../../../assets/character/layers/basic/back_backpack_48x56.png') as ImageSourcePropType,
  bottom_overalls: require('../../../assets/character/layers/basic/bottom_overalls_48x56.png') as ImageSourcePropType,
  face_headphones_front: require('../../../assets/character/layers/basic/face_headphones_front_48x56.png') as ImageSourcePropType,
  face_headphones_back: require('../../../assets/character/layers/basic/face_headphones_back_48x56.png') as ImageSourcePropType,
  face_sunglasses: require('../../../assets/character/layers/basic/face_sunglasses_48x56.png') as ImageSourcePropType,
  hat_red_cap: require('../../../assets/character/layers/basic/hat_red_cap_48x56.png') as ImageSourcePropType,
  hat_teal_beanie: require('../../../assets/character/layers/basic/hat_teal_beanie_48x56.png') as ImageSourcePropType,
  shoes_sneakers: require('../../../assets/character/layers/basic/shoes_sneakers_48x56.png') as ImageSourcePropType,
  top_blue_tshirt: require('../../../assets/character/layers/basic/top_blue_tshirt_48x56.png') as ImageSourcePropType,
  top_green_vest: require('../../../assets/character/layers/basic/top_green_vest_48x56.png') as ImageSourcePropType,
  top_yellow_hoodie: require('../../../assets/character/layers/basic/top_yellow_hoodie_48x56.png') as ImageSourcePropType,
} as const;

export const BASE_SPRITE = SPRITES.base;

export interface CharacterLayerProfile {
  slot: Slot;
  sprite: ImageSourcePropType;
  /**
   * 베이스 몸체 앞/뒤 중 어디에 놓을지. 리그 z순서는 같은 면 안에서의 순서만 정하고,
   * 면 자체는 스프라이트가 그려진 방식에 따라 결정한다. 현재 배포된 48x56 레이어는
   * 자기 가림을 스프라이트 안에서 처리하므로 전부 FRONT다.
   */
  placement: 'BEHIND_BASE' | 'FRONT';
  /** 베이스보다 뒤로 지나가는 보조 레이어. 헤드폰 밴드가 머리 뒤를 감싸는 부분이다. */
  backSprite?: ImageSourcePropType;
}

function layer(
  slot: Slot,
  sprite: ImageSourcePropType,
  extra: Partial<Pick<CharacterLayerProfile, 'placement' | 'backSprite'>> = {},
): CharacterLayerProfile {
  return { slot, sprite, placement: extra.placement ?? 'FRONT', backSprite: extra.backSprite };
}

export const LAYER_PROFILES: Readonly<Record<string, CharacterLayerProfile>> = {
  hat_red_cap: layer('HAT', SPRITES.hat_red_cap),
  hat_teal_beanie: layer('HAT', SPRITES.hat_teal_beanie),
  face_sunglasses: layer('FACE', SPRITES.face_sunglasses),
  face_headphones: layer('FACE', SPRITES.face_headphones_front, {
    backSprite: SPRITES.face_headphones_back,
  }),
  top_blue_tshirt: layer('TOP', SPRITES.top_blue_tshirt),
  top_yellow_hoodie: layer('TOP', SPRITES.top_yellow_hoodie),
  top_green_vest: layer('TOP', SPRITES.top_green_vest),
  bottom_overalls: layer('BOTTOM', SPRITES.bottom_overalls),
  shoes_sneakers: layer('SHOES', SPRITES.shoes_sneakers),
  back_backpack: layer('BACK', SPRITES.back_backpack),
};

export function layerProfileFor(spriteKey: string | null | undefined) {
  return spriteKey ? LAYER_PROFILES[spriteKey] ?? null : null;
}

/** 카탈로그 64종 중 아직 스프라이트가 배포되지 않은 항목을 구분한다. */
export function hasSprite(spriteKey: string | null | undefined) {
  return layerProfileFor(spriteKey) !== null;
}
