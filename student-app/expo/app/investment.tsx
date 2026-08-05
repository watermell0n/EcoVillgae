import { useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
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
  const scenarios = [
    { name: "거북이상사", risk: "안정형", range: "+2% ~ +6%" },
    { name: "독수리테크", risk: "성장형", range: "-4% ~ +12%" },
    { name: "호랑이운송", risk: "도전형", range: "-12% ~ +18%" }
  ];
  const [selectedCompany, setSelectedCompany] = useState(scenarios[0].name);

  return (
    <View style={styles.content}>
      <Text style={styles.sectionTitle}>단기 투자 상품</Text>
      <View style={styles.shortGrid}>
        {scenarios.map((scenario) => (
          <Pressable key={scenario.name} onPress={() => setSelectedCompany(scenario.name)} style={[styles.shortProduct, selectedCompany === scenario.name && styles.shortProductActive]}>
            <View style={styles.marketLine}><Text style={styles.company}>{scenario.name}</Text><Text style={styles.signal}>{scenario.risk}</Text></View>
            <View style={styles.rangeLine}><Text style={styles.rangeLabel}>결과 범위</Text><Text style={styles.rangeValue}>{scenario.range}</Text></View>
          </Pressable>
        ))}
      </View>
      <View style={styles.scenarioAction}><Text style={styles.scenarioLabel}>{selectedCompany}</Text><Pressable style={styles.primaryButton}><Text style={styles.primaryText}>투자 시나리오 확인</Text></Pressable><Pressable style={styles.outlineButton}><Text style={styles.outlineText}>투자 금액 정하기</Text></Pressable></View>
    </View>
  );
}

function LongInvestment() {
  const { identity } = useAuth();
  const currencyName = identity?.currencyName ?? "비빔";
  const stocks = [
    { name: "독수리테크", change: "+12.4%", news: "AI 신제품 반응이 좋아요.", points: "▁▂▃▅▆█" },
    { name: "거북이상사", change: "+3.1%", news: "생활용품 판매가 안정적이에요.", points: "▂▂▃▃▄▅" },
    { name: "호랑이운송", change: "-4.8%", news: "물류 비용이 늘어났어요.", points: "█▇▆▅▄▃" }
  ];
  const [selectedStock, setSelectedStock] = useState(stocks[0].name);
  const current = stocks.find((stock) => stock.name === selectedStock) ?? stocks[0];

  return (
    <View style={styles.content}>
      <Text style={styles.sectionTitle}>장기투자 목록</Text>
      <View style={styles.stockList}>
        {stocks.map((stock) => <Pressable key={stock.name} onPress={() => setSelectedStock(stock.name)} style={[styles.stockRow, selectedStock === stock.name && styles.stockRowActive]}><View><Text style={styles.company}>{stock.name}</Text><Text style={styles.stockNews}>{stock.news}</Text></View><Text style={[styles.stockChange, stock.change.startsWith("-") && styles.loss]}>{stock.change}</Text></Pressable>)}
      </View>
      <View style={styles.stockDetail}>
        <View style={styles.marketLine}><Text style={styles.company}>{current.name}</Text><Text style={[styles.stockChange, current.change.startsWith("-") && styles.loss]}>{current.change}</Text></View>
        <Text style={styles.graph}>{current.points}</Text>
        <Text style={styles.stockNews}>{current.news}</Text>
      </View>
      <View style={styles.holdingSection}>
        <Text style={styles.sectionTitle}>내 주식 보유 현황</Text>
        <View style={styles.holdingRow}><View><Text style={styles.company}>독수리테크</Text><Text style={styles.stockNews}>보유 수량 3주 · 평균단가 50 {currencyName}</Text></View><View style={styles.holdingRight}><Text style={styles.holdingValue}>168 {currencyName}</Text><Text style={styles.stockChange}>+18 {currencyName}</Text></View></View>
        <View style={styles.holdingRow}><View><Text style={styles.company}>거북이상사</Text><Text style={styles.stockNews}>보유 수량 2주 · 평균단가 40 {currencyName}</Text></View><View style={styles.holdingRight}><Text style={styles.holdingValue}>83 {currencyName}</Text><Text style={styles.stockChange}>+3 {currencyName}</Text></View></View>
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
  sectionTitle: { color: colors.ink900, fontSize: 23, fontWeight: "900" },
  shortGrid: { flexDirection: "row", flexWrap: "wrap", gap: 12 },
  shortProduct: { width: 260, minHeight: 168, justifyContent: "space-between", padding: 20, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 6, backgroundColor: colors.white },
  shortProductActive: { borderColor: colors.orange500, backgroundColor: "#FFF8F0" },
  scenarioAction: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: 10, paddingTop: 8, borderTopWidth: 1, borderTopColor: colors.lineSoft },
  scenarioLabel: { marginRight: 8, color: colors.ink900, fontSize: 17, fontWeight: "900" },
  rangeLine: { gap: 5, paddingTop: 22 },
  rangeLabel: { color: colors.muted600, fontSize: 13, fontWeight: "800" },
  rangeValue: { color: colors.ink900, fontSize: 21, fontWeight: "900" },
  marketLine: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 20 },
  company: { color: colors.ink900, fontSize: 19, fontWeight: "900" },
  signal: { color: colors.green500, fontSize: 14, fontWeight: "900" },
  stockList: { borderTopWidth: 1, borderTopColor: colors.lineSoft },
  stockRow: { minHeight: 76, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 18, paddingHorizontal: 12, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  stockRowActive: { backgroundColor: colors.washMint },
  stockNews: { marginTop: 5, color: colors.muted600, fontSize: 14, fontWeight: "700" },
  stockChange: { color: colors.green500, fontSize: 18, fontWeight: "900" },
  loss: { color: colors.red500 },
  stockDetail: { gap: 10, padding: 18, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 6, backgroundColor: colors.white },
  graph: { color: colors.green500, fontSize: 34, letterSpacing: 2, fontWeight: "900" },
  holdingSection: { gap: 0, paddingTop: 12 },
  holdingRow: { minHeight: 82, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 18, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  holdingRight: { alignItems: "flex-end", gap: 4 },
  holdingValue: { color: colors.ink900, fontSize: 18, fontWeight: "900" },
  decisionRow: { flexDirection: "row", gap: 12 },
  primaryButton: { alignSelf: "flex-start", minHeight: 46, justifyContent: "center", paddingHorizontal: 22, borderRadius: 8, backgroundColor: colors.orange500 },
  primaryText: { color: colors.white, fontSize: 15, fontWeight: "900" },
  outlineButton: { minHeight: 46, justifyContent: "center", paddingHorizontal: 22, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 8, backgroundColor: colors.white },
  outlineText: { color: colors.ink900, fontSize: 15, fontWeight: "900" },
  empty: { maxWidth: 760, gap: 10, paddingVertical: 34, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.lineSoft },
  emptyTitle: { color: colors.ink900, fontSize: 23, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 16, lineHeight: 24, fontWeight: "700" }
});
