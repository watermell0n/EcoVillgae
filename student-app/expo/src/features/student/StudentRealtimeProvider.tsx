import { useQueryClient } from "@tanstack/react-query";
import { usePathname } from "expo-router";
import { useEffect, useRef, type PropsWithChildren } from "react";
import { AppState } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { StudentEconomyRealtimeClient } from "./realtime";

const screenLabels: Record<string, string> = {
  "/": "마을 광장",
  "/wallet": "내 지갑",
  "/shop": "이코 상점",
  "/bank": "이코 은행",
  "/investment": "투자 광장",
  "/trade": "친구 거래소",
  "/quiz": "경제생활 퀴즈",
  "/meeting": "마을 회의",
  "/real-estate": "부동산",
  "/credit": "신용등급",
  "/character": "내 캐릭터",
  "/paystub": "월급명세서",
  "/classroom": "선생님 수업",
  "/materials": "수업 자료",
  "/profile": "내 프로필"
};

function screenLabel(pathname: string) {
  return screenLabels[pathname] ?? (pathname.replace(/^\//, "") || "마을 광장");
}

export function StudentRealtimeProvider({ children }: PropsWithChildren) {
  const { identity } = useAuth();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const clientRef = useRef<StudentEconomyRealtimeClient | null>(null);

  useEffect(() => {
    if (!identity?.classroomId) return;
    const classroomId = identity.classroomId;
    const client = new StudentEconomyRealtimeClient({
      classroomId,
      onEvent: (event) => {
        if (event.event_type === "finance.product.changed") {
          void queryClient.invalidateQueries({ queryKey: ["financial-products", classroomId] });
        }
      },
      onRecoveryRequired: () => {
        void queryClient.invalidateQueries({ queryKey: ["financial-products", classroomId] });
        void queryClient.invalidateQueries({ queryKey: ["classroom-access", classroomId] });
      }
    });
    clientRef.current = client;
    client.start();
    return () => {
      clientRef.current = null;
      client.stop();
    };
  }, [identity?.classroomId, queryClient]);

  useEffect(() => {
    const status = AppState.currentState === "active" ? "ONLINE" : "AWAY";
    clientRef.current?.updatePresence(status, screenLabel(pathname));
  }, [pathname]);

  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state) => {
      clientRef.current?.updatePresence(
        state === "active" ? "ONLINE" : "AWAY",
        screenLabel(pathname)
      );
    });
    return () => subscription.remove();
  }, [pathname]);

  return children;
}
