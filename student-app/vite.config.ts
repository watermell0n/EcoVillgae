import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/**
 * 캐릭터 공통 코어는 Expo 트리 안에 있고 웹이 그것을 참조한다.
 * Metro는 프로젝트 루트 밖을 기본 설정으로 읽지 못하지만 Vite는 읽을 수 있어서,
 * 이 배치가 양쪽이 추가 설정 없이 단일 사본을 공유하는 유일한 방법이다.
 * 자세한 이유는 expo/src/features/character/core/index.ts 주석 참고.
 */
const characterCore = fileURLToPath(
  new URL("./expo/src/features/character/core", import.meta.url)
);

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@character-core": characterCore
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, "")
      },
      "/ws": {
        target: "ws://localhost:8080",
        ws: true,
        changeOrigin: true
      }
    }
  }
});
