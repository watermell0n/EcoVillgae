import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchFinanceAccounts, fetchFinancialProducts, subscribeDeposit, SUBSCRIBABLE_PRODUCT_STATUS, type FinancialProduct } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function BankScreen() {
  const { identity } = useAuth();
  const queryClient = useQueryClient();
  const products = useQuery({ queryKey: ["financial-products", identity?.classroomId], queryFn: () => fetchFinancialProducts(identity!.classroomId), enabled: Boolean(identity) });
  const accounts = useQuery({ queryKey: ["finance", identity?.classroomId], queryFn: () => fetchFinanceAccounts(identity!.classroomId), enabled: Boolean(identity) });
  const subscribe = useMutation({
    mutationFn: ({ product, amount }: { product: FinancialProduct; amount: number }) => subscribeDeposit(identity!.classroomId, product, amount),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["finance", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] });
    }
  });
  const activeProducts = products.data?.filter((product) => product.status === SUBSCRIBABLE_PRODUCT_STATUS && product.productType === "DEPOSIT") ?? [];

  return (
    <Screen title="이코 은행" right={<BackButton />}>
      {products.isPending ? <ActivityIndicator color={colors.purple500} /> : products.isError ? <Text style={styles.error}>{products.error.message}</Text> : (
        <View style={styles.grid}>
          {activeProducts.length === 0 ? <Text style={styles.description}>지금 가입할 수 있는 예금 상품이 없어요.</Text> : activeProducts.map((product) => (
            <StateCard key={product.resourceId} title={product.label} tone="mint">
              <Text style={styles.rate}>이자 {(product.interestRateBps / 100).toFixed(1)}%</Text>
              <Pressable disabled={subscribe.isPending} onPress={() => subscribe.mutate({ product, amount: 100 })} style={[styles.action, subscribe.isPending && styles.disabled]}>
                <Text style={styles.actionText}>{subscribe.isPending ? "처리 중" : "100 비빔 가입하기"}</Text>
              </Pressable>
            </StateCard>
          ))}
        </View>
      )}
      <StateCard title="내 예금">
        {accounts.isPending ? <ActivityIndicator color={colors.purple500} /> : accounts.data?.depositAccounts.length === 0 ? <Text style={styles.description}>아직 가입한 예금이 없어요.</Text> : accounts.data?.depositAccounts.map((account) => (
          <View key={account.resourceId} style={styles.account}><Text style={styles.accountTitle}>{account.label}</Text><Text style={styles.accountValue}>{account.principalAmount} 비빔 · {account.status}</Text></View>
        ))}
        {accounts.isError ? <Text style={styles.error}>{accounts.error.message}</Text> : null}
        {subscribe.isError ? <Text style={styles.error}>{subscribe.error.message}</Text> : null}
      </StateCard>
    </Screen>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 16 },
  rate: { color: colors.purple500, fontSize: 18, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 15 },
  action: { alignSelf: "flex-start", paddingHorizontal: 18, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.purple500 },
  actionText: { color: colors.white, fontWeight: "900" },
  disabled: { opacity: 0.45 },
  account: { flexDirection: "row", justifyContent: "space-between", padding: 14, borderRadius: radii.control, backgroundColor: colors.washBlue },
  accountTitle: { color: colors.ink900, fontWeight: "800" },
  accountValue: { color: colors.muted600, fontWeight: "700" },
  error: { color: colors.red500, fontWeight: "700" }
});
