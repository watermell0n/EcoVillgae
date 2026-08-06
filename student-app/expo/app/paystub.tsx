import { useQuery } from "@tanstack/react-query";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchWallet } from "@/features/student/api";
import { colors } from "@/theme/tokens";

export default function PaystubScreen() {
  const { identity } = useAuth();
  const wallet = useQuery({
    queryKey: ["wallet", identity?.classroomId],
    queryFn: () => fetchWallet(identity!.classroomId),
    enabled: Boolean(identity)
  });
  const rows = wallet.data?.recentTransactions.filter((transaction) => /급여|월급|수당|세금|공제/.test(transaction.label)) ?? [];
  const paystubs = rows.reduce<Record<string, typeof rows>>((grouped, row) => {
    const date = new Date(row.occurredAt).toLocaleDateString("ko-KR");
    (grouped[date] ??= []).push(row);
    return grouped;
  }, {});

  return (
    <Screen title="월급명세서" right={<BackButton />}>
      <StateCard title="월급 받은 날별 명세서">
        {wallet.isPending ? <ActivityIndicator color={colors.green500} /> : null}
        {wallet.isError ? <Text style={styles.error}>{wallet.error.message}</Text> : null}
        {!wallet.isPending && !wallet.isError && rows.length === 0 ? <Text style={styles.helper}>아직 확인할 급여 내역이 없어요.</Text> : null}
        {Object.entries(paystubs).map(([date, entries]) => {
          const gross = entries.filter((entry) => entry.amount > 0).reduce((total, entry) => total + entry.amount, 0);
          const tax = Math.abs(entries.filter((entry) => entry.amount < 0).reduce((total, entry) => total + entry.amount, 0));
          return (
            <View key={date} style={styles.paystub}>
              <Text style={styles.date}>{date} 지급</Text>
              {entries.map((row) => (
                <View key={row.resourceId} style={styles.row}>
                  <Text style={styles.label}>{row.label}</Text>
                  <Text style={[styles.amount, row.amount < 0 && styles.negative]}>{row.amount > 0 ? "+" : ""}{row.amount} {identity?.currencyName ?? "비빔"}</Text>
                </View>
              ))}
              <View style={styles.summary}>
                <Text style={styles.summaryLabel}>총 지급 {gross} · 소득세 {tax}</Text>
                <Text style={styles.net}>실수령 {gross - tax} {identity?.currencyName ?? "비빔"}</Text>
              </View>
            </View>
          );
        })}
      </StateCard>
    </Screen>
  );
}

const styles = StyleSheet.create({
  paystub: { gap: 2, paddingBottom: 18, marginBottom: 18, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  date: { color: colors.ink900, fontSize: 18, fontWeight: "900", paddingBottom: 6 },
  row: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  label: { color: colors.ink900, fontSize: 16, fontWeight: "700" },
  amount: { color: colors.green500, fontSize: 16, fontWeight: "900" },
  negative: { color: colors.red500 },
  summary: { paddingTop: 12, gap: 4, alignItems: "flex-end" },
  summaryLabel: { color: colors.muted600, fontSize: 13, fontWeight: "700" },
  net: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  helper: { color: colors.muted600, fontSize: 15 },
  error: { color: colors.red500, fontWeight: "800" }
});
