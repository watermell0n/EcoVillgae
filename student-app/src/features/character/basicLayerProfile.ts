import type { Slot } from './types';

const PIXEL_ASSET_VERSION = '20260804-17';

export interface CharacterLayerProfile {
  slot: Slot;
  placement: 'BEHIND_BASE' | 'FRONT';
  frontPlacement: 'NORMAL' | 'OVERLAY';
  masterSprite: string;
  masterBackSprite?: string;
  masterOcclusionSprite?: string;
  renderSprite: string;
}

interface BasicLayerOptions {
  placement?: CharacterLayerProfile['placement'];
  frontPlacement?: CharacterLayerProfile['frontPlacement'];
  masterFrontKey?: string;
  masterBackKey?: string;
  masterOcclusionKey?: string;
}

function spriteSource(spriteKey: string, size: 'master' | 'render') {
  const suffix = size === 'master' ? '.png' : '_48x56.png';
  return `/character/layers/basic/${spriteKey}${suffix}?v=${PIXEL_ASSET_VERSION}`;
}

function basicLayer(
  spriteKey: string,
  slot: Slot,
  options: BasicLayerOptions = {},
): CharacterLayerProfile {
  const masterFrontKey = options.masterFrontKey ?? spriteKey;
  return {
    slot,
    placement: options.placement ?? 'FRONT',
    frontPlacement: options.frontPlacement ?? 'NORMAL',
    masterSprite: spriteSource(masterFrontKey, 'master'),
    masterBackSprite: options.masterBackKey
      ? spriteSource(options.masterBackKey, 'master')
      : undefined,
    masterOcclusionSprite: options.masterOcclusionKey
      ? spriteSource(options.masterOcclusionKey, 'master')
      : undefined,
    renderSprite: spriteSource(spriteKey, 'render'),
  };
}

export const BASIC_LAYER_PROFILES: Readonly<Record<string, CharacterLayerProfile>> = {
  hat_red_cap: basicLayer('hat_red_cap', 'HAT'),
  hat_teal_beanie: basicLayer('hat_teal_beanie', 'HAT'),
  face_sunglasses: basicLayer('face_sunglasses', 'FACE'),
  face_headphones: basicLayer('face_headphones', 'FACE', {
    frontPlacement: 'OVERLAY',
    masterFrontKey: 'face_headphones_front',
    masterBackKey: 'face_headphones_back',
  }),
  top_blue_tshirt: basicLayer('top_blue_tshirt', 'TOP', {
    masterOcclusionKey: 'top_blue_tshirt_occlusion',
  }),
  top_yellow_hoodie: basicLayer('top_yellow_hoodie', 'TOP'),
  top_green_vest: basicLayer('top_green_vest', 'TOP'),
  bottom_overalls: basicLayer('bottom_overalls', 'BOTTOM'),
  shoes_sneakers: basicLayer('shoes_sneakers', 'SHOES', {
    masterOcclusionKey: 'shoes_sneakers_occlusion',
  }),
  back_backpack: basicLayer('back_backpack', 'BACK'),
};

export function basicLayerProfileFor(spriteKey: string | null | undefined) {
  return spriteKey ? BASIC_LAYER_PROFILES[spriteKey] ?? null : null;
}
