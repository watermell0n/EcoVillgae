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

/**
 * 상품별 3단 명암 램프. 상품을 추가하면 여기에 등록한다.
 *
 * 순서는 [base, mid, dark, ...포인트]이며 generate-items.js가 이 순서로 꺼내 쓴다.
 * 여기 없는 색을 쓰면 check-assets.js가 미등록 색으로 잡는다.
 * 배경(bg_*)은 팔레트 검사에서 경고만 나지만, 출력을 깨끗하게 유지하려고 함께 등록한다.
 */
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

  // ── 규격 §11 3·4단계에서 추가한 54종 ──────────────────────────

  // 모자
  hat_baseball_blue: ['#397fc4', '#2a5f96', '#1d4269'],
  hat_baseball_red: ['#d94b45', '#a93b38', '#73302f'],
  hat_baseball_navy: ['#2b4472', '#1d2f52', '#131f38'],
  hat_baseball_green: ['#4a8149', '#366135', '#244123'],
  hat_baseball_yellow: ['#e7b83f', '#bd9128', '#8a6a1c'],
  hat_straw: ['#e0c07a', '#b8934f', '#8a6b34', '#b84234'],
  hat_beach: ['#f7ecc8', '#d9c69a', '#ab9a72', '#3f84ba'],
  hat_pom_beanie: ['#4f7849', '#3a5a36', '#274023'],
  hat_chef: ['#f7f5ee', '#dcdfd7', '#b3b8ae'],
  hat_safari: ['#c9a064', '#a07a45', '#71542d', '#6f4a2c'],
  hat_space_helmet: ['#eef3f5', '#c0cbd3', '#8f9ea9', '#4389b5'],
  hat_safety_police: ['#21406f', '#16294a', '#0e1a30', '#e4b83e'],
  hat_pilot: ['#f2f2ed', '#cdd2d4', '#9aa1a5', '#1b232e', '#e4b83e'],
  hat_crown: ['#f0c04a', '#c99a2f', '#96701f', '#d94b45'],
  hat_wizard: ['#6541a0', '#4a2c7d', '#331d59', '#f0c04a'],
  hat_knight: ['#aebac3', '#7f8d98', '#56626d', '#dbe3e7'],
  hat_dinosaur: ['#54ad55', '#3d8440', '#2a5c2d', '#8fd16e'],

  // 얼굴
  face_goggles: ['#3d9bd3', '#2a76a6', '#1c5478', '#bce8f4'],
  face_ski_goggles: ['#ef9a24', '#c47816', '#8f550d', '#ffd18a'],

  // 목 장식
  neck_scarf: ['#c0392f', '#93281f', '#661a14', '#e07a68'],
  neck_knit_scarf: ['#4e7848', '#385733', '#253b22', '#7fa16f'],

  // 상의
  top_swimsuit: ['#2272b9', '#17548f', '#0f3a66'],
  top_padding: ['#d84438', '#a5302a', '#71211d', '#f0836f'],
  top_soccer: ['#f28b27', '#c66a17', '#91490d'],
  top_baseball_pinstripe: ['#f4f2e8', '#c9ccc4', '#9a9e98', '#7f8a96'],
  top_baseball_red: ['#d94b45', '#a93b38', '#73302f'],
  top_baseball_blue: ['#357fcb', '#24569d', '#17386b'],
  top_baseball_navy: ['#2b4472', '#1d2f52', '#131f38'],
  top_baseball_gray: ['#cbd0d3', '#9aa3a9', '#6c757c', '#2b4472'],
  top_spacesuit: ['#eef3f5', '#c0cbd3', '#8f9ea9', '#4389b5', '#d94b45'],
  top_safety_police: ['#21406f', '#16294a', '#0e1a30', '#e4b83e'],
  top_legal_professional: ['#2f353d', '#1f242a', '#14181c', '#a93b38'],
  top_pilot: ['#f2f2ed', '#cdd2d4', '#9aa1a5', '#1b232e', '#e4b83e'],
  top_wizard_robe: ['#6541a0', '#4a2c7d', '#331d59', '#f0c04a'],
  top_fantasy_armor: ['#aebac3', '#7f8d98', '#56626d', '#e4b83e', '#7b563b'],
  top_dinosaur: ['#54ad55', '#3d8440', '#2a5c2d', '#a9d66f', '#dbe96e'],

  // 신발
  shoes_red_boots: ['#d44436', '#a5302a', '#71211d', '#ef7460'],
  shoes_sandals: ['#8b5e38', '#6a4527', '#4a301a'],
  shoes_aqua: ['#42b8cb', '#2f8b9b', '#1f6070', '#8de0e9'],
  shoes_snow_boots: ['#dce4e8', '#aab6be', '#78848c'],
  shoes_space_boots: ['#edf1f3', '#bcc7ce', '#8a97a0', '#4389b5'],

  // 등 장식
  back_briefcase: ['#8c552d', '#6a3f21', '#4a2b16', '#e4b83e'],
  back_fairy_wings: ['#b7dff2', '#86bcdc', '#5a93bd', '#eaf8ff'],
  back_dinosaur_tail: ['#54ad55', '#3d8440', '#2a5c2d', '#dbe96e'],

  // 손 소품
  hand_baseball_glove: ['#9b5a2f', '#75421f', '#512c14', '#c17a38'],
  hand_magic_wand: ['#7d522d', '#5c3a1e', '#3f2713', '#f0c04a', '#fbe08a'],
  hand_toy_sword: ['#d7e1e6', '#a3b0b8', '#76838b', '#e4b83e', '#7d522d'],

  // 효과
  effect_swim_tube: ['#ef9a35', '#c2751c', '#8f5410'],
  effect_fluffy_cloud: ['#cfe6f2', '#a8c9dd', '#7ea6c1'],

  // 배경 — 풍경이므로 3단 램프 규칙 대신 장면 색을 순서대로 나열한다.
  bg_spring: [
    '#9fdcf0', '#8ad272', '#6cb85a', '#72513d', '#543b2c',
    '#f6a9c2', '#ffd3e0', '#fff5f8',
  ],
  bg_summer_sea: ['#8fd5ef', '#3db8d1', '#2a94ad', '#eafaff', '#f8f4dc', '#e6dcb8'],
  bg_autumn: ['#f4d7a2', '#d58a46', '#b06d33', '#70462f', '#c94f3a', '#e87932', '#d99a2b'],
  bg_winter: ['#9fc9e8', '#eef7fb', '#cfe3ee', '#315b57', '#4f8c72', '#f7fbfd'],
  bg_space: ['#111a3d', '#26235c', '#3a3480', '#634a91', '#f7d86d', '#f4f7ff'],
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
