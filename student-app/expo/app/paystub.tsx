import { StyleSheet, Text, View } from "react-native";

import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { colors } from "@/theme/tokens";

const rows = [
  { label: "1인 1역 월급", amount: 150 },
  { label: "퀴즈 정답수당", amount: 25 },
  { label: "소득세", amount: -17 }
];

export default function PaystubScreen() {
  return (
    <Screen title="월급명세서" subtitle="SCR-STU-005 · 급여와 세금의 서버 확정 내역입니다." right={<BackButton />}>
      <View style={styles.columns}>
        <StateCard title="급여 명세서">
          {rows.map((row) => (
            <View key={row.label} style={styles.row}>
              <Text style={styles.label}>{row.label}</Text>
              <Text style={[styles.amount, row.amount < 0 && styles.negative]}>{row.amount > 0 ? "+" : ""}{row.amount} 비빔</Text>
            </View>
          ))}
          <View style={styles.total}><Text style={styles.totalLabel}>내 지갑에 들어온 돈</Text><Text style={styles.totalValue}>158 비빔</Text></View>
        </StateCard>
        <StateCard title="세금은 어디로 가나요?" tone="cream">
          <Text style={styles.explain}>소득세 17비빔은 우리 반 공동기금으로 모여요. 나중에 함께 투표해서 사용할 곳을 정해요.</Text>
          <Text style={styles.flow}>총급여 175 → 소득세 17 → 실수령액 158</Text>
        </StateCard>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  columns: { flexDirection: "row", gap: 18 },
  row: { minWidth: 430, flexDirection: "row", justifyContent: "space-between", paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  label: { color: colors.ink900, fontSize: 16, fontWeight: "700" },
  amount: { color: colors.green500, fontSize: 16, fontWeight: "900" },
  negative: { color: colors.red500 },
  total: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingTop: 16 },
  totalLabel: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  totalValue: { color: colors.purple500, fontSize: 26, fontWeight: "900" },
  explain: { maxWidth: 440, color: colors.muted600, fontSize: 16, lineHeight: 25 },
  flow: { color: colors.orange500, fontSize: 18, fontWeight: "900", marginTop: 18 }
});
