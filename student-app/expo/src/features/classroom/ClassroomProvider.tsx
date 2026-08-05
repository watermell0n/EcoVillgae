import { useQuery } from "@tanstack/react-query";
import { router, usePathname } from "expo-router";
import { createContext, useContext, useEffect, useMemo, type PropsWithChildren } from "react";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthProvider";
import { fetchClassroomAccess, hasModule, type ClassroomAccess, type ModuleGroup } from "./api";
import { colors } from "@/theme/tokens";

type ClassroomContextValue = {
  access?: ClassroomAccess;
  error: Error | null;
  isLoading: boolean;
  isOffline: boolean;
  hasCachedData: boolean;
  enabled(group: ModuleGroup): boolean;
  refetch(): Promise<unknown>;
};

const ClassroomContext = createContext<ClassroomContextValue | null>(null);

// `/classroom`(선생님 수업)과 `/materials`는 모듈 스위치가 아닌 수업 진행에 딸린 화면이라
// 모듈 가드를 두지 않습니다.
const routeGroups: Record<string, ModuleGroup> = {
  "/wallet": "wallet",
  "/shop": "shop",
  "/bank": "bank",
  "/investment": "investment",
  "/trade": "trade",
  "/quiz": "quiz",
  "/meeting": "meeting",
  "/real-estate": "realEstate",
  "/credit": "credit",
  "/character": "character",
  "/paystub": "paystub"
};

export function ClassroomProvider({ children }: PropsWithChildren) {
  const { identity } = useAuth();
  const pathname = usePathname();
  const query = useQuery({
    queryKey: ["classroom-access", identity?.classroomId],
    queryFn: () => fetchClassroomAccess(identity!.classroomId),
    enabled: Boolean(identity?.classroomId),
    staleTime: 15_000,
    gcTime: 30 * 60_000,
    refetchOnMount: "always",
    refetchInterval: 3_000,
    refetchIntervalInBackground: true
  });

  const value = useMemo<ClassroomContextValue>(() => ({
    access: query.data,
    error: query.error,
    isLoading: Boolean(identity) && query.isPending,
    isOffline: query.error instanceof ApiError && query.error.kind === "offline",
    hasCachedData: Boolean(query.data && query.error),
    enabled: (group) => hasModule(query.data, group),
    refetch: query.refetch
  }), [identity, query.data, query.error, query.isPending, query.refetch]);

  useEffect(() => {
    const group = routeGroups[pathname];
    if (!group || query.isPending || !query.data) return;
    if (!hasModule(query.data, group)) router.replace("/(tabs)");
  }, [pathname, query.data, query.isPending]);

  const locked = Boolean(identity && query.data?.mode === "CLOSED");

  return (
    <ClassroomContext.Provider value={value}>
      {locked ? <ClassroomLockScreen isRefreshing={query.isFetching} /> : children}
    </ClassroomContext.Provider>
  );
}

function ClassroomLockScreen({ isRefreshing }: { isRefreshing: boolean }) {
  return (
    <SafeAreaView style={styles.lockSafeArea}>
      <View style={styles.lockPage}>
        <Text style={styles.lockEyebrow}>선생님 수업 중</Text>
        <View style={styles.lockIllustration}>
          <View style={styles.lockBoard} />
          <View style={styles.lockDesk} />
        </View>
        <Text accessibilityRole="header" style={styles.lockTitle}>지금은 선생님 화면을 함께 봐요</Text>
        <Text style={styles.lockDescription}>수업이 끝나면 마을 메뉴가 자동으로 다시 열려요.</Text>
        <View style={styles.lockStatus}>
          {isRefreshing ? <ActivityIndicator size="small" color={colors.green500} /> : <View style={styles.lockStatusDot} />}
          <Text style={styles.lockStatusText}>수업 상태 확인 중</Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  lockSafeArea: { flex: 1, backgroundColor: colors.sky100 },
  lockPage: { flex: 1, alignItems: "center", justifyContent: "center", padding: 32 },
  lockEyebrow: { color: colors.green500, fontSize: 15, fontWeight: "900" },
  lockIllustration: { width: 250, height: 160, marginVertical: 28, alignItems: "center", justifyContent: "flex-end" },
  lockBoard: { position: "absolute", top: 0, width: 210, height: 110, borderWidth: 8, borderColor: "#8CB7C5", borderRadius: 4, backgroundColor: "#F8FCFF" },
  lockDesk: { width: 134, height: 22, borderRadius: 3, backgroundColor: "#EDB06F" },
  lockTitle: { maxWidth: 620, color: colors.ink900, fontSize: 30, lineHeight: 40, fontWeight: "900", textAlign: "center" },
  lockDescription: { marginTop: 12, color: colors.muted600, fontSize: 17, lineHeight: 25, fontWeight: "700", textAlign: "center" },
  lockStatus: { minHeight: 44, marginTop: 26, flexDirection: "row", alignItems: "center", gap: 10, paddingHorizontal: 18, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 8, backgroundColor: colors.white },
  lockStatusDot: { width: 9, height: 9, borderRadius: 5, backgroundColor: colors.green500 },
  lockStatusText: { color: colors.muted600, fontSize: 14, fontWeight: "800" }
});

export function useClassroom() {
  const value = useContext(ClassroomContext);
  if (!value) throw new Error("useClassroom must be used inside ClassroomProvider");
  return value;
}
