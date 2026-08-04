import {
  Canvas,
  FilterMode,
  Image as SkiaImage,
  MipmapMode,
  useImage
} from "@shopify/react-native-skia";
import type { ImageSourcePropType } from "react-native";

import type { PixelStackProps } from "./PixelStack";

/**
 * 네이티브 구현. RN Image에는 nearest-neighbor 옵션이 없어서 48x56 스프라이트를
 * 확대하면 도트가 뭉개진다. Skia로 그리면서 sampling을 Nearest로 고정한다.
 *
 * 이 태블릿은 밀도 320dpi(DPR 2)라 240x280 논리 크기가 물리적으로 480x560,
 * 즉 원본의 10배 확대다. 보간을 끄지 않으면 확대가 그대로 흐림으로 보인다.
 */
const NEAREST = { filter: FilterMode.Nearest, mipmap: MipmapMode.None } as const;

/**
 * 레이어마다 별도 컴포넌트로 감싼다. useImage는 훅이라 개수가 바뀌는 배열 안에서
 * 직접 호출하면 훅 순서가 깨지지만, 컴포넌트 경계를 두면 각 인스턴스가 훅 하나만
 * 호출하므로 레이어 수가 달라져도 안전하다.
 */
function PixelLayer({
  source,
  width,
  height
}: Readonly<{ source: ImageSourcePropType; width: number; height: number }>) {
  const image = useImage(source as Parameters<typeof useImage>[0]);
  if (!image) return null;
  return (
    <SkiaImage
      image={image}
      x={0}
      y={0}
      width={width}
      height={height}
      fit="contain"
      sampling={NEAREST}
    />
  );
}

export function PixelStack({ sources, width, height }: Readonly<PixelStackProps>) {
  return (
    <Canvas style={{ width, height }}>
      {sources.map((source, index) => (
        <PixelLayer key={index} source={source} width={width} height={height} />
      ))}
    </Canvas>
  );
}
