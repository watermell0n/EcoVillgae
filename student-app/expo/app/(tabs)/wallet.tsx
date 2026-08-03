import { StyleSheet, Text, View } from "react-native";

import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { transactions } from "@/features/home/data";
import { colors, radii } from "@/theme/tokens";

export default function WalletScreen() {
  return (
    <Screen title="내 지갑" subtitle="SCR-STU-004 · 금액은 서버의 최종 응답으로만 확정합니다.">
      <View style={styles.summaryRow}>
        <StateCard tone="sky"><Text style={styles.metricLabel}>사용할 수 있는 돈</Text><Text style={styles.metricValue}>158 비빔</Text></StateCard>
        <StateCard tone="mint"><Text style={styles.metricLabel}>이번 달 받은 돈</Text><Text style={styles.metricValue}>175 비빔</Text></StateCard>
        <StateCard tone="cream"><Text style={styles.metricLabel}>이번 달 낸 세금</Text><Text style={styles.metricValue}>17 비빔</Text></StateCard>
      </View>

      <StateCard title="최근 거래">
        {transactions.map((transaction) => (
          <View key={transaction.id} style={styles.transaction}>
            <View style={styles.transactionCopy}>
              <Text style={styles.transactionTitle}>{transaction.title}</Text>
              <Text style={styles.transactionId}>{transaction.id}</Text>
            </View>
            <View style={styles.transactionAmount}>
              <Text style={[styles.amount, transaction.amount < 0 ? styles.negative : styles.positive]}>
                {transaction.amount > 0 ? "+" : ""}{transaction.amount} 비빔
              </Text>
              <Text style={styles.after}>거래 후 {transaction.after} 비빔</Text>
            </View>
          </View>
        ))}
      </StateCard>
    </Screen>
  );
}

const styles = StyleSheet.create({
  summaryRow: { flexDirection: "row", gap: 16 },
  metricLabel: { color: colors.muted600, fontSize: 14, fontWeight: "700" },
  metricValue: { color: colors.ink900, fontSize: 25, fontWeight: "900" },
  transaction: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    minHeight: 72,
    paddingHorizontal: 16,
    borderRadius: radii.control,
    backgroundColor: colors.washBlue
  },
  transactionCopy: { gap: 3 },
  transactionTitle: { color: colors.ink900, fontSize: 16, fontWeight: "800" },
  transactionId: { color: colors.muted600, fontSize: 12 },
  transactionAmount: { alignItems: "flex-end", gap: 3 },
  amount: { fontSize: 16, fontWeight: "900" },
  positive: { color: colors.green500 },
  negative: { color: colors.red500 },
  after: { color: colors.muted600, fontSize: 12 }
});
