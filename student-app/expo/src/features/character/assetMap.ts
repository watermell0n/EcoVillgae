/**
 * Expo(Metro) 에셋 해석기.
 *
 * Metro는 정적 `require()` 경로만 번들에 담기 때문에 경로를 문자열로 조립할 수 없다.
 * 그래서 여기만 손으로 나열하고, 누락은 CI가 잡는다
 * (`node scripts/character/check-assets.js`가 core의 requiredAssetIds()와 대조한다).
 *
 * 에셋 파일은 웹 `student-app/public/character/`가 정본이며
 * `node scripts/character/sync-assets.js`로 이 앱의 `assets/character/`에 복제된다.
 * 직접 수정하지 않는다.
 */
import type { ImageSourcePropType } from 'react-native';

const SOURCES: Readonly<Record<string, ImageSourcePropType>> = {
  base_default: require('../../../assets/character/base/base_default.png'),

  hat_red_cap: require('../../../assets/character/items/hat/hat_red_cap.png'),
  hat_teal_beanie: require('../../../assets/character/items/hat/hat_teal_beanie.png'),

  face_sunglasses: require('../../../assets/character/items/face/face_sunglasses.png'),
  face_headphones_back: require('../../../assets/character/items/face/face_headphones_back.png'),
  face_headphones_front: require('../../../assets/character/items/face/face_headphones_front.png'),

  top_blue_tshirt: require('../../../assets/character/items/top/top_blue_tshirt.png'),
  top_yellow_hoodie: require('../../../assets/character/items/top/top_yellow_hoodie.png'),
  top_green_vest: require('../../../assets/character/items/top/top_green_vest.png'),

  bottom_overalls: require('../../../assets/character/items/bottom/bottom_overalls.png'),

  shoes_sneakers: require('../../../assets/character/items/shoes/shoes_sneakers.png'),

  back_backpack: require('../../../assets/character/items/back/back_backpack.png'),

  // 가림 마스크는 상품이 몸 실루엣을 새로 정의해야 할 때만 둔다(규격 §6.3).
  // 현재 10종은 불투명 의상을 위에 덮는 것으로 충분해 마스크가 없다.
};

export const BASE_ASSET = SOURCES.base_default;

export function assetSource(assetId: string): ImageSourcePropType | null {
  return SOURCES[assetId] ?? null;
}

/** CI 검사용. 이 앱이 실제로 번들에 담은 에셋 식별자. */
export const BUNDLED_ASSET_IDS = Object.keys(SOURCES);
