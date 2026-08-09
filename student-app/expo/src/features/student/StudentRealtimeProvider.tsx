import { useQueryClient } from "@tanstack/react-query";
import { usePathname } from "expo-router";
import { useEffect, useRef, type PropsWithChildren } from "react";
import { AppState } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { StudentEconomyRealtimeClient } from "./realtime";
import { updateStudentPresence } from "./api";

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
        if (event.event_type.startsWith("investment.")) {
          void queryClient.invalidateQueries({ queryKey: ["investment-markets", classroomId] });
          void queryClient.invalidateQueries({ queryKey: ["wallet", classroomId] });
        }
        if (
          event.event_type === "economic_event.published"
          && event.payload.template_type === "INVESTMENT_NEWS"
        ) {
          void queryClient.invalidateQueries({ queryKey: ["investment-markets", classroomId] });
        }
      },
      onRecoveryRequired: () => {
        void queryClient.invalidateQueries({ queryKey: ["financial-products", classroomId] });
        void queryClient.invalidateQueries({ queryKey: ["classroom-access", classroomId] });
        void queryClient.invalidateQueries({ queryKey: ["investment-markets", classroomId] });
        void queryClient.invalidateQueries({ queryKey: ["wallet", classroomId] });
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

  // Some school networks close WebSockets without a useful browser error. Keep a
  // small authenticated HTTP heartbeat so the teacher roster still updates.
  useEffect(() => {
    if (!identity?.classroomId) return;
    const classroomId = identity.classroomId;
    const publish = () => {
      const status = AppState.currentState === "active" ? "ONLINE" : "AWAY";
      void updateStudentPresence(classroomId, status, screenLabel(pathname)).catch(() => undefined);
    };
    publish();
    const timer = setInterval(publish, 20_000);
    return () => clearInterval(timer);
  }, [identity?.classroomId, pathname]);

  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state) => {
      clientRef.current?.updatePresence(
        state === "active" ? "ONLINE" : "AWAY",
        screenLabel(pathname)
      );
      if (identity?.classroomId) {
        void updateStudentPresence(
          identity.classroomId,
          state === "active" ? "ONLINE" : "AWAY",
          screenLabel(pathname)
        ).catch(() => undefined);
      }
    });
    return () => subscription.remove();
  }, [identity?.classroomId, pathname]);

  return children;
}
