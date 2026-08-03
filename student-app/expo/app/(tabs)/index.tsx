import { router } from "expo-router";
import { ActivityIndicator, Pressable, StyleSheet, Text, useWindowDimensions, View } from "react-native";

import { Screen } from "@/components/Screen";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import type { ModuleGroup } from "@/features/classroom/api";
import { colors, radii } from "@/theme/tokens";

const toneColors = {
  orange: colors.orange500,
  green: colors.green500,
  purple: colors.purple500,
  red: colors.red500,
  yellow: colors.yellow500,
  sky: "#68A9D6"
};

type HomeCard = {
  id: ModuleGroup;
  title: string;
  subtitle: string;
  icon: string;
  tone: keyof typeof toneColors;
  route: "/(tabs)/shop" | "/(tabs)/wallet" | "/bank" | "/investment" | "/classroom" | "/trade" | "/quiz" | "/character" | "/paystub";
};

const cards: HomeCard[] = [
  { id: "wallet", title: "내 지갑", subtitle: "잔액과 거래 내역을 확인해요", icon: "👛", tone: "sky", route: "/(tabs)/wallet" },
  { id: "shop", title: "이코 상점", subtitle: "열린 상품을 고르고 구매해요", icon: "🏪", tone: "orange", route: "/(tabs)/shop" },
  { id: "bank", title: "이코 은행", subtitle: "예금과 적금 상품을 살펴봐요", icon: "🏦", tone: "purple", route: "/bank" },
  { id: "investment", title: "투자 광장", subtitle: "시장 가격과 내 손익을 확인해요", icon: "📈", tone: "red", route: "/investment" },
  { id: "trade", title: "친구 거래소", subtitle: "친구와 비빔 거래를 제안해요", icon: "🤝", tone: "sky", route: "/trade" },
  { id: "quiz", title: "경제생활 퀴즈", subtitle: "선생님이 연 퀴즈에 참여해요", icon: "📝", tone: "green", route: "/quiz" },
  { id: "meeting", title: "마을 회의", subtitle: "공동기금 사용처에 투표해요", icon: "🗳️", tone: "yellow", route: "/classroom" },
  { id: "character", title: "내 캐릭터", subtitle: "보유 아이템을 확인해요", icon: "🙂", tone: "sky", route: "/character" },
  { id: "paystub", title: "월급명세서", subtitle: "받은 돈과 세금을 확인해요", icon: "💼", tone: "green", route: "/paystub" }
];

export default function VillageScreen() {
  const { width } = useWindowDimensions();
  const { enabled, error, hasCachedData, isLoading, isOffline, refetch } = useClassroom();
  const columns = width >= 1050 ? 3 : width >= 640 ? 2 : 1;
  const visibleCards = cards.filter((card) => enabled(card.id));

  return (
    <Screen title="마을 광장">
      {isLoading ? (
        <View style={styles.state}><ActivityIndicator color={colors.green500} size="large" /><Text style={styles.stateText}>우리 반 기능을 확인하고 있어요.</Text></View>
      ) : error && !hasCachedData ? (
        <View style={styles.state}>
          <Text style={styles.stateTitle}>{isOffline ? "인터넷 연결이 끊겼어요" : "마을 기능을 불러오지 못했어요"}</Text>
          <Text style={styles.stateText}>{isOffline ? "연결되면 다시 확인할 수 있어요." : error.message}</Text>
          <Pressable onPress={() => refetch()} style={styles.retry}><Text style={styles.retryText}>다시 불러오기</Text></Pressable>
        </View>
      ) : visibleCards.length === 0 ? (
        <View style={styles.state}>
          <Text style={styles.stateIcon}>🏘️</Text>
          <Text style={styles.stateTitle}>지금 열려 있는 활동이 없어요</Text>
          <Text style={styles.stateText}>선생님이 활동을 열면 여기에 바로 나타나요.</Text>
        </View>
      ) : (
        <>
        {isOffline ? <View style={styles.offlineBanner}><Text style={styles.offlineText}>오프라인 상태예요. 마지막으로 확인한 메뉴를 보여주고 있어요.</Text><Pressable onPress={() => refetch()}><Text style={styles.offlineAction}>다시 연결</Text></Pressable></View> : null}
        <View style={styles.grid}>
          {visibleCards.map((card) => (
            <Pressable
              accessibilityRole="button"
              key={card.id}
              onPress={() => router.push(card.route)}
              style={({ pressed }) => [
                styles.module,
                { width: columns === 3 ? "31.6%" : columns === 2 ? "48.5%" : "100%", borderColor: toneColors[card.tone] },
                pressed && styles.pressed
              ]}
            >
              <Text style={styles.moduleIcon}>{card.icon}</Text>
              <Text style={styles.moduleTitle}>{card.title}</Text>
              <Text style={styles.moduleSubtitle}>{card.subtitle}</Text>
              <Text style={[styles.moduleCta, { backgroundColor: toneColors[card.tone] }]}>들어가기</Text>
            </Pressable>
          ))}
        </View>
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 20 },
  state: { minHeight: 360, alignItems: "center", justifyContent: "center", gap: 14 },
  stateIcon: { fontSize: 54 },
  stateTitle: { color: colors.ink900, fontSize: 23, fontWeight: "900", textAlign: "center" },
  stateText: { maxWidth: 460, color: colors.muted600, fontSize: 16, lineHeight: 24, textAlign: "center" },
  retry: { marginTop: 4, paddingHorizontal: 20, paddingVertical: 12, borderRadius: radii.control, backgroundColor: colors.green500 },
  retryText: { color: colors.white, fontWeight: "900" },
  offlineBanner: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 12, paddingHorizontal: 16, paddingVertical: 12, borderRadius: radii.control, backgroundColor: "#FFF5D8" },
  offlineText: { flex: 1, color: colors.ink900, fontSize: 14, fontWeight: "700" },
  offlineAction: { color: colors.orange500, fontWeight: "900" },
  module: {
    minHeight: 214,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 20,
    paddingVertical: 24,
    borderRadius: 12,
    borderWidth: 1.5,
    backgroundColor: "rgba(255,255,255,0.92)",
    shadowColor: colors.navy900,
    shadowOpacity: 0.08,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 2
  },
  pressed: { opacity: 0.8 },
  moduleIcon: { marginBottom: 14, fontSize: 42 },
  moduleTitle: { color: colors.ink900, fontSize: 21, fontWeight: "900", textAlign: "center" },
  moduleSubtitle: { minHeight: 44, marginTop: 10, marginBottom: 14, color: colors.muted600, fontSize: 14, lineHeight: 21, fontWeight: "700", textAlign: "center" },
  moduleCta: { minHeight: 34, paddingHorizontal: 16, paddingVertical: 8, overflow: "hidden", borderRadius: radii.chip, color: colors.white, fontSize: 14, fontWeight: "900" }
});
