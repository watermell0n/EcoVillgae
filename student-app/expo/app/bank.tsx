import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { fetchFinanceAccounts, fetchFinancialProducts, subscribeDeposit, type FinancialProduct } from "@/features/student/api";
import { colors } from "@/theme/tokens";

type BankTab = "DEPOSIT" | "SAVINGS" | "LOAN";

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
      ...(codes.has("DEPOSIT") ? [{ id: "DEPOSIT" as const, label: "예금" }] : []),
      ...(codes.has("SAVINGS") || codes.has("DEPOSIT") ? [{ id: "SAVINGS" as const, label: "적금" }] : []),
      ...(codes.has("LOAN") ? [{ id: "LOAN" as const, label: "대출" }] : [])
    ];
  }, [access?.activeCodes]);
  const [selected, setSelected] = useState<BankTab>(availableTabs[0]?.id ?? "DEPOSIT");
  const activeTab = availableTabs.some((tab) => tab.id === selected) ? selected : availableTabs[0]?.id;
  const activeProducts = products.data?.filter((product) => product.status === "ACTIVE" && product.productType === activeTab) ?? [];

  return (
    <Screen title="이코 은행" right={<BackButton />}>
      <View style={styles.bankHeader}>
        <View>
          <Text style={styles.eyebrow}>내 돈을 계획하는 곳</Text>
          <Text style={styles.heading}>통장을 만들고 돈의 흐름을 살펴봐요</Text>
        </View>
        <Text style={styles.bankMark}>ECO BANK</Text>
      </View>
      <View accessibilityRole="tablist" style={styles.tabs}>
        {availableTabs.map((tab) => (
          <Pressable key={tab.id} accessibilityRole="tab" accessibilityState={{ selected: activeTab === tab.id }} onPress={() => setSelected(tab.id)} style={[styles.tab, activeTab === tab.id && styles.tabActive]}>
            <Text style={[styles.tabText, activeTab === tab.id && styles.tabTextActive]}>{tab.label}</Text>
          </Pressable>
        ))}
      </View>
      {activeTab === "LOAN" ? (
        <PassbookCover title="비빔 생활 통장" subtitle="필요한 만큼 빌리고 계획대로 갚아요" color="#78A8C7">
          <Text style={styles.passbookNotice}>대출 신청은 선생님이 정한 한도와 상환 계획을 확인한 뒤 열려요.</Text>
          <Pressable style={styles.passbookButton}><Text style={styles.passbookButtonText}>대출 가능 금액 확인</Text></Pressable>
        </PassbookCover>
      ) : (
        <View style={styles.productArea}>
          <PassbookCover
            title={activeTab === "SAVINGS" ? "차곡차곡 적금 통장" : "든든한 예금 통장"}
            subtitle={activeTab === "SAVINGS" ? "매일 정한 금액을 모아요" : "한 번 맡기고 만기까지 기다려요"}
            color={activeTab === "SAVINGS" ? "#E4A867" : "#6FAE83"}
          >
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
                  <Text style={styles.passbookButtonText}>{product.productType === "DEPOSIT" ? "100비빔 가입" : "상품 보기"}</Text>
                </Pressable>
              </View>
            ))}
          </PassbookCover>
        </View>
      )}
      <View style={styles.accountsSection}>
        <Text style={styles.sectionTitle}>내 통장</Text>
        {accounts.isPending ? <ActivityIndicator color={colors.green500} /> : accounts.data?.depositAccounts.length === 0 ? <Text style={styles.emptyText}>아직 만든 통장이 없어요.</Text> : accounts.data?.depositAccounts.map((account) => (
          <View key={account.resourceId} style={styles.accountLine}>
            <View><Text style={styles.accountTitle}>{account.label}</Text><Text style={styles.accountMeta}>{account.status}</Text></View>
            <Text style={styles.accountValue}>{account.principalAmount.toLocaleString("ko-KR")}비빔</Text>
          </View>
        ))}
        {products.isError ? <Text style={styles.error}>{products.error.message}</Text> : null}
        {accounts.isError ? <Text style={styles.error}>{accounts.error.message}</Text> : null}
        {subscribe.isError ? <Text style={styles.error}>{subscribe.error.message}</Text> : null}
      </View>
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
  bankHeader: { flexDirection: "row", alignItems: "flex-end", justifyContent: "space-between", gap: 20, maxWidth: 1040 },
  eyebrow: { marginBottom: 7, color: colors.green500, fontSize: 14, fontWeight: "900" },
  heading: { maxWidth: 700, color: colors.ink900, fontSize: 26, lineHeight: 36, fontWeight: "900" },
  bankMark: { color: colors.muted600, fontSize: 13, fontWeight: "900" },
  tabs: { flexDirection: "row", gap: 4, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  tab: { minWidth: 104, paddingHorizontal: 16, paddingVertical: 13, borderBottomWidth: 3, borderBottomColor: "transparent" },
  tabActive: { borderBottomColor: colors.green500 },
  tabText: { color: colors.muted600, fontSize: 16, fontWeight: "800", textAlign: "center" },
  tabTextActive: { color: colors.ink900 },
  productArea: { width: "100%", maxWidth: 900 },
  passbook: { position: "relative", minHeight: 350, paddingLeft: 52, paddingRight: 30, paddingTop: 26, paddingBottom: 30, borderRadius: 6, overflow: "hidden", shadowColor: colors.ink900, shadowOffset: { width: 0, height: 12 }, shadowOpacity: 0.12, shadowRadius: 20, elevation: 8 },
  passbookSpine: { position: "absolute", left: 0, top: 0, bottom: 0, width: 24, backgroundColor: "rgba(20,40,50,0.16)" },
  passbookTop: { flexDirection: "row", justifyContent: "space-between", marginBottom: 28 },
  passbookLogo: { color: colors.white, fontSize: 15, fontWeight: "900" },
  passbookNumber: { color: "rgba(255,255,255,0.8)", fontSize: 12, fontWeight: "800" },
  passbookTitle: { color: colors.white, fontSize: 28, fontWeight: "900" },
  passbookSubtitle: { marginTop: 7, color: "rgba(255,255,255,0.84)", fontSize: 15, fontWeight: "700" },
  passbookPaper: { marginTop: 28, gap: 10, padding: 18, borderRadius: 3, backgroundColor: "#FFFEF7" },
  passbookNotice: { color: colors.muted600, fontSize: 15, lineHeight: 23, fontWeight: "700" },
  productLine: { minHeight: 68, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 18, borderBottomWidth: 1, borderBottomColor: "#E8E2D2" },
  productCopy: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 15 },
  productTitle: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  productRate: { color: colors.green500, fontSize: 15, fontWeight: "900" },
  passbookButton: { minHeight: 42, alignItems: "center", justifyContent: "center", paddingHorizontal: 18, borderRadius: 6, backgroundColor: colors.ink900 },
  passbookButtonText: { color: colors.white, fontSize: 14, fontWeight: "900" },
  disabled: { opacity: 0.45 },
  accountsSection: { width: "100%", maxWidth: 900, gap: 0, paddingTop: 6 },
  sectionTitle: { paddingBottom: 12, color: colors.ink900, fontSize: 21, fontWeight: "900" },
  accountLine: { minHeight: 70, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 20, borderTopWidth: 1, borderTopColor: colors.lineSoft },
  accountTitle: { color: colors.ink900, fontSize: 16, fontWeight: "900" },
  accountMeta: { marginTop: 4, color: colors.muted600, fontSize: 13, fontWeight: "700" },
  accountValue: { color: colors.green500, fontSize: 18, fontWeight: "900" },
  emptyText: { paddingVertical: 20, borderTopWidth: 1, borderTopColor: colors.lineSoft, color: colors.muted600, fontSize: 15, fontWeight: "700" },
  error: { paddingTop: 12, color: colors.red500, fontWeight: "700" }
});
