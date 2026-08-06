import { Image, StyleSheet, View, type ImageSourcePropType } from "react-native";

import { useCrispPixelId } from "../crispPixels";

export interface PixelStackProps {
  /** 아래에서 위로 겹칠 전체 캔버스 레이어들. */
  sources: ImageSourcePropType[];
  width: number;
  height: number;
}

/**
 * 웹 구현. RN Image를 겹치고 image-rendering: pixelated로 nearest-neighbor를 얻는다.
 * 네이티브는 PixelStack.native.tsx가 Skia로 같은 결과를 만든다.
 */
export function PixelStack({ sources, width, height }: Readonly<PixelStackProps>) {
  const crispId = useCrispPixelId();
  const size = { width, height };

  return (
    <View nativeID={crispId} style={size}>
      {sources.map((source, index) => (
        <Image
          key={index}
          source={source}
          style={[styles.layer, size]}
          resizeMode="contain"
          fadeDuration={0}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  layer: { position: "absolute", left: 0, top: 0 }
});
