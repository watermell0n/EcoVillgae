import { useQuery } from "@tanstack/react-query";
import { router, usePathname } from "expo-router";
import { createContext, useContext, useEffect, useMemo, type PropsWithChildren } from "react";
import { ActivityIndicator, Image, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthProvider";
import { fetchClassroomAccess, hasModule, isModuleSuspended, type ClassroomAccess, type ModuleGroup } from "./api";
import { PresentationRealtimeClient } from "./presentationRealtime";
import { colors } from "@/theme/tokens";
import { fetchGuidedActivity } from "@/features/student/api";

type ClassroomContextValue = {
  access?: ClassroomAccess;
  error: Error | null;
  isLoading: boolean;
  isOffline: boolean;
  hasCachedData: boolean;
  enabled(group: ModuleGroup): boolean;
  // 선생님이 학생 이용만 잠근 기능입니다. 메뉴는 보이지만 들어갈 수 없습니다.
  suspended(group: ModuleGroup): boolean;
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
  const guidedActivityId = query.data?.resources.guidedActivityId;
  const guidedQuery = useQuery({
    queryKey: ["guided-activity", identity?.classroomId, guidedActivityId],
    queryFn: () => fetchGuidedActivity(identity!.classroomId, guidedActivityId!),
    enabled: Boolean(identity?.classroomId && guidedActivityId),
    // 자료가 넘어가면 WS-ACT-001 이벤트로 바로 다시 읽습니다. 이 주기는 연결이 끊겼을 때를
    // 위한 안전망이라 짧게 둘 이유가 없습니다. 자료는 수백 KB라 잦은 폴링이 그대로 비용입니다.
    refetchInterval: 20_000,
    refetchIntervalInBackground: true
  });

  // 선생님이 자료를 넘기면 폴링 주기를 기다리지 않고 즉시 현재 장을 다시 읽습니다.
  const refetchGuided = guidedQuery.refetch;
  useEffect(() => {
    const classroomId = identity?.classroomId;
    if (!classroomId || !guidedActivityId) return;
    const client = new PresentationRealtimeClient({
      classroomId,
      activityId: guidedActivityId,
      onPresentationChanged: () => {
        void refetchGuided();
      }
    });
    client.start();
    return () => client.stop();
  }, [identity?.classroomId, guidedActivityId, refetchGuided]);

  const value = useMemo<ClassroomContextValue>(() => ({
    access: query.data,
    error: query.error,
    isLoading: Boolean(identity) && query.isPending,
    isOffline: query.error instanceof ApiError && query.error.kind === "offline",
    hasCachedData: Boolean(query.data && query.error),
    enabled: (group) => hasModule(query.data, group),
    suspended: (group) => isModuleSuspended(query.data, group),
    refetch: query.refetch
  }), [identity, query.data, query.error, query.isPending, query.refetch]);

  useEffect(() => {
    const group = routeGroups[pathname];
    if (!group || query.isPending || !query.data) return;
    // 잠긴 기능은 화면을 열어 두지 않고 마을 광장으로 돌려보냅니다.
    if (!hasModule(query.data, group) || isModuleSuspended(query.data, group)) router.replace("/(tabs)");
  }, [pathname, query.data, query.isPending]);

  const locked = Boolean(identity && query.data?.mode === "CLOSED");
  const guided = Boolean(identity && query.data?.mode === "GUIDED");

  return (
    <ClassroomContext.Provider value={value}>
      {guided ? (
        <GuidedLessonScreen
          title={guidedQuery.data?.presentationTitle}
          imageUrl={guidedQuery.data?.presentationImageUrl}
          isRefreshing={guidedQuery.isFetching}
        />
      ) : locked ? <ClassroomLockScreen isRefreshing={query.isFetching} /> : children}
    </ClassroomContext.Provider>
  );
}

function GuidedLessonScreen({
  title,
  imageUrl,
  isRefreshing
}: {
  title?: string;
  imageUrl?: string;
  isRefreshing: boolean;
}) {
  return (
    <SafeAreaView style={styles.guidedSafeArea}>
      <View style={styles.guidedHeader}>
        <View>
          <Text style={styles.guidedEyebrow}>선생님 수업 중</Text>
          <Text accessibilityRole="header" style={styles.guidedTitle}>{title ?? "수업 화면을 준비하고 있어요"}</Text>
        </View>
        {isRefreshing ? <ActivityIndicator size="small" color={colors.green500} /> : <View style={styles.lockStatusDot} />}
      </View>
      <View style={styles.guidedCanvas}>
        {imageUrl ? (
          <Image source={{ uri: imageUrl }} resizeMode="contain" style={styles.guidedImage} accessibilityLabel={title ?? "선생님 수업 자료"} />
        ) : (
          <View style={styles.guidedLoading}>
            <ActivityIndicator size="large" color={colors.green500} />
            <Text style={styles.lockDescription}>선생님 화면과 연결하고 있어요.</Text>
          </View>
        )}
      </View>
      <Text style={styles.guidedNotice}>수업이 끝날 때까지 이 화면으로 고정됩니다.</Text>
    </SafeAreaView>
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
  guidedSafeArea: { flex: 1, backgroundColor: "#F4F7F2" },
  guidedHeader: { minHeight: 76, paddingHorizontal: 24, paddingVertical: 12, flexDirection: "row", alignItems: "center", justifyContent: "space-between", borderBottomWidth: 1, borderBottomColor: colors.lineSoft, backgroundColor: colors.white },
  guidedEyebrow: { color: colors.green500, fontSize: 13, fontWeight: "900" },
  guidedTitle: { marginTop: 3, color: colors.ink900, fontSize: 21, fontWeight: "900" },
  guidedCanvas: { flex: 1, margin: 16, overflow: "hidden", borderRadius: 14, backgroundColor: colors.white },
  guidedImage: { width: "100%", height: "100%" },
  guidedLoading: { flex: 1, alignItems: "center", justifyContent: "center" },
  guidedNotice: { paddingBottom: 12, color: colors.muted600, fontSize: 13, fontWeight: "800", textAlign: "center" },
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
