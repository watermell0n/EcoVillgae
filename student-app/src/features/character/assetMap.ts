/**
 * 웹(Vite) 에셋 해석기.
 *
 * Vite `public/`은 경로 그대로 URL이 되므로 core가 계산한 상대 경로를 그대로 쓴다.
 * 파일 목록을 손으로 나열하지 않으므로 Expo 쪽과 달리 누락이 발생할 수 없다.
 *
 * 에셋 정본은 `student-app/public/character/`이며,
 * `node scripts/character/sync-assets.js`로 Expo 앱에 복제된다.
 */
import { CHARACTER_ASSET_PATHS } from '@character-core';

/** 에셋을 교체했는데 브라우저가 옛 파일을 들고 있는 것을 막는다. */
const ASSET_VERSION = '20260805';

function assetUrl(relativePath: string): string {
  return `/character/${relativePath}?v=${ASSET_VERSION}`;
}

export const BASE_ASSET = assetUrl(CHARACTER_ASSET_PATHS.base_default);

export function assetSource(assetId: string): string | null {
  const relativePath = CHARACTER_ASSET_PATHS[assetId];
  return relativePath ? assetUrl(relativePath) : null;
}
