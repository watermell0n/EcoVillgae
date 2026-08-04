import { StyleSheet, Text, View, type ImageSourcePropType } from "react-native";

import { assetSource } from "../assetMap";
import {
  CHARACTER_SCALE,
  EMPTY_EQUIPPED,
  SLOT_LABEL,
  CATALOG_BY_ID,
  characterDisplaySize,
  hasCharacterAsset,
  planCharacterLayers
} from "../core";
import { PixelStack } from "./PixelStack";

/**
 * 단일 상품 미리보기. 상품은 언제나 몸에 착용한 모습으로 보여준다.
 * 코드형 렌더러를 쓰지 않으므로 잘라낸 단독 스프라이트가 존재하지 않고,
 * 꼬리·날개처럼 몸이 없으면 형태를 알 수 없는 상품도 있다(규격 §2.5 검토 결과).
 *
 * 아직 64×64 에셋이 없는 상품은 그럴듯한 대체 그림 대신 슬롯 이름을 보여준다.
 * 없는 아트를 있는 것처럼 보이면 안 된다.
 */
export function ItemSprite({
  spriteKey,
  scale = CHARACTER_SCALE.thumbnail,
  withBase = true
}: Readonly<{ spriteKey: string; scale?: number; withBase?: boolean }>) {
  const { width, height } = characterDisplaySize(scale);

  if (!hasCharacterAsset(spriteKey)) {
    const slot = CATALOG_BY_ID[spriteKey]?.slot;
    return (
      <View style={[styles.pending, { width, height }]}>
        <Text style={styles.pendingLabel}>{slot ? SLOT_LABEL[slot] : "아이템"}</Text>
        <Text style={styles.pendingHint}>도트 준비 중</Text>
      </View>
    );
  }

  const item = CATALOG_BY_ID[spriteKey];
  const { layers } = planCharacterLayers({ ...EMPTY_EQUIPPED, [item.slot]: item.id });
  const sources = layers
    .filter((layer) => withBase || layer.assetId !== "base_default")
    .map((layer) => assetSource(layer.assetId))
    .filter((source): source is ImageSourcePropType => source !== null);

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
