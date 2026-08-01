import { router } from "expo-router";
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from "react-native";

import { Screen } from "@/components/Screen";
import { moduleCards } from "@/features/home/data";
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
  id: "shop" | "bank" | "investment" | "meeting" | "trade" | "quiz";
  title: string;
  subtitle: string;
  icon: string;
  tone: keyof typeof toneColors;
  state: "active" | "locked" | "new" | "resultOnly";
  cta: string;
};

const primaryCards = moduleCards.reduce<HomeCard[]>((cards, card) => {
  if (card.id === "paystub" || card.id === "character") return cards;
  cards.push({
    ...card,
    id: card.id as HomeCard["id"],
    cta: card.id === "investment" ? "결과 보기" : card.id === "meeting" ? "수업 후 열려요" : "입장하기"
  });
  return cards;
}, []);

const homeCards: HomeCard[] = [
  ...primaryCards,
  { id: "trade", title: "친구 거래소", subtitle: "친구와 아이템·비빔 거래를 제안해요", icon: "🤝", tone: "sky", state: "active", cta: "거래하기" },
  { id: "quiz", title: "오늘의 퀴즈", subtitle: "선생님이 연 퀴즈에 참여해요", icon: "📝", tone: "green", state: "active", cta: "풀러가기" }
];

export default function VillageScreen() {
  const { width } = useWindowDimensions();
  const columns = width >= 1050 ? 3 : width >= 640 ? 2 : 1;

  function openModule(module: HomeCard) {
    const routes = {
      shop: "/(tabs)/shop",
      bank: "/bank",
      investment: "/investment",
      meeting: "/classroom",
      trade: "/trade",
      quiz: "/quiz"
    } as const;
    router.push(routes[module.id]);
  }

  return (
    <Screen title="마을 광장" subtitle="지금은 자율시간이에요. 열린 마을 기능만 사용할 수 있어요.">
      <View style={styles.grid}>
        {homeCards.map((module) => {
          const enabled = module.state !== "locked";
          const status = module.state === "new"
            ? "NEW"
            : module.state === "locked"
              ? "잠김"
              : module.state === "resultOnly"
                ? "조회"
                : "열림";
          return (
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ disabled: !enabled }}
              disabled={!enabled}
              key={module.id}
              onPress={() => openModule(module)}
              style={({ pressed }) => [
                styles.module,
                {
                  width: columns === 3 ? "31.6%" : columns === 2 ? "48.5%" : "100%",
                  borderColor: toneColors[module.tone]
                },
                !enabled && styles.moduleDisabled,
                pressed && styles.pressed
              ]}
            >
              <Text style={styles.statusBadge}>{status}</Text>
              <Text style={styles.moduleIcon}>{module.icon}</Text>
              <Text style={styles.moduleTitle}>{module.title}</Text>
              <Text style={styles.moduleSubtitle}>{module.subtitle}</Text>
              <Text style={[styles.moduleCta, { backgroundColor: toneColors[module.tone] }]}>{module.cta}</Text>
            </Pressable>
          );
        })}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 20 },
  module: {
    position: "relative",
    minHeight: 214,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 20,
    paddingTop: 26,
    paddingBottom: 22,
    borderRadius: 18,
    borderWidth: 1.5,
    backgroundColor: "rgba(255,255,255,0.9)",
    shadowColor: colors.navy900,
    shadowOpacity: 0.1,
    shadowRadius: 15,
    shadowOffset: { width: 0, height: 7 },
    elevation: 3
  },
  statusBadge: {
    position: "absolute",
    top: 14,
    right: 14,
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: radii.chip,
    color: colors.muted600,
    backgroundColor: "#EEF3F4",
    fontSize: 12,
    fontWeight: "900"
  },
  moduleDisabled: { opacity: 0.54 },
  pressed: { opacity: 0.8 },
  moduleIcon: { marginBottom: 14, fontSize: 42 },
  moduleTitle: { color: colors.ink900, fontSize: 21, fontWeight: "900", textAlign: "center" },
  moduleSubtitle: {
    minHeight: 44,
    marginTop: 10,
    marginBottom: 14,
    color: colors.muted600,
    fontSize: 14,
    lineHeight: 21,
    fontWeight: "700",
    textAlign: "center"
  },
  moduleCta: {
    minHeight: 34,
    paddingHorizontal: 16,
    paddingVertical: 8,
    overflow: "hidden",
    borderRadius: radii.chip,
    color: colors.white,
    fontSize: 14,
    fontWeight: "900"
  }
});
