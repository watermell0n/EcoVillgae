/**
 * 착용 상태 → 정렬된 레이어 목록.
 *
 * 웹과 Expo가 **이 함수 하나**로 겹침 순서를 계산한다. 각 앱은 결과의 assetId를
 * 플랫폼 소스로 바꿔 순서대로 그리기만 한다. 그래서 두 플랫폼의 합성이 어긋날 수 없다.
 *
 * 규격: docs/design/CHARACTER_ASSET_SPEC.md §5, §6
 */

import { CATALOG_BY_ID } from './catalog';
import {
  CHARACTER_ITEMS,
  OCCLUSION_ENABLED,
  coveredSlots,
  itemDefinitionFor,
} from './items';
import { BASE_BODY_Z, bandFor, bandZ, type ItemLayerPart } from './layerOrder';
import { SLOT_Z_ORDER, type EquippedMap, type Slot } from './types';

export interface CharacterLayer {
  /** React 리스트 key. 안정적이어야 한다. */
  key: string;
  assetId: string;
  /** 이 레이어 직전에 적용할 가림 마스크. OCCLUSION_ENABLED가 꺼져 있으면 없다. */
  occlusionAssetId?: string;
  z: number;
}

export interface CharacterComposition {
  layers: CharacterLayer[];
  /** 카탈로그에는 있지만 에셋이 없어 건너뛴 상품. 개발 환경 경고에 쓴다. */
  missingSpriteKeys: string[];
  /** 다른 상품이 덮어서 의도적으로 생략한 슬롯. */
  coveredSlots: Slot[];
}

const PART_ORDER: ItemLayerPart[] = ['behind', 'main', 'front'];

function slotIndex(slot: Slot): number {
  const index = SLOT_Z_ORDER.indexOf(slot);
  return index < 0 ? SLOT_Z_ORDER.length : index;
}

export function planCharacterLayers(equipped: EquippedMap): CharacterComposition {
  const covered = coveredSlots(equipped);
  const missingSpriteKeys: string[] = [];
  const entries: Array<CharacterLayer & { sort: [number, number, number] }> = [
    { key: 'base', assetId: 'base_default', z: BASE_BODY_Z, sort: [BASE_BODY_Z, -1, 0] },
  ];

  for (const [slot, cosmeticId] of Object.entries(equipped) as Array<[Slot, string | null]>) {
    if (!cosmeticId) continue;
    if (covered.has(slot)) continue;

    const item = CATALOG_BY_ID[cosmeticId];
    if (!item) continue;

    const definition = itemDefinitionFor(item.spriteKey);
    if (!definition) {
      missingSpriteKeys.push(item.spriteKey);
      continue;
    }

    const occlusion = OCCLUSION_ENABLED ? definition.assets.occlusion : undefined;
    let attachedOcclusion = false;

    for (const [partIndex, part] of PART_ORDER.entries()) {
      const assetId = definition.assets[part];
      if (!assetId) continue;
      const band = bandFor(slot, part);
      const z = bandZ(band);
      // 마스크는 몸체 위에 처음 올라가는 부위 앞에서 한 번만 적용한다.
      const useOcclusion = Boolean(occlusion) && !attachedOcclusion && z > BASE_BODY_Z;
      if (useOcclusion) attachedOcclusion = true;
      entries.push({
        key: `${item.spriteKey}:${part}`,
        assetId,
        occlusionAssetId: useOcclusion ? occlusion : undefined,
        z,
        sort: [z, slotIndex(slot), partIndex],
      });
    }
  }

  entries.sort((a, b) => {
    for (let i = 0; i < 3; i += 1) {
      if (a.sort[i] !== b.sort[i]) return a.sort[i] - b.sort[i];
    }
    return 0;
  });

  return {
    layers: entries.map(({ sort: _sort, ...layer }) => layer),
    missingSpriteKeys,
    coveredSlots: [...covered],
  };
}

/**
 * 개발 환경에서 에셋 누락을 알린다. 조용히 건너뛰면 상품이 사라진 것을 아무도 모른다.
 * 운영 빌드에서는 호출하지 않는다.
 */
export function describeMissingAssets(missingSpriteKeys: string[]): string | null {
  if (!missingSpriteKeys.length) return null;
  const total = Object.keys(CHARACTER_ITEMS).length;
  return (
    `[character] 64x64 에셋이 없어 ${missingSpriteKeys.length}종을 건너뛴다: `
    + `${missingSpriteKeys.join(', ')} (현재 배포 ${total}종)`
  );
}
