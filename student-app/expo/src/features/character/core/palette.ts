/**
 * 캐릭터 공용 팔레트. 웹·Expo·PNG 생성 스크립트가 모두 이 값을 참조한다.
 * scripts/character/lib/palette.js와 값이 같아야 한다.
 * 규격: docs/design/CHARACTER_ASSET_SPEC.md §4
 */

export const CHARACTER_PALETTE = {
  /** 외곽선·눈·입 */
  ink: '#262b31',
  /** 몸 기본색 */
  body: '#fffdf8',
  /** 안쪽 테두리 */
  rim: '#dce2e1',
  /** 방향성 음영 */
  shade: '#eef1f0',
  /** 볼 */
  cheek: '#f1a3b5',
} as const;

/** 가림 마스크는 알파 스텐실이므로 단색만 쓴다. */
export const CHARACTER_MASK_COLOR = '#ffffff';

/**
 * 폐기된 값. 절차적 렌더러가 쓰던 잉크·볼 색이며 PNG 값과 미묘하게 달라
 * 렌더 경로에 따라 색이 바뀌는 원인이었다. 되살리지 않는다.
 *
 *   ink   '#273038'  →  CHARACTER_PALETTE.ink
 *   cheek '#f3a6b8'  →  CHARACTER_PALETTE.cheek
 */

export type CharacterPalette = typeof CHARACTER_PALETTE;
