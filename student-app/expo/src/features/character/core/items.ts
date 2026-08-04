/**
 * 상품별 에셋 구성과 호환성 규칙. 웹·Expo가 같은 정의를 쓴다.
 *
 * 여기에 등록된 상품만 화면에 그려진다. 등록되지 않은 카탈로그 상품은
 * `PENDING_ASSET_KEYS`로 모여 개발 환경 경고와 CI 검사에 걸린다.
 * 규격: docs/design/CHARACTER_ASSET_SPEC.md §5.1, §8
 */

import { CATALOG } from './catalog';
import type { ItemLayerPart } from './layerOrder';
import type { EquippedMap, Slot } from './types';
import { CATALOG_BY_ID } from './catalog';

/**
 * 에셋 파일 식별자. 플랫폼별 해석은 각 앱의 assetMap이 담당한다.
 * 웹은 URL로, Expo는 Metro `require()`로 바꾼다.
 */
export type CharacterAssetId = string;

export type CharacterItemAssets = Partial<Record<ItemLayerPart, CharacterAssetId>> & {
  /** 몸·의상에서 지워낼 영역을 담은 알파 스텐실 */
  occlusion?: CharacterAssetId;
};

export interface CharacterItemDefinition {
  assets: CharacterItemAssets;
  /** 시각적으로 덮는 슬롯. 소유·착용 상태는 유지하고 렌더만 생략한다. */
  coversSlots?: Slot[];
  /** 렌더에서 완전히 제외하는 슬롯. */
  hidesSlots?: Slot[];
}

/**
 * 64×64 에셋이 배포된 상품.
 *
 * 규격 §11 3·4단계에서 나머지 상품을 여기에 추가한다. 항목을 추가하면
 * `PENDING_ASSET_KEYS`가 자동으로 줄어들고 상점 노출도 함께 열린다.
 */
export const CHARACTER_ITEMS: Readonly<Record<string, CharacterItemDefinition>> = {
  hat_red_cap: { assets: { main: 'hat_red_cap' } },
  hat_teal_beanie: { assets: { main: 'hat_teal_beanie' } },
  face_sunglasses: { assets: { main: 'face_sunglasses' } },
  face_headphones: {
    assets: { behind: 'face_headphones_back', front: 'face_headphones_front' },
  },
  top_blue_tshirt: {
    assets: { main: 'top_blue_tshirt', occlusion: 'top_blue_tshirt_occlusion' },
  },
  top_yellow_hoodie: { assets: { main: 'top_yellow_hoodie' } },
  top_green_vest: { assets: { main: 'top_green_vest' } },
  bottom_overalls: { assets: { main: 'bottom_overalls' } },
  shoes_sneakers: {
    assets: { main: 'shoes_sneakers', occlusion: 'shoes_sneakers_occlusion' },
  },
  back_backpack: { assets: { main: 'back_backpack' } },
};

/**
 * 에셋 제작이 예정된 상품의 호환성 규칙.
 *
 * 에셋보다 먼저 확정해 둔다. 상품이 `CHARACTER_ITEMS`로 올라올 때 이 값을 합쳐서
 * 쓰므로, 규칙을 나중에 다시 찾아 넣는 일이 없다. 규격 §8 참조.
 */
export const PLANNED_COMPATIBILITY: Readonly<
  Record<string, Pick<CharacterItemDefinition, 'coversSlots' | 'hidesSlots'>>
> = {
  hat_space_helmet: { hidesSlots: ['FACE'] },
  hat_knight: { hidesSlots: ['FACE'] },
  hat_dinosaur: { hidesSlots: ['FACE'] },
  top_wizard_robe: { coversSlots: ['BOTTOM'] },
  top_spacesuit: { coversSlots: ['BOTTOM'] },
  top_fantasy_armor: { coversSlots: ['BOTTOM'] },
  top_dinosaur: { coversSlots: ['BOTTOM'], hidesSlots: ['NECK'] },
};

/** 카탈로그에는 있지만 아직 64×64 에셋이 없는 상품. */
export const PENDING_ASSET_KEYS: ReadonlySet<string> = new Set(
  CATALOG.filter((item) => !CHARACTER_ITEMS[item.spriteKey]).map((item) => item.spriteKey),
);

/**
 * 가림 마스크 렌더링 스위치.
 *
 * 현재 배포된 마스크 2종(`top_blue_tshirt`, `shoes_sneakers`)은 상품이 실제로 덮는
 * 영역보다 넓게 파낸다. 티셔츠의 경우 옷깃 안쪽까지 지워서 목에 구멍이 생긴다.
 * 마스크 아트를 규격 §6.3에 맞게 다시 만든 뒤 켠다(규격 §11 2단계).
 *
 * 그때까지 웹과 Expo 모두 끈 상태로 두어 두 플랫폼의 결과를 동일하게 유지한다.
 * 한쪽만 켜면 같은 상품이 서로 다르게 보인다.
 */
export const OCCLUSION_ENABLED = false;

/** 에셋 식별자 → `character/` 아래 상대 경로. 웹 URL과 CI 검사가 함께 쓴다. */
export const CHARACTER_ASSET_PATHS: Readonly<Record<string, string>> = (() => {
  const paths: Record<string, string> = { base_default: 'base/base_default.png' };
  for (const [spriteKey, definition] of Object.entries(CHARACTER_ITEMS)) {
    const slot = CATALOG_BY_ID[spriteKey]?.slot;
    for (const [part, assetId] of Object.entries(definition.assets)) {
      if (!assetId) continue;
      paths[assetId] = part === 'occlusion'
        ? `masks/${assetId}.png`
        : `items/${(slot ?? 'misc').toLowerCase()}/${assetId}.png`;
    }
  }
  return paths;
})();

/**
 * 에셋 식별자 → 그 에셋이 속한 상품의 spriteKey.
 *
 * 한 상품이 여러 파일로 갈리기 때문에 필요하다(`face_headphones` →
 * `face_headphones_back`, `face_headphones_front`). 팔레트·대칭 예외를 조회할 때
 * 파일 이름이 아니라 상품 기준으로 봐야 한다.
 */
export const SPRITE_KEY_BY_ASSET_ID: Readonly<Record<string, string>> = (() => {
  const owners: Record<string, string> = {};
  for (const [spriteKey, definition] of Object.entries(CHARACTER_ITEMS)) {
    for (const assetId of Object.values(definition.assets)) {
      if (assetId) owners[assetId] = spriteKey;
    }
  }
  return owners;
})();

/** 렌더에 실제로 필요한 에셋 식별자 전체. 마스크는 스위치가 켜졌을 때만 포함한다. */
export function requiredAssetIds(includeOcclusion = OCCLUSION_ENABLED): string[] {
  const ids = new Set<string>(['base_default']);
  for (const definition of Object.values(CHARACTER_ITEMS)) {
    for (const [part, assetId] of Object.entries(definition.assets)) {
      if (!assetId) continue;
      if (part === 'occlusion' && !includeOcclusion) continue;
      ids.add(assetId);
    }
  }
  return [...ids].sort();
}

export function itemDefinitionFor(spriteKey: string | null | undefined) {
  if (!spriteKey) return null;
  const definition = CHARACTER_ITEMS[spriteKey];
  if (!definition) return null;
  const planned = PLANNED_COMPATIBILITY[spriteKey];
  if (!planned) return definition;
  return { ...planned, ...definition };
}

export function hasCharacterAsset(spriteKey: string | null | undefined): boolean {
  return itemDefinitionFor(spriteKey) !== null;
}

/** 착용 중 상품이 시각적으로 덮어 렌더를 생략할 슬롯. */
export function coveredSlots(equipped: EquippedMap): Set<Slot> {
  const covered = new Set<Slot>();
  for (const cosmeticId of Object.values(equipped)) {
    if (!cosmeticId) continue;
    const item = CATALOG_BY_ID[cosmeticId];
    const definition = itemDefinitionFor(item?.spriteKey);
    if (!definition) continue;
    for (const slot of definition.coversSlots ?? []) covered.add(slot);
    for (const slot of definition.hidesSlots ?? []) covered.add(slot);
  }
  return covered;
}

/**
 * 같은 슬롯은 교체하고 다른 슬롯은 유지한다.
 * 전신 의상과 하의처럼 실제로 겹칠 수 없는 조합만 명시적으로 충돌시킨다.
 *
 * `coversSlots`는 에셋 유무와 무관하게 판정해야 한다. 에셋이 아직 없는 전신 의상도
 * 하의와 충돌하는 것은 사실이므로 `PLANNED_COMPATIBILITY`까지 함께 본다.
 */
export function conflictsFor(
  item: { slot: Slot; spriteKey: string },
  equipped: EquippedMap,
): Slot[] {
  const conflicts = new Set<Slot>();
  const rules = { ...PLANNED_COMPATIBILITY[item.spriteKey], ...CHARACTER_ITEMS[item.spriteKey] };

  for (const slot of rules.coversSlots ?? []) conflicts.add(slot);

  // 반대 방향: 이미 착용한 상품이 이 상품의 슬롯을 덮는다면 그 상품을 벗겨야 한다.
  for (const [slot, cosmeticId] of Object.entries(equipped) as Array<[Slot, string | null]>) {
    if (!cosmeticId || slot === item.slot) continue;
    const worn = CATALOG_BY_ID[cosmeticId];
    if (!worn) continue;
    const wornRules = {
      ...PLANNED_COMPATIBILITY[worn.spriteKey],
      ...CHARACTER_ITEMS[worn.spriteKey],
    };
    if ((wornRules.coversSlots ?? []).includes(item.slot)) conflicts.add(slot);
  }

  conflicts.delete(item.slot);
  return [...conflicts];
}
