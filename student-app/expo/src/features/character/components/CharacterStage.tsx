import { useEffect } from "react";
import { StyleSheet, View, type ImageSourcePropType } from "react-native";

import { assetSource } from "../assetMap";
import {
  CHARACTER_CANVAS_HEIGHT,
  CHARACTER_CANVAS_WIDTH,
  CHARACTER_SCALE,
  characterDisplaySize,
  describeMissingAssets,
  planCharacterLayers,
  type EquippedMap
} from "../core";
import { PixelStack } from "./PixelStack";

/**
 * 착용 미리보기. 레이어 순서는 core의 planCharacterLayers가 정하므로 웹과 결과가 같다.
 * 표시 크기는 64의 정수 배율만 쓴다.
 */
export function CharacterStage({
  equipped,
  scale = CHARACTER_SCALE.stage
}: Readonly<{ equipped: EquippedMap; scale?: number }>) {
  const { layers, missingSpriteKeys } = planCharacterLayers(equipped);
  const { width, height } = characterDisplaySize(scale);

  useEffect(() => {
    if (!__DEV__) return;
    const message = describeMissingAssets(missingSpriteKeys);
    if (message) console.warn(message);
  }, [missingSpriteKeys.join(",")]);

  const sources = layers
    .map((layer) => assetSource(layer.assetId))
    .filter((source): source is ImageSourcePropType => source !== null);

  return (
    <View style={[styles.stage, { width, height }]}>
      <PixelStack sources={sources} width={width} height={height} />
    </View>
  );
}

export { CHARACTER_CANVAS_HEIGHT, CHARACTER_CANVAS_WIDTH };

const styles = StyleSheet.create({
  stage: { position: "relative" }
});
