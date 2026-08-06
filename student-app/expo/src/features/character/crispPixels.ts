import { useEffect, useId } from "react";
import { Platform } from "react-native";

const STYLE_ID = "ecovillage-crisp-pixels";
const ID_PREFIX = "crisp-";

/**
 * 도트 스프라이트를 정수 배율로 확대할 때 브라우저 기본 보간이 픽셀 경계를 뭉갠다.
 * RN Web은 Image에 넘긴 imageRendering 스타일을 통과시켜 주지 않지만 nativeID는 DOM id로
 * 매핑되므로, id 접두사를 스코프로 규칙을 문서에 한 번만 심는다.
 *
 * 웹 전용이다. 태블릿 네이티브는 RN Image에 nearest-neighbor 옵션이 없어
 * @shopify/react-native-skia의 FilterMode.Nearest나 사전 확대 스프라이트가 필요하다.
 */
export function useCrispPixelId() {
  const id = ID_PREFIX + useId().replace(/[^a-zA-Z0-9_-]/g, "");

  useEffect(() => {
    if (Platform.OS !== "web" || typeof document === "undefined") return;
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `[id^="${ID_PREFIX}"] img { image-rendering: pixelated; }`;
    document.head.appendChild(style);
  }, []);

  return id;
}
