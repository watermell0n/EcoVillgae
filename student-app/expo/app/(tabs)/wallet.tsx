import { useQuery } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchWallet } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function WalletScreen() {
  const { identity } = useAuth();
  const currencyName = identity?.currencyName ?? "비빔";
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
          <View accessibilityLabel={`현재 잔액 ${wallet.data.balance} ${currencyName}`} style={styles.balanceCard}>
            <View style={styles.balanceHeading}>
              <View>
                <Text style={styles.balanceEyebrow}>내 지갑 잔액</Text>
                <Text style={styles.balanceHelper}>지금 바로 사용할 수 있는 돈이에요</Text>
              </View>
              <Text style={styles.walletIcon}>👛</Text>
            </View>
            <View style={styles.balanceRow}>
              <Text adjustsFontSizeToFit minimumFontScale={0.65} numberOfLines={1} style={styles.metricValue}>{wallet.data.balance.toLocaleString("ko-KR")}</Text>
              <Text style={styles.currencyName}>{currencyName}</Text>
            </View>
          </View>
          <StateCard title="거래 내역">
            {wallet.data.recentTransactions.length === 0 ? <Text style={styles.helper}>아직 거래 내역이 없어요.</Text> : wallet.data.recentTransactions.map((transaction) => (
              <View key={transaction.resourceId} style={styles.transaction}>
                <View style={styles.transactionCopy}>
                  <Text style={styles.transactionTitle}>{transaction.label}</Text>
                  <Text style={styles.transactionDate}>{new Date(transaction.occurredAt).toLocaleDateString("ko-KR")}</Text>
                </View>
                <Text style={[styles.amount, transaction.amount < 0 ? styles.negative : styles.positive]}>{transaction.amount > 0 ? "+" : ""}{transaction.amount.toLocaleString("ko-KR")} {currencyName}</Text>
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
  balanceCard: { minHeight: 220, justifyContent: "space-between", gap: 28, paddingHorizontal: 30, paddingVertical: 28, borderRadius: radii.card, backgroundColor: colors.green500, shadowColor: colors.ink900, shadowOffset: { width: 0, height: 10 }, shadowOpacity: 0.16, shadowRadius: 20, elevation: 7 },
  balanceHeading: { flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between", gap: 18 },
  balanceEyebrow: { color: colors.white, fontSize: 20, fontWeight: "900" },
  balanceHelper: { marginTop: 6, color: "rgba(255,255,255,0.78)", fontSize: 14, fontWeight: "700" },
  walletIcon: { fontSize: 38 },
  balanceRow: { flexDirection: "row", alignItems: "flex-end", gap: 12 },
  metricValue: { flexShrink: 1, color: colors.white, fontSize: 58, lineHeight: 66, fontWeight: "900", letterSpacing: -1.5 },
  currencyName: { paddingBottom: 8, color: colors.white, fontSize: 23, fontWeight: "900" },
  transaction: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", minHeight: 68, paddingHorizontal: 4, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  transactionCopy: { gap: 4 },
  transactionTitle: { color: colors.ink900, fontSize: 16, fontWeight: "800" },
  transactionDate: { color: colors.muted600, fontSize: 12 },
  amount: { fontSize: 17, fontWeight: "900" },
  positive: { color: colors.green500 },
  negative: { color: colors.red500 }
});
