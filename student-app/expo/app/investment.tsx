import { useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { colors } from "@/theme/tokens";

type InvestmentMode = "short" | "long";

export default function InvestmentScreen() {
  const { access } = useClassroom();
  const modes = access?.investmentModes ?? { shortTerm: false, longTerm: false };
  const availableModes = useMemo(() => [
    ...(modes.shortTerm ? [{ id: "short" as const, label: "단기 투자" }] : []),
    ...(modes.longTerm ? [{ id: "long" as const, label: "장기 투자" }] : [])
  ], [modes.longTerm, modes.shortTerm]);
  const [selected, setSelected] = useState<InvestmentMode>(modes.shortTerm ? "short" : "long");
  const active = availableModes.some((mode) => mode.id === selected) ? selected : availableModes[0]?.id;

  return (
    <Screen title="투자 광장" right={<BackButton />}>
      {availableModes.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>지금 열려 있는 투자 수업이 없어요</Text>
          <Text style={styles.description}>선생님이 단기 투자나 장기 투자를 선택하면 이곳에 나타나요.</Text>
        </View>
      ) : (
        <>
          <View accessibilityRole="tablist" style={styles.tabs}>
            {availableModes.map((mode) => (
              <Pressable
                accessibilityRole="tab"
                accessibilityState={{ selected: active === mode.id }}
                key={mode.id}
                onPress={() => setSelected(mode.id)}
                style={[styles.tab, active === mode.id && styles.tabActive]}
              >
                <Text style={[styles.tabText, active === mode.id && styles.tabTextActive]}>{mode.label}</Text>
              </Pressable>
            ))}
          </View>
          {active === "short" ? <ShortInvestment /> : <LongInvestment />}
        </>
      )}
    </Screen>
  );
}

function ShortInvestment() {
  return (
    <View style={styles.content}>
      <View style={styles.headingBlock}>
        <Text style={styles.eyebrow}>내일 결과 확인</Text>
        <Text style={styles.title}>하루 동안 기업의 변화를 예측해요</Text>
        <Text style={styles.description}>오늘 한 번 투자하면 내일 결과가 확정돼요. 투자 뒤에는 금액을 바꿀 수 없어요.</Text>
      </View>
      <View style={styles.marketRow}>
        <View style={styles.marketLine}><Text style={styles.company}>거북이상사</Text><Text style={styles.signal}>안정형</Text></View>
        <Text style={styles.marketCopy}>생활용품 수요가 꾸준하다는 소식이 있어요.</Text>
        <Pressable style={styles.primaryButton}><Text style={styles.primaryText}>투자 금액 정하기</Text></Pressable>
      </View>
    </View>
  );
}

function LongInvestment() {
  return (
    <View style={styles.content}>
      <View style={styles.headingBlock}>
        <Text style={styles.eyebrow}>매일 새 소식</Text>
        <Text style={styles.title}>소식을 읽고 보유할지 결정해요</Text>
        <Text style={styles.description}>매일 공개되는 기업 소식과 가격 변화를 보고 계속 보유하거나 투자금을 회수할 수 있어요.</Text>
      </View>
      <View style={styles.newsLine}>
        <Text style={styles.newsDate}>오늘</Text>
        <View style={styles.newsBody}>
          <Text style={styles.company}>독수리테크 신제품 발표</Text>
          <Text style={styles.marketCopy}>새 제품의 반응을 더 지켜볼 필요가 있어요.</Text>
        </View>
      </View>
      <View style={styles.decisionRow}>
        <Pressable style={styles.outlineButton}><Text style={styles.outlineText}>계속 보유</Text></Pressable>
        <Pressable style={styles.primaryButton}><Text style={styles.primaryText}>투자금 회수</Text></Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  tabs: { flexDirection: "row", alignSelf: "flex-start", borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  tab: { minWidth: 132, paddingHorizontal: 18, paddingVertical: 14, borderBottomWidth: 3, borderBottomColor: "transparent" },
  tabActive: { borderBottomColor: colors.orange500 },
  tabText: { color: colors.muted600, fontSize: 16, fontWeight: "800", textAlign: "center" },
  tabTextActive: { color: colors.ink900 },
  content: { width: "100%", maxWidth: 960, gap: 24, paddingTop: 12 },
  headingBlock: { gap: 9 },
  eyebrow: { color: colors.green500, fontSize: 14, fontWeight: "900" },
  title: { color: colors.ink900, fontSize: 27, lineHeight: 36, fontWeight: "900" },
  description: { maxWidth: 720, color: colors.muted600, fontSize: 16, lineHeight: 24, fontWeight: "700" },
  marketRow: { gap: 13, paddingVertical: 22, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.lineSoft },
  marketLine: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 20 },
  company: { color: colors.ink900, fontSize: 19, fontWeight: "900" },
  signal: { color: colors.green500, fontSize: 14, fontWeight: "900" },
  marketCopy: { color: colors.muted600, fontSize: 15, lineHeight: 22, fontWeight: "700" },
  newsLine: { flexDirection: "row", gap: 20, paddingVertical: 22, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.lineSoft },
  newsDate: { width: 52, color: colors.orange500, fontSize: 15, fontWeight: "900" },
  newsBody: { flex: 1, gap: 7 },
  decisionRow: { flexDirection: "row", gap: 12 },
  primaryButton: { alignSelf: "flex-start", minHeight: 46, justifyContent: "center", paddingHorizontal: 22, borderRadius: 8, backgroundColor: colors.orange500 },
  primaryText: { color: colors.white, fontSize: 15, fontWeight: "900" },
  outlineButton: { minHeight: 46, justifyContent: "center", paddingHorizontal: 22, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 8, backgroundColor: colors.white },
  outlineText: { color: colors.ink900, fontSize: 15, fontWeight: "900" },
  empty: { maxWidth: 760, gap: 10, paddingVertical: 34, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.lineSoft },
  emptyTitle: { color: colors.ink900, fontSize: 23, fontWeight: "900" }
});
