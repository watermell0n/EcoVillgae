const PIXEL_ASSET_VERSION = '20260804-5';

export const BASE_CHARACTER_PROFILE = {
  id: 'BASE',
  masterWidth: 64,
  masterHeight: 64,
  renderWidth: 48,
  renderHeight: 56,
  centerX: 32,
  floorY: 60,
  masterSprite: `/character/base/base_char_base.png?v=${PIXEL_ASSET_VERSION}`,
  renderSprite: `/character/base/base_char_base_48x56.png?v=${PIXEL_ASSET_VERSION}`,
} as const;

export type CharacterBody = typeof BASE_CHARACTER_PROFILE.id;
