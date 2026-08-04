// 공용 팔레트 정본. student-app 쪽 TS 팔레트와 값이 같아야 한다.
// docs/design/CHARACTER_ASSET_SPEC.md §4 참조.

/** 몸체 공용 5색 */
const SHARED = {
  ink: '#262b31',
  body: '#fffdf8',
  rim: '#dce2e1',
  shade: '#eef1f0',
  cheek: '#f1a3b5',
};

/** 상품별 3단 명암 램프. 상품을 추가하면 여기에 등록한다. */
const ITEM_RAMPS = {
  hat_red_cap: ['#d94b45', '#a93b38', '#73302f'],
  hat_teal_beanie: ['#09a8b2', '#087f8c', '#075a66'],
  face_sunglasses: ['#4b535e', '#151b20', '#77838b'],
  face_headphones: ['#aeb7bd', '#77818a', '#4b535e'],
  // #8ba4c8은 x21/x42 미러 쌍으로 찍힌 어깨 하이라이트다.
  top_blue_tshirt: ['#5f80b0', '#466696', '#30466d', '#8ba4c8'],
  top_yellow_hoodie: ['#f4c348', '#d69b2f', '#a96f22', '#fff3c2', '#ffdc73'],
  top_green_vest: ['#709a3f', '#527532', '#38522b'],
  bottom_overalls: ['#5672a0', '#3d5680', '#293d61'],
  shoes_sneakers: ['#e6e9e9', '#b7c0c5', '#a33f39', '#d85a50', '#f08b7d'],
  back_backpack: ['#965d31', '#704125', '#51301e'],
};

/** 가림 마스크는 알파 스텐실이므로 단색만 쓴다. */
const MASK_COLOR = '#ffffff';

/** 어떤 에셋에서든 허용되는 색 전체 */
function allowedColors(spriteKey) {
  const shared = Object.values(SHARED);
  if (!spriteKey) return new Set([...shared, MASK_COLOR]);
  return new Set([...shared, MASK_COLOR, ...(ITEM_RAMPS[spriteKey] ?? [])]);
}

/**
 * 대칭을 강제하는 색. (규격 §2.2)
 *
 * 알파(실루엣)는 별도로 항상 검사한다. 색 중에서는 잉크 외곽선과 볼만 강제한다.
 * rim(#dce2e1)과 shade(#eef1f0)는 둘 다 명암 램프의 일부다 — 실측상 rim은
 * y27..34에서는 좌우 쌍이지만 y42..51에서는 방향성 음영으로 쓰여, 대칭을 강제하면
 * 하반신 명암을 다시 그려야 한다. 형태가 아니라 조명이므로 자유롭게 둔다.
 */
const SYMMETRY_ENFORCED_COLORS = new Set([SHARED.ink, SHARED.cheek]);

module.exports = { SHARED, ITEM_RAMPS, MASK_COLOR, allowedColors, SYMMETRY_ENFORCED_COLORS };
