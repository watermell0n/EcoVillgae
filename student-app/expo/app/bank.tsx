import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { fetchFinanceAccounts, fetchFinancialProducts, subscribeDeposit, type FinancialProduct } from "@/features/student/api";
import { colors } from "@/theme/tokens";

type BankTab = "ACCOUNT" | "DEPOSIT" | "SAVINGS" | "LOAN";

export default function BankScreen() {
  const { identity } = useAuth();
  const { access } = useClassroom();
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
  const availableTabs = useMemo(() => {
    const codes = access?.activeCodes ?? new Set<string>();
    return [
      { id: "ACCOUNT" as const, label: "내 통장" },
      ...(codes.has("DEPOSIT") ? [{ id: "DEPOSIT" as const, label: "예금" }] : []),
      ...(codes.has("SAVINGS") || codes.has("DEPOSIT") ? [{ id: "SAVINGS" as const, label: "적금" }] : []),
      ...(codes.has("LOAN") ? [{ id: "LOAN" as const, label: "대출" }] : [])
    ];
  }, [access?.activeCodes]);
  const [selected, setSelected] = useState<BankTab>("ACCOUNT");
  const activeTab = availableTabs.some((tab) => tab.id === selected) ? selected : availableTabs[0]?.id;
  const activeProducts = products.data?.filter((product) => product.status === "ACTIVE" && product.productType === activeTab) ?? [];

  return (
    <Screen title="이코 은행" right={<BackButton />}>
      <View accessibilityRole="tablist" style={styles.tabs}>
        {availableTabs.map((tab) => (
          <Pressable key={tab.id} accessibilityRole="tab" accessibilityState={{ selected: activeTab === tab.id }} onPress={() => setSelected(tab.id)} style={[styles.tab, activeTab === tab.id && styles.tabActive]}>
            <Text style={[styles.tabText, activeTab === tab.id && styles.tabTextActive]}>{tab.label}</Text>
          </Pressable>
        ))}
      </View>
      {activeTab === "ACCOUNT" ? (
        <View style={styles.accountsSection}>
          {accounts.isPending ? <ActivityIndicator color={colors.green500} /> : accounts.data?.depositAccounts.length === 0 ? <Text style={styles.emptyText}>아직 가입한 통장이 없어요.</Text> : accounts.data?.depositAccounts.map((account, index) => (
            <PassbookCover key={account.resourceId} title={account.label} subtitle={account.label.includes("적금") ? "납입 내역을 확인해요" : "만기까지 안전하게 보관해요"} color={account.label.includes("적금") ? "#D89A57" : "#6FAE83"}>
              <View style={styles.accountDetail}><Text style={styles.accountDetailLabel}>{account.label.includes("적금") ? "지금까지 납입한 금액" : "만기 예상 금액"}</Text><Text style={styles.accountDetailValue}>{account.principalAmount.toLocaleString("ko-KR")}비빔</Text></View>
              <View style={styles.accountSchedule}><Text>{account.label.includes("적금") ? "1회차 납입 완료" : "만기일"}</Text><Text>{account.label.includes("적금") ? "다음 납입을 기다리고 있어요" : account.maturesAt || "만기일을 확인 중이에요"}</Text></View>
              <Text style={styles.passbookNumber}>통장 {String(index + 1).padStart(2, "0")}</Text>
            </PassbookCover>
          ))}
        </View>
      ) : activeTab === "LOAN" ? (
        <PassbookCover title="비빔 생활 통장" subtitle="필요한 만큼 빌리고 계획대로 갚아요" color="#78A8C7">
          <Text style={styles.passbookNotice}>대출 신청은 선생님이 정한 한도와 상환 계획을 확인한 뒤 열려요.</Text>
          <Pressable style={styles.passbookButton}><Text style={styles.passbookButtonText}>대출 가능 금액 확인</Text></Pressable>
        </PassbookCover>
      ) : (
        <View style={styles.productArea}>
          <Text style={styles.productSectionTitle}>{activeTab === "SAVINGS" ? "적금 상품" : "예금 상품"}</Text>
            {products.isPending ? <ActivityIndicator color={colors.green500} /> : activeProducts.length === 0 ? <Text style={styles.passbookNotice}>지금 가입할 수 있는 상품이 없어요.</Text> : activeProducts.map((product) => (
              <View key={product.resourceId} style={styles.productLine}>
                <View style={styles.productCopy}>
                  <Text style={styles.productTitle}>{product.label}</Text>
                  <Text style={styles.productRate}>연 {(product.interestRateBps / 100).toFixed(1)}%</Text>
                </View>
                <Pressable
                  disabled={subscribe.isPending || product.productType !== "DEPOSIT"}
                  onPress={() => subscribe.mutate({ product, amount: 100 })}
                  style={[styles.passbookButton, (subscribe.isPending || product.productType !== "DEPOSIT") && styles.disabled]}
                >
                  <Text style={styles.passbookButtonText}>{product.productType === "DEPOSIT" ? "가입하기" : "가입 준비 중"}</Text>
                </Pressable>
              </View>
            ))}
        </View>
      )}
      {products.isError ? <Text style={styles.error}>{products.error.message}</Text> : null}
      {accounts.isError ? <Text style={styles.error}>{accounts.error.message}</Text> : null}
      {subscribe.isError ? <Text style={styles.error}>{subscribe.error.message}</Text> : null}
    </Screen>
  );
}

function PassbookCover({ title, subtitle, color, children }: { title: string; subtitle: string; color: string; children: ReactNode }) {
  return (
    <View style={[styles.passbook, { backgroundColor: color }]}>
      <View style={styles.passbookSpine} />
      <View style={styles.passbookTop}>
        <Text style={styles.passbookLogo}>이코은행</Text>
        <Text style={styles.passbookNumber}>통장 01</Text>
      </View>
      <Text style={styles.passbookTitle}>{title}</Text>
      <Text style={styles.passbookSubtitle}>{subtitle}</Text>
      <View style={styles.passbookPaper}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  tabs: { flexDirection: "row", gap: 4, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  tab: { minWidth: 104, paddingHorizontal: 16, paddingVertical: 13, borderBottomWidth: 3, borderBottomColor: "transparent" },
  tabActive: { borderBottomColor: colors.green500 },
  tabText: { color: colors.muted600, fontSize: 16, fontWeight: "800", textAlign: "center" },
  tabTextActive: { color: colors.ink900 },
  productArea: { width: "100%", maxWidth: 900, borderTopWidth: 1, borderTopColor: colors.lineSoft },
  productSectionTitle: { paddingTop: 20, color: colors.ink900, fontSize: 22, fontWeight: "900" },
  passbook: { position: "relative", minHeight: 350, paddingLeft: 52, paddingRight: 30, paddingTop: 26, paddingBottom: 30, borderRadius: 6, overflow: "hidden", shadowColor: colors.ink900, shadowOffset: { width: 0, height: 12 }, shadowOpacity: 0.12, shadowRadius: 20, elevation: 8 },
  passbookSpine: { position: "absolute", left: 0, top: 0, bottom: 0, width: 24, backgroundColor: "rgba(20,40,50,0.16)" },
  passbookTop: { flexDirection: "row", justifyContent: "space-between", marginBottom: 28 },
  passbookLogo: { color: colors.white, fontSize: 15, fontWeight: "900" },
  passbookNumber: { color: "rgba(255,255,255,0.8)", fontSize: 12, fontWeight: "800" },
  passbookTitle: { color: colors.white, fontSize: 28, fontWeight: "900" },
  passbookSubtitle: { marginTop: 7, color: "rgba(255,255,255,0.84)", fontSize: 15, fontWeight: "700" },
  passbookPaper: { marginTop: 28, gap: 10, padding: 18, borderRadius: 3, backgroundColor: "#FFFEF7" },
  passbookNotice: { color: colors.muted600, fontSize: 15, lineHeight: 23, fontWeight: "700" },
  productLine: { minHeight: 86, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 18, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  productCopy: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 15 },
  productTitle: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  productRate: { color: colors.green500, fontSize: 15, fontWeight: "900" },
  passbookButton: { minHeight: 42, alignItems: "center", justifyContent: "center", paddingHorizontal: 18, borderRadius: 6, backgroundColor: colors.ink900 },
  passbookButtonText: { color: colors.white, fontSize: 14, fontWeight: "900" },
  disabled: { opacity: 0.45 },
  accountsSection: { width: "100%", maxWidth: 900, gap: 18, paddingTop: 6 },
  sectionTitle: { paddingBottom: 12, color: colors.ink900, fontSize: 21, fontWeight: "900" },
  accountLine: { minHeight: 70, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 20, borderTopWidth: 1, borderTopColor: colors.lineSoft },
  accountTitle: { color: colors.ink900, fontSize: 16, fontWeight: "900" },
  accountMeta: { marginTop: 4, color: colors.muted600, fontSize: 13, fontWeight: "700" },
  accountValue: { color: colors.green500, fontSize: 18, fontWeight: "900" },
  accountDetail: { gap: 4, paddingBottom: 12, borderBottomWidth: 1, borderBottomColor: "#E8E2D2" },
  accountDetailLabel: { color: colors.muted600, fontSize: 13, fontWeight: "800" },
  accountDetailValue: { color: colors.ink900, fontSize: 24, fontWeight: "900" },
  accountSchedule: { gap: 4 },
  emptyText: { paddingVertical: 20, borderTopWidth: 1, borderTopColor: colors.lineSoft, color: colors.muted600, fontSize: 15, fontWeight: "700" },
  error: { paddingTop: 12, color: colors.red500, fontWeight: "700" }
});
