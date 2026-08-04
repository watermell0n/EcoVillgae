import { StyleSheet, Text, View, type ImageSourcePropType } from "react-native";

import { CHARACTER_RIG } from "../assetProfile";
import { CATALOG_BY_ID } from "../catalog";
import { BASE_SPRITE, layerProfileFor } from "../layerProfile";
import { SLOT_LABEL } from "../types";
import { PixelStack } from "./PixelStack";

const { logicalWidth, logicalHeight } = CHARACTER_RIG.canvas;

/**
 * 단일 아이템 미리보기. 카탈로그 64종 중 아직 48x56 스프라이트가 배포되지 않은 항목은
 * 그럴듯한 대체 그림 대신 슬롯 이름을 보여준다. 없는 아트를 있는 것처럼 보이면 안 된다.
 */
export function ItemSprite({
  spriteKey,
  scale = 2,
  withBase = false
}: Readonly<{ spriteKey: string; scale?: number; withBase?: boolean }>) {
  const profile = layerProfileFor(spriteKey);
  const width = logicalWidth * scale;
  const height = logicalHeight * scale;

  if (!profile) {
    const slot = CATALOG_BY_ID[spriteKey]?.slot;
    return (
      <View style={[styles.pending, { width, height }]}>
        <Text style={styles.pendingLabel}>{slot ? SLOT_LABEL[slot] : "아이템"}</Text>
        <Text style={styles.pendingHint}>도트 준비 중</Text>
      </View>
    );
  }

  const sources: ImageSourcePropType[] = [];
  if (profile.backSprite) sources.push(profile.backSprite);
  if (withBase) sources.push(BASE_SPRITE);
  sources.push(profile.sprite);

  return <PixelStack sources={sources} width={width} height={height} />;
}

const styles = StyleSheet.create({
  pending: {
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 10,
    borderWidth: 1,
    borderStyle: "dashed",
    borderColor: "#CFE8EF",
    backgroundColor: "#F0F7FF",
    gap: 2
  },
  pendingLabel: { color: "#6F8198", fontSize: 12, fontWeight: "800" },
  pendingHint: { color: "#6F8198", fontSize: 10 }
});
