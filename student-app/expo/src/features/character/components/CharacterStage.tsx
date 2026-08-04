import { Image, StyleSheet, View, type ImageSourcePropType } from "react-native";

import { CHARACTER_RIG, RIG_LAYER_ORDER, assetProfileFor, type RigSlot } from "../assetProfile";
import { CATALOG_BY_ID } from "../catalog";
import { BASE_SPRITE, layerProfileFor } from "../layerProfile";
import type { CosmeticDTO, EquippedMap } from "../types";

const { logicalWidth, logicalHeight } = CHARACTER_RIG.canvas;
const BASE_Z = RIG_LAYER_ORDER.indexOf("BASE");
/** 베이스 앞 레이어는 리그 순서를 유지한 채 항상 몸체 위로 올린다. */
const FRONT_BAND = RIG_LAYER_ORDER.length;

export interface StageLayer {
  key: string;
  source: ImageSourcePropType;
  z: number;
}

function rigZ(rigSlot: RigSlot) {
  return RIG_LAYER_ORDER.indexOf(rigSlot);
}

/**
 * 장착 목록을 리그 순서대로 정렬한 레이어 목록으로 바꾼다.
 * 스프라이트가 아직 없는 카탈로그 항목은 조용히 건너뛴다.
 */
export function buildStageLayers(equipped: EquippedMap): StageLayer[] {
  const layers: StageLayer[] = [{ key: "base", source: BASE_SPRITE, z: BASE_Z }];

  for (const cosmeticId of Object.values(equipped)) {
    if (!cosmeticId) continue;
    const item: CosmeticDTO | undefined = CATALOG_BY_ID[cosmeticId];
    if (!item) continue;
    const profile = layerProfileFor(item.spriteKey);
    if (!profile) continue;

    const slotZ = rigZ(assetProfileFor(item).rigSlot);
    if (profile.backSprite) {
      layers.push({ key: `${item.spriteKey}:back`, source: profile.backSprite, z: BASE_Z - 1 });
    }
    layers.push({
      key: item.spriteKey,
      source: profile.sprite,
      z: profile.placement === "BEHIND_BASE" ? slotZ : FRONT_BAND + slotZ,
    });
  }

  return layers.sort((a, b) => a.z - b.z);
}

export function CharacterStage({
  equipped,
  scale = 4
}: Readonly<{ equipped: EquippedMap; scale?: number }>) {
  const width = logicalWidth * scale;
  const height = logicalHeight * scale;
  const layers = buildStageLayers(equipped);

  return (
    <View style={[styles.stage, { width, height }]}>
      {layers.map((layer) => (
        <Image
          key={layer.key}
          source={layer.source}
          style={[styles.layer, { width, height }]}
          resizeMode="contain"
          fadeDuration={0}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  stage: { position: "relative" },
  layer: { position: "absolute", left: 0, top: 0 }
});
