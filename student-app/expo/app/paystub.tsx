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
  const wallet = useQuery({ queryKey: ["wallet", identity?.classroomId], queryFn: () => fetchWallet(identity!.classroomId), enabled: Boolean(identity) });
  const rows = wallet.data?.recentTransactions.filter((transaction) => /급여|수당|세금|소득세/.test(transaction.label)) ?? [];

  return (
    <Screen title="월급명세서" right={<BackButton />}>
      <StateCard title="받은 돈과 세금">
        {wallet.isPending ? <ActivityIndicator color={colors.green500} /> : wallet.isError ? <Text style={styles.error}>{wallet.error.message}</Text> : rows.length === 0 ? <Text style={styles.helper}>아직 확인할 급여 내역이 없어요.</Text> : rows.map((row) => (
          <View key={row.resourceId} style={styles.row}><Text style={styles.label}>{row.label}</Text><Text style={[styles.amount, row.amount < 0 && styles.negative]}>{row.amount > 0 ? "+" : ""}{row.amount} {identity?.currencyName ?? "비빔"}</Text></View>
        ))}
      </StateCard>
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  label: { color: colors.ink900, fontSize: 16, fontWeight: "700" },
  amount: { color: colors.green500, fontSize: 16, fontWeight: "900" },
  negative: { color: colors.red500 },
  helper: { color: colors.muted600, fontSize: 15 },
  error: { color: colors.red500, fontWeight: "800" }
});
