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
 * 64×64 에셋이 배포된 상품. 카탈로그 64종 전체가 여기에 있어야 한다.
 *
 * 여기 없는 상품은 `PENDING_ASSET_KEYS`로 모여 개발 환경 경고와 CI 검사에 걸리고,
 * 상점에서는 구매·착용이 막힌다(가이드라인 §12.3 임시 안전장치).
 *
 * 에셋은 `node scripts/character/generate-items.js`가 만든다. 좌표를 고칠 때는
 * PNG를 직접 손대지 말고 그 스크립트를 고친 뒤 다시 생성한다.
 */
export const CHARACTER_ITEMS: Readonly<Record<string, CharacterItemDefinition>> = {
  // ── 모자 19종 ──────────────────────────────────────────────
  hat_red_cap: { assets: { main: 'hat_red_cap' } },
  hat_teal_beanie: { assets: { main: 'hat_teal_beanie' } },
  hat_straw: { assets: { main: 'hat_straw' } },
  hat_beach: { assets: { main: 'hat_beach' } },
  hat_pom_beanie: { assets: { main: 'hat_pom_beanie' } },
  hat_baseball_blue: { assets: { main: 'hat_baseball_blue' } },
  hat_baseball_red: { assets: { main: 'hat_baseball_red' } },
  hat_baseball_navy: { assets: { main: 'hat_baseball_navy' } },
  hat_baseball_green: { assets: { main: 'hat_baseball_green' } },
  hat_baseball_yellow: { assets: { main: 'hat_baseball_yellow' } },
  hat_chef: { assets: { main: 'hat_chef' } },
  hat_safari: { assets: { main: 'hat_safari' } },
  hat_safety_police: { assets: { main: 'hat_safety_police' } },
  hat_pilot: { assets: { main: 'hat_pilot' } },
  hat_crown: { assets: { main: 'hat_crown' } },
  hat_wizard: { assets: { main: 'hat_wizard' } },
  /**
   * 얼굴을 감싸는 모자 3종은 `hidesSlots: ['FACE']`로 얼굴 장식을 렌더에서 뺀다.
   * 규격 §9.8 — 투구 위로 고글이 돌출하던 문제의 근본 처리다. 소유·착용 상태는 유지한다.
   *
   * 우주 헬멧과 공룡 후드는 바이저·얼굴 창을 투명하게 파냈다. BASE의 눈·볼이 그대로
   * 보이므로 캐릭터가 사라지지 않는다. 기사 투구는 바이저가 닫힌 디자인이다.
   */
  hat_space_helmet: { assets: { main: 'hat_space_helmet' }, hidesSlots: ['FACE'] },
  hat_knight: { assets: { main: 'hat_knight' }, hidesSlots: ['FACE'] },
  hat_dinosaur: { assets: { main: 'hat_dinosaur' }, hidesSlots: ['FACE'] },

  // ── 얼굴 4종 ───────────────────────────────────────────────
  face_sunglasses: { assets: { main: 'face_sunglasses' } },
  face_headphones: {
    assets: { behind: 'face_headphones_back', front: 'face_headphones_front' },
  },
  face_goggles: { assets: { main: 'face_goggles' } },
  face_ski_goggles: { assets: { main: 'face_ski_goggles' } },

  // ── 목 장식 2종 ────────────────────────────────────────────
  neck_scarf: { assets: { main: 'neck_scarf' } },
  neck_knit_scarf: { assets: { main: 'neck_knit_scarf' } },

  // ── 상의 18종 ──────────────────────────────────────────────
  top_blue_tshirt: { assets: { main: 'top_blue_tshirt' } },
  top_yellow_hoodie: { assets: { main: 'top_yellow_hoodie' } },
  top_green_vest: { assets: { main: 'top_green_vest' } },
  top_swimsuit: { assets: { main: 'top_swimsuit' } },
  top_padding: { assets: { main: 'top_padding' } },
  top_soccer: { assets: { main: 'top_soccer' } },
  top_baseball_pinstripe: { assets: { main: 'top_baseball_pinstripe' } },
  top_baseball_red: { assets: { main: 'top_baseball_red' } },
  top_baseball_blue: { assets: { main: 'top_baseball_blue' } },
  top_baseball_navy: { assets: { main: 'top_baseball_navy' } },
  top_baseball_gray: { assets: { main: 'top_baseball_gray' } },
  top_safety_police: { assets: { main: 'top_safety_police' } },
  top_legal_professional: { assets: { main: 'top_legal_professional' } },
  top_pilot: { assets: { main: 'top_pilot' } },
  // 전신 의상은 하의를 시각적으로 덮는다(규격 §8). 소유·착용은 유지하고 렌더만 생략한다.
  top_spacesuit: { assets: { main: 'top_spacesuit' }, coversSlots: ['BOTTOM'] },
  top_wizard_robe: { assets: { main: 'top_wizard_robe' }, coversSlots: ['BOTTOM'] },
  top_fantasy_armor: { assets: { main: 'top_fantasy_armor' }, coversSlots: ['BOTTOM'] },
  top_dinosaur: {
    assets: { main: 'top_dinosaur' },
    coversSlots: ['BOTTOM'],
    hidesSlots: ['NECK'],
  },

  // ── 하의 1종 ───────────────────────────────────────────────
  bottom_overalls: { assets: { main: 'bottom_overalls' } },

  // ── 신발 6종 ───────────────────────────────────────────────
  shoes_sneakers: { assets: { main: 'shoes_sneakers' } },
  shoes_red_boots: { assets: { main: 'shoes_red_boots' } },
  shoes_sandals: { assets: { main: 'shoes_sandals' } },
  shoes_aqua: { assets: { main: 'shoes_aqua' } },
  shoes_snow_boots: { assets: { main: 'shoes_snow_boots' } },
  shoes_space_boots: { assets: { main: 'shoes_space_boots' } },

  // ── 등 장식 4종 ────────────────────────────────────────────
  /**
   * 배포된 스프라이트는 가방 본체가 아니라 **어깨끈만** 그려져 있다. BACK 슬롯의
   * 기본 위치(BASE보다 뒤)에 놓으면 276px 중 254px(92%)이 몸에 가려 갈색 조각만
   * 보였다. 어깨끈은 몸 앞으로 지나가는 부위이므로 front에 둔다(규격 §5.1, §9.1).
   *
   * 가방 본체를 추가하려면 behind에 별도 파일을 넣는다. 팔이 없는 정면 캐릭터에서는
   * 어깨끈만으로도 배낭으로 읽히므로 지금은 두지 않는다.
   */
  back_backpack: { assets: { front: 'back_backpack' } },
  /** 서류 가방은 몸 옆에 드는 물건이라 front다. behind에 두면 몸에 다 가린다. */
  back_briefcase: { assets: { front: 'back_briefcase' } },
  /** 날개·꼬리는 몸 뒤에서 좌우로 뻗는다(규격 §9.2, §9.3). */
  back_fairy_wings: { assets: { behind: 'back_fairy_wings' } },
  back_dinosaur_tail: { assets: { behind: 'back_dinosaur_tail' } },

  // ── 손 소품 3종 ────────────────────────────────────────────
  hand_baseball_glove: { assets: { front: 'hand_baseball_glove' } },
  hand_magic_wand: { assets: { front: 'hand_magic_wand' } },
  hand_toy_sword: { assets: { front: 'hand_toy_sword' } },

  // ── 효과 2종 ───────────────────────────────────────────────
  // 캐릭터가 링·구름 안에 서 있어야 하므로 앞뒤로 나눈다(규격 §5.1).
  effect_swim_tube: {
    assets: { behind: 'effect_swim_tube_behind', front: 'effect_swim_tube_front' },
  },
  effect_fluffy_cloud: {
    assets: { behind: 'effect_fluffy_cloud_behind', front: 'effect_fluffy_cloud_front' },
  },

  // ── 배경 5종 ───────────────────────────────────────────────
  bg_spring: { assets: { main: 'bg_spring' } },
  bg_summer_sea: { assets: { main: 'bg_summer_sea' } },
  bg_autumn: { assets: { main: 'bg_autumn' } },
  bg_winter: { assets: { main: 'bg_winter' } },
  bg_space: { assets: { main: 'bg_space' } },
};

/** 카탈로그에는 있지만 아직 64×64 에셋이 없는 상품. */
export const PENDING_ASSET_KEYS: ReadonlySet<string> = new Set(
  CATALOG.filter((item) => !CHARACTER_ITEMS[item.spriteKey]).map((item) => item.spriteKey),
);

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

/** 렌더에 실제로 필요한 에셋 식별자 전체. */
export function requiredAssetIds(): string[] {
  const ids = new Set<string>(['base_default']);
  for (const definition of Object.values(CHARACTER_ITEMS)) {
    for (const assetId of Object.values(definition.assets)) {
      if (assetId) ids.add(assetId);
    }
  }
  return [...ids].sort();
}

export function itemDefinitionFor(spriteKey: string | null | undefined) {
  if (!spriteKey) return null;
  return CHARACTER_ITEMS[spriteKey] ?? null;
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
 */
export function conflictsFor(
  item: { slot: Slot; spriteKey: string },
  equipped: EquippedMap,
): Slot[] {
  const conflicts = new Set<Slot>();

  for (const slot of CHARACTER_ITEMS[item.spriteKey]?.coversSlots ?? []) conflicts.add(slot);

  // 반대 방향: 이미 착용한 상품이 이 상품의 슬롯을 덮는다면 그 상품을 벗겨야 한다.
  for (const [slot, cosmeticId] of Object.entries(equipped) as Array<[Slot, string | null]>) {
    if (!cosmeticId || slot === item.slot) continue;
    const worn = CATALOG_BY_ID[cosmeticId];
    if (!worn) continue;
    if ((CHARACTER_ITEMS[worn.spriteKey]?.coversSlots ?? []).includes(item.slot)) {
      conflicts.add(slot);
    }
  }

  conflicts.delete(item.slot);
  return [...conflicts];
}
