import { useQuery } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchWallet } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function WalletScreen() {
  const { identity } = useAuth();
  const wallet = useQuery({
    queryKey: ["wallet", identity?.classroomId],
    queryFn: () => fetchWallet(identity!.classroomId),
    enabled: Boolean(identity?.classroomId)
  });

  return (
    <Screen title="내 지갑">
      {wallet.isPending ? (
        <View style={styles.center}><ActivityIndicator color={colors.green500} size="large" /><Text style={styles.helper}>지갑을 확인하고 있어요.</Text></View>
      ) : wallet.isError ? (
        <View style={styles.center}><Text style={styles.error}>{wallet.error.message}</Text><Pressable onPress={() => wallet.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 불러오기</Text></Pressable></View>
      ) : (
        <>
          <StateCard tone="mint"><Text style={styles.metricLabel}>사용할 수 있는 돈</Text><Text style={styles.metricValue}>{wallet.data.balance} {identity?.currencyName ?? "비빔"}</Text></StateCard>
          <StateCard title="거래 내역">
            {wallet.data.recentTransactions.length === 0 ? <Text style={styles.helper}>아직 거래 내역이 없어요.</Text> : wallet.data.recentTransactions.map((transaction) => (
              <View key={transaction.resourceId} style={styles.transaction}>
                <View style={styles.transactionCopy}>
                  <Text style={styles.transactionTitle}>{transaction.label}</Text>
                  <Text style={styles.transactionDate}>{new Date(transaction.occurredAt).toLocaleDateString("ko-KR")}</Text>
                </View>
                <Text style={[styles.amount, transaction.amount < 0 ? styles.negative : styles.positive]}>{transaction.amount > 0 ? "+" : ""}{transaction.amount} {identity?.currencyName ?? "비빔"}</Text>
              </View>
            ))}
          </StateCard>
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { minHeight: 320, alignItems: "center", justifyContent: "center", gap: 14 },
  helper: { color: colors.muted600, fontSize: 15 },
  error: { color: colors.red500, fontSize: 16, fontWeight: "800", textAlign: "center" },
  retry: { paddingHorizontal: 20, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.green500 },
  retryText: { color: colors.white, fontWeight: "900" },
  metricLabel: { color: colors.muted600, fontSize: 15, fontWeight: "700" },
  metricValue: { color: colors.green500, fontSize: 38, fontWeight: "900" },
  transaction: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", minHeight: 68, paddingHorizontal: 4, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  transactionCopy: { gap: 4 },
  transactionTitle: { color: colors.ink900, fontSize: 16, fontWeight: "800" },
  transactionDate: { color: colors.muted600, fontSize: 12 },
  amount: { fontSize: 17, fontWeight: "900" },
  positive: { color: colors.green500 },
  negative: { color: colors.red500 }
});
