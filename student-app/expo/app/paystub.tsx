import { useQuery } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchPayslips, type Payslip } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function PaystubScreen() {
  const { identity } = useAuth();
  const currency = identity?.currencyName ?? "비빔";
  const payslips = useQuery({
    queryKey: ["payslips", identity?.classroomId],
    queryFn: () => fetchPayslips(identity!.classroomId),
    enabled: Boolean(identity?.classroomId),
    staleTime: 10_000,
    refetchInterval: 10_000
  });

  return (
    <Screen title="월급명세서" right={<BackButton />}>
      <StateCard tone="sky">
        <Text style={styles.lead}>받은 월급과 세금이 어떻게 계산됐는지 확인해요</Text>
        <Text style={styles.description}>소득세는 우리 반 공동기금으로 모이고, 실수령액만 내 지갑에 들어와요.</Text>
      </StateCard>

      {payslips.isPending ? (
        <View style={styles.state}><ActivityIndicator color={colors.green500} size="large" /><Text style={styles.description}>월급명세서를 불러오고 있어요.</Text></View>
      ) : payslips.isError ? (
        <View style={styles.state}>
          <Text style={styles.error}>{payslips.error.message}</Text>
          <Pressable accessibilityRole="button" onPress={() => payslips.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 불러오기</Text></Pressable>
        </View>
      ) : payslips.data.length === 0 ? (
        <StateCard title="아직 발급된 월급명세서가 없어요">
          <Text style={styles.description}>선생님이 월급을 지급하면 총급여·공제·세금·실수령액이 여기에 기록돼요.</Text>
        </StateCard>
      ) : (
        <View style={styles.list}>
          {payslips.data.map((payslip) => <PayslipReceipt currency={currency} key={payslip.resourceId} payslip={payslip} />)}
        </View>
      )}
    </Screen>
  );
}

function PayslipReceipt({ currency, payslip }: { currency: string; payslip: Payslip }) {
  return (
    <View accessibilityLabel={`${payslip.periodKey} 월급명세서, 실수령액 ${payslip.netAmount} ${currency}`} style={styles.receipt}>
      <View style={styles.receiptHeader}>
        <View style={styles.receiptHeading}>
          <Text style={styles.period}>{payslip.periodKey}</Text>
          <Text style={styles.role}>{payslip.roleName}</Text>
          <Text style={styles.date}>{new Date(payslip.issuedAt).toLocaleString("ko-KR")}</Text>
        </View>
        <View style={styles.issuedBadge}><Text style={styles.issuedBadgeText}>지급 완료</Text></View>
      </View>
      <View style={styles.rows}>
        <AmountRow amount={payslip.grossAmount} currency={currency} label="총급여" />
        <AmountRow amount={-payslip.deductionAmount} currency={currency} label="기타 공제" muted={payslip.deductionAmount === 0} />
        <AmountRow amount={-payslip.taxAmount} currency={currency} label="소득세 · 공동기금" muted={payslip.taxAmount === 0} tax />
      </View>
      <View style={styles.total}>
        <View><Text style={styles.totalLabel}>내 지갑에 들어온 금액</Text><Text style={styles.formula}>{payslip.grossAmount.toLocaleString("ko-KR")} - {(payslip.deductionAmount + payslip.taxAmount).toLocaleString("ko-KR")}</Text></View>
        <Text style={styles.totalAmount}>{payslip.netAmount.toLocaleString("ko-KR")} {currency}</Text>
      </View>
    </View>
  );
}

function AmountRow({ amount, currency, label, muted = false, tax = false }: { amount: number; currency: string; label: string; muted?: boolean; tax?: boolean }) {
  return (
    <View style={styles.row}>
      <Text style={[styles.rowLabel, tax && styles.taxLabel]}>{label}</Text>
      <Text style={[styles.rowAmount, amount < 0 && styles.negative, muted && styles.mutedAmount]}>{amount > 0 ? "+" : ""}{amount.toLocaleString("ko-KR")} {currency}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  lead: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 15, lineHeight: 22 },
  state: { minHeight: 280, alignItems: "center", justifyContent: "center", gap: 14 },
  error: { color: colors.red500, fontSize: 16, fontWeight: "800", textAlign: "center" },
  retry: { paddingHorizontal: 20, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.green500 },
  retryText: { color: colors.white, fontWeight: "900" },
  list: { gap: 20 },
  receipt: { overflow: "hidden", borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.card, backgroundColor: colors.white, shadowColor: colors.ink900, shadowOffset: { width: 0, height: 6 }, shadowOpacity: 0.08, shadowRadius: 14, elevation: 3 },
  receiptHeader: { minHeight: 108, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 16, padding: 22, backgroundColor: "#EAF8F3" },
  receiptHeading: { flex: 1, gap: 3 },
  period: { color: colors.green700, fontSize: 14, fontWeight: "900" },
  role: { color: colors.ink900, fontSize: 23, fontWeight: "900" },
  date: { color: colors.muted600, fontSize: 12 },
  issuedBadge: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: radii.chip, backgroundColor: colors.green500 },
  issuedBadgeText: { color: colors.white, fontSize: 13, fontWeight: "900" },
  rows: { paddingHorizontal: 22, paddingVertical: 8 },
  row: { minHeight: 58, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 16, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  rowLabel: { flex: 1, color: colors.ink900, fontSize: 16, fontWeight: "800" },
  taxLabel: { color: colors.purple500 },
  rowAmount: { color: colors.green700, fontSize: 17, fontWeight: "900" },
  negative: { color: colors.red500 },
  mutedAmount: { color: colors.muted600 },
  total: { minHeight: 94, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 18, paddingHorizontal: 22, paddingVertical: 18, backgroundColor: "#F7FAFC" },
  totalLabel: { color: colors.ink900, fontSize: 15, fontWeight: "900" },
  formula: { marginTop: 4, color: colors.muted600, fontSize: 12 },
  totalAmount: { flexShrink: 1, color: colors.green700, fontSize: 25, fontWeight: "900", textAlign: "right" }
});
