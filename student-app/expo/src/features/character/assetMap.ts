/**
 * Expo(Metro) 에셋 해석기.
 *
 * Metro는 정적 `require()` 경로만 번들에 담기 때문에 경로를 문자열로 조립할 수 없다.
 * 그래서 여기만 손으로 나열하고, 누락은 CI가 잡는다
 * (`node scripts/character/check-assets.js`가 core의 requiredAssetIds()와 대조한다).
 *
 * 에셋 파일은 `student-app/public/character/`가 정본이며
 * `node scripts/character/sync-assets.js`로 이 앱의 `assets/character/`에 복제된다.
 * 직접 수정하지 않는다.
 */
import type { ImageSourcePropType } from 'react-native';

const SOURCES: Readonly<Record<string, ImageSourcePropType>> = {
  base_default: require('../../../assets/character/base/base_default.png'),

  // 모자 19종
  hat_red_cap: require('../../../assets/character/items/hat/hat_red_cap.png'),
  hat_teal_beanie: require('../../../assets/character/items/hat/hat_teal_beanie.png'),
  hat_straw: require('../../../assets/character/items/hat/hat_straw.png'),
  hat_beach: require('../../../assets/character/items/hat/hat_beach.png'),
  hat_pom_beanie: require('../../../assets/character/items/hat/hat_pom_beanie.png'),
  hat_baseball_blue: require('../../../assets/character/items/hat/hat_baseball_blue.png'),
  hat_baseball_red: require('../../../assets/character/items/hat/hat_baseball_red.png'),
  hat_baseball_navy: require('../../../assets/character/items/hat/hat_baseball_navy.png'),
  hat_baseball_green: require('../../../assets/character/items/hat/hat_baseball_green.png'),
  hat_baseball_yellow: require('../../../assets/character/items/hat/hat_baseball_yellow.png'),
  hat_chef: require('../../../assets/character/items/hat/hat_chef.png'),
  hat_safari: require('../../../assets/character/items/hat/hat_safari.png'),
  hat_space_helmet: require('../../../assets/character/items/hat/hat_space_helmet.png'),
  hat_safety_police: require('../../../assets/character/items/hat/hat_safety_police.png'),
  hat_pilot: require('../../../assets/character/items/hat/hat_pilot.png'),
  hat_crown: require('../../../assets/character/items/hat/hat_crown.png'),
  hat_wizard: require('../../../assets/character/items/hat/hat_wizard.png'),
  hat_knight: require('../../../assets/character/items/hat/hat_knight.png'),
  hat_dinosaur: require('../../../assets/character/items/hat/hat_dinosaur.png'),

  // 얼굴 4종
  face_sunglasses: require('../../../assets/character/items/face/face_sunglasses.png'),
  face_headphones_back: require('../../../assets/character/items/face/face_headphones_back.png'),
  face_headphones_front: require('../../../assets/character/items/face/face_headphones_front.png'),
  face_goggles: require('../../../assets/character/items/face/face_goggles.png'),
  face_ski_goggles: require('../../../assets/character/items/face/face_ski_goggles.png'),

  // 목 장식 2종
  neck_scarf: require('../../../assets/character/items/neck/neck_scarf.png'),
  neck_knit_scarf: require('../../../assets/character/items/neck/neck_knit_scarf.png'),

  // 상의 18종
  top_blue_tshirt: require('../../../assets/character/items/top/top_blue_tshirt.png'),
  top_yellow_hoodie: require('../../../assets/character/items/top/top_yellow_hoodie.png'),
  top_green_vest: require('../../../assets/character/items/top/top_green_vest.png'),
  top_swimsuit: require('../../../assets/character/items/top/top_swimsuit.png'),
  top_padding: require('../../../assets/character/items/top/top_padding.png'),
  top_soccer: require('../../../assets/character/items/top/top_soccer.png'),
  top_baseball_pinstripe: require('../../../assets/character/items/top/top_baseball_pinstripe.png'),
  top_baseball_red: require('../../../assets/character/items/top/top_baseball_red.png'),
  top_baseball_blue: require('../../../assets/character/items/top/top_baseball_blue.png'),
  top_baseball_navy: require('../../../assets/character/items/top/top_baseball_navy.png'),
  top_baseball_gray: require('../../../assets/character/items/top/top_baseball_gray.png'),
  top_spacesuit: require('../../../assets/character/items/top/top_spacesuit.png'),
  top_safety_police: require('../../../assets/character/items/top/top_safety_police.png'),
  top_legal_professional: require('../../../assets/character/items/top/top_legal_professional.png'),
  top_pilot: require('../../../assets/character/items/top/top_pilot.png'),
  top_wizard_robe: require('../../../assets/character/items/top/top_wizard_robe.png'),
  top_fantasy_armor: require('../../../assets/character/items/top/top_fantasy_armor.png'),
  top_dinosaur: require('../../../assets/character/items/top/top_dinosaur.png'),

  // 신발 6종
  shoes_sneakers: require('../../../assets/character/items/shoes/shoes_sneakers.png'),
  shoes_red_boots: require('../../../assets/character/items/shoes/shoes_red_boots.png'),
  shoes_sandals: require('../../../assets/character/items/shoes/shoes_sandals.png'),
  shoes_aqua: require('../../../assets/character/items/shoes/shoes_aqua.png'),
  shoes_snow_boots: require('../../../assets/character/items/shoes/shoes_snow_boots.png'),
  shoes_space_boots: require('../../../assets/character/items/shoes/shoes_space_boots.png'),

  // 등 장식 4종
  back_backpack: require('../../../assets/character/items/back/back_backpack.png'),
  back_briefcase: require('../../../assets/character/items/back/back_briefcase.png'),
  back_fairy_wings: require('../../../assets/character/items/back/back_fairy_wings.png'),
  back_dinosaur_tail: require('../../../assets/character/items/back/back_dinosaur_tail.png'),

  // 손 소품 3종
  hand_baseball_glove: require('../../../assets/character/items/hand/hand_baseball_glove.png'),
  hand_magic_wand: require('../../../assets/character/items/hand/hand_magic_wand.png'),
  hand_toy_sword: require('../../../assets/character/items/hand/hand_toy_sword.png'),

  // 효과 2종 (앞·뒤 분리)
  effect_swim_tube_behind: require('../../../assets/character/items/effect/effect_swim_tube_behind.png'),
  effect_swim_tube_front: require('../../../assets/character/items/effect/effect_swim_tube_front.png'),
  effect_fluffy_cloud_behind: require('../../../assets/character/items/effect/effect_fluffy_cloud_behind.png'),
  effect_fluffy_cloud_front: require('../../../assets/character/items/effect/effect_fluffy_cloud_front.png'),

  // 배경 5종
  bg_spring: require('../../../assets/character/items/background/bg_spring.png'),
  bg_summer_sea: require('../../../assets/character/items/background/bg_summer_sea.png'),
  bg_autumn: require('../../../assets/character/items/background/bg_autumn.png'),
  bg_winter: require('../../../assets/character/items/background/bg_winter.png'),
  bg_space: require('../../../assets/character/items/background/bg_space.png'),

  // 가림 마스크는 상품이 몸 실루엣을 새로 정의해야 할 때만 둔다(규격 §6.3).
  // 현재 64종은 불투명 의상을 위에 덮는 것으로 충분해 마스크가 없다.
};

export const BASE_ASSET = SOURCES.base_default;

export function assetSource(assetId: string): ImageSourcePropType | null {
  return SOURCES[assetId] ?? null;
}

/** CI 검사용. 이 앱이 실제로 번들에 담은 에셋 식별자. */
export const BUNDLED_ASSET_IDS = Object.keys(SOURCES);
