import { useEffect } from "react";
import { StyleSheet, View, type ImageSourcePropType } from "react-native";

import { assetSource } from "../assetMap";
import {
  CHARACTER_CANVAS_HEIGHT,
  CHARACTER_CANVAS_WIDTH,
  CHARACTER_PALETTE,
  CHARACTER_SCALE,
  characterDisplaySize,
  describeMissingAssets,
  describeUnresolvedAsset,
  planCharacterLayers,
  reportMissingCharacterAsset,
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

  /**
   * 이전에는 `.filter(source => source !== null)`로 해석 못 한 레이어를 조용히 버렸다.
   * 그러면 상품을 착용해도 아무 변화가 없는 것처럼 보이고 원인을 추적할 단서도 없다.
   * 해석 실패를 따로 모아 경고하고, BASE가 빠졌을 때는 폴백 실루엣을 깐다
   * (가이드라인 §4.3).
   */
  const resolved = layers.map((layer) => ({ layer, source: assetSource(layer.assetId) }));
  const unresolved = resolved.filter((entry) => entry.source === null);
  const sources = resolved
    .map((entry) => entry.source)
    .filter((source): source is ImageSourcePropType => source !== null);
  const baseMissing = unresolved.some((entry) => entry.layer.assetId === "base_default");

  useEffect(() => {
    if (!__DEV__) return;
    const message = describeMissingAssets(missingSpriteKeys);
    if (message) console.warn(message);
  }, [missingSpriteKeys.join(",")]);

  useEffect(() => {
    for (const { layer } of unresolved) {
      if (reportMissingCharacterAsset(layer.assetId)) {
        console.warn(describeUnresolvedAsset(layer.assetId, layer.spriteKey));
      }
    }
  }, [unresolved.map((entry) => entry.layer.assetId).join(",")]);

  return (
    <View style={[styles.stage, { width, height }]}>
      {baseMissing ? <View style={[styles.fallback, { width, height }]} /> : null}
      <PixelStack sources={sources} width={width} height={height} />
      {__DEV__ && unresolved.length > 0 ? (
        <View style={[styles.unresolvedMark, { width, height }]} pointerEvents="none" />
      ) : null}
    </View>
  );
}

export { CHARACTER_CANVAS_HEIGHT, CHARACTER_CANVAS_WIDTH };

const styles = StyleSheet.create({
  stage: { position: "relative" },
  /** BASE PNG를 못 읽었을 때. 빈 화면 대신 몸 자리를 보여준다. */
  fallback: {
    position: "absolute",
    left: 0,
    top: 0,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: CHARACTER_PALETTE.ink,
    backgroundColor: CHARACTER_PALETTE.rim
  },
  /** 개발 빌드에서만 보이는 표시. 렌더가 조용히 비어 보이는 상황을 눈에 띄게 한다. */
  unresolvedMark: {
    position: "absolute",
    left: 0,
    top: 0,
    borderWidth: 2,
    borderStyle: "dashed",
    borderColor: "#e0574a"
  }
});
