import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchFinanceAccounts, subscribeDeposit } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

const products = [
  { title: "3일 예금", description: "오늘 넣으면 3일차에 이자를 받아요.", amount: 100, rate: "10%" },
  { title: "매일 적금", description: "매일 조금씩 모으는 연습이에요.", amount: 30, rate: "15%", disabled: true }
];

export default function BankScreen() {
  const { identity } = useAuth();
  const queryClient = useQueryClient();
  const accounts = useQuery({ queryKey: ["finance", identity?.classroomId], queryFn: () => fetchFinanceAccounts(identity!.classroomId), enabled: Boolean(identity) });
  const subscribe = useMutation({
    mutationFn: (amount: number) => subscribeDeposit(identity!.classroomId, amount),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["finance", identity?.classroomId] })
  });

  return (
    <Screen title="이코 은행" subtitle="SCR-STU-013·014 · 서버 처리 중에는 중복 가입을 막습니다." right={<BackButton />}>
      <View style={styles.grid}>
        {products.map((product) => (
          <StateCard key={product.title} title={product.title} tone="mint">
            <Text style={styles.rate}>{product.amount} 비빔부터 · 이자 {product.rate}</Text>
            <Text style={styles.description}>{product.description}</Text>
            <Pressable
              disabled={product.disabled || subscribe.isPending}
              onPress={() => subscribe.mutate(product.amount)}
              style={[styles.action, (product.disabled || subscribe.isPending) && styles.disabled]}
            >
              <Text style={styles.actionText}>{product.disabled ? "이관 예정" : subscribe.isPending ? "처리 중" : "가입하기"}</Text>
            </Pressable>
          </StateCard>
        ))}
      </View>
      <StateCard title="내 예금">
        {accounts.isPending ? <ActivityIndicator color={colors.purple500} /> : accounts.data?.depositAccounts.map((account) => (
          <View key={account.resourceId} style={styles.account}><Text style={styles.accountTitle}>{account.label}</Text><Text style={styles.accountValue}>{account.principalAmount} 비빔 · {account.status}</Text></View>
        ))}
        {subscribe.isError ? <Text style={styles.error}>{subscribe.error.message}</Text> : null}
      </StateCard>
    </Screen>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: "row", gap: 16 },
  rate: { color: colors.purple500, fontSize: 18, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 14 },
  action: { alignSelf: "flex-start", paddingHorizontal: 18, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.purple500 },
  actionText: { color: colors.white, fontWeight: "900" },
  disabled: { opacity: 0.45 },
  account: { flexDirection: "row", justifyContent: "space-between", padding: 14, borderRadius: radii.control, backgroundColor: colors.washBlue },
  accountTitle: { color: colors.ink900, fontWeight: "800" },
  accountValue: { color: colors.muted600, fontWeight: "700" },
  error: { color: colors.red500, fontWeight: "700" }
});
