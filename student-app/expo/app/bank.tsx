import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { applyLoan, fetchFinanceAccounts, fetchFinancialProducts, fetchLoanOverview, subscribeDeposit, subscribeSavings, type FinancialProduct } from "@/features/student/api";
import { colors } from "@/theme/tokens";

type BankTab = "ACCOUNT" | "DEPOSIT" | "SAVINGS" | "LOAN";

export default function BankScreen() {
  const { identity } = useAuth();
  const { access } = useClassroom();
  const queryClient = useQueryClient();
  const products = useQuery({ queryKey: ["financial-products", identity?.classroomId], queryFn: () => fetchFinancialProducts(identity!.classroomId), enabled: Boolean(identity) });
  const accounts = useQuery({ queryKey: ["finance", identity?.classroomId], queryFn: () => fetchFinanceAccounts(identity!.classroomId), enabled: Boolean(identity) });
  const loan = useQuery({ queryKey: ["loan-overview", identity?.classroomId], queryFn: () => fetchLoanOverview(identity!.classroomId), enabled: Boolean(identity && access?.activeCodes.has("LOAN")) });
  const subscribe = useMutation({
    mutationFn: ({ product, amount }: { product: FinancialProduct; amount: number }) => (
      product.productType === "SAVINGS"
        ? subscribeSavings(identity!.classroomId, product, amount)
        : subscribeDeposit(identity!.classroomId, product, amount)
    ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["finance", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] });
    }
  });
  const applyLoanMutation = useMutation({
    mutationFn: ({ amount, installments }: { amount: number; installments: number }) => applyLoan(identity!.classroomId, amount, installments),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["loan-overview", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] });
    }
  });
  const availableTabs = useMemo(() => {
    const codes = access?.activeCodes ?? new Set<string>();
    return [
      { id: "ACCOUNT" as const, label: "내 통장" },
      ...(codes.has("DEPOSIT") ? [{ id: "DEPOSIT" as const, label: "예금" }] : []),
      ...(codes.has("SAVINGS") ? [{ id: "SAVINGS" as const, label: "적금" }] : []),
      ...(codes.has("LOAN") ? [{ id: "LOAN" as const, label: "대출" }] : [])
    ];
  }, [access?.activeCodes]);
  const [selected, setSelected] = useState<BankTab>("ACCOUNT");
  const [selectedProduct, setSelectedProduct] = useState<FinancialProduct | null>(null);
  const [loanAmount, setLoanAmount] = useState("100");
  const activeTab = availableTabs.some((tab) => tab.id === selected) ? selected : availableTabs[0]?.id;
  const activeProducts = products.data?.filter((product) => (
    ["PUBLISHED", "ACTIVE"].includes(product.status)
      && product.productType === activeTab
  )) ?? [];
  const hasAccounts = Boolean(
    accounts.data && (accounts.data.depositAccounts.length > 0 || accounts.data.savingsAccounts.length > 0)
  );
  const requestedLoanAmount = Number(loanAmount);
  const canSubmitLoan = Boolean(loan.data?.canApply && Number.isInteger(requestedLoanAmount) && requestedLoanAmount >= loan.data.installmentCount && requestedLoanAmount <= loan.data.availableLimit);
  const productTermLabel = (product: FinancialProduct) => product.productType === "SAVINGS"
    ? `${product.termUnits ?? product.installmentCount ?? "-"}주`
    : `${product.termUnits ?? "-"}일`;
  const expectedMaturity = (product: FinancialProduct) => {
    if (!product.termUnits) return "상품 조건 확인 중";
    const date = new Date();
    date.setDate(date.getDate() + (product.productType === "SAVINGS" ? product.termUnits * 7 : product.termUnits));
    return date.toLocaleDateString("ko-KR");
  };

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
          {accounts.isPending ? <ActivityIndicator color={colors.green500} /> : !hasAccounts ? <Text style={styles.emptyText}>아직 가입한 통장이 없어요.</Text> : accounts.data?.depositAccounts.map((account, index) => (
            <PassbookCover key={account.resourceId} title={account.label} subtitle={account.label.includes("적금") ? "납입 내역을 확인해요" : "만기까지 안전하게 보관해요"} color={account.label.includes("적금") ? "#D89A57" : "#6FAE83"}>
              <View style={styles.accountDetail}><Text style={styles.accountDetailLabel}>{account.label.includes("적금") ? "지금까지 납입한 금액" : "만기 예상 금액"}</Text><Text style={styles.accountDetailValue}>{account.principalAmount.toLocaleString("ko-KR")}비빔</Text></View>
              <View style={styles.accountSchedule}><Text>{account.label.includes("적금") ? "1회차 납입 완료" : "만기일"}</Text><Text>{account.label.includes("적금") ? "다음 납입을 기다리고 있어요" : account.maturesAt || "만기일을 확인 중이에요"}</Text></View>
              <Text style={styles.passbookNumber}>통장 {String(index + 1).padStart(2, "0")}</Text>
            </PassbookCover>
          ))}
          {accounts.data?.savingsAccounts.map((account, index) => (
            <PassbookCover key={account.resourceId} title={account.label} subtitle={`${account.paidInstallmentCount}/${account.installmentCount}회 납입 완료`} color="#D89A57">
              <View style={styles.accountDetail}><Text style={styles.accountDetailLabel}>지금까지 납입한 금액</Text><Text style={styles.accountDetailValue}>{account.principalBalance.toLocaleString("ko-KR")}비빔</Text></View>
              <View style={styles.accountSchedule}><Text>다음 회차 납입 금액</Text><Text>{account.installmentAmount.toLocaleString("ko-KR")}비빔</Text></View>
              <Text style={styles.passbookNumber}>적금 통장 {String(index + 1).padStart(2, "0")}</Text>
            </PassbookCover>
          ))}
        </View>
      ) : activeTab === "LOAN" ? (
        <PassbookCover title="생활 대출" subtitle="가입한 대출의 상환 일정과 이자를 확인해요" color="#78A8C7" numberLabel="대출 상품">
          <Text style={styles.loanProductBadge}>대출 상품</Text>
          {loan.isPending ? <ActivityIndicator color={colors.green500} /> : loan.data ? <>
            <View style={styles.loanGrid}>
              <View style={styles.loanMetric}><Text style={styles.accountDetailLabel}>내 신용점수</Text><Text style={styles.accountDetailValue}>{loan.data.creditScore}점</Text><Text style={styles.loanMetricDetail}>{loan.data.creditLevel}</Text></View>
              <View style={styles.loanMetric}><Text style={styles.accountDetailLabel}>대출 가능 금액</Text><Text style={styles.accountDetailValue}>{loan.data.availableLimit.toLocaleString("ko-KR")}</Text><Text style={styles.loanMetricDetail}>전체 금리 {(loan.data.interestRateBps / 100).toFixed(2)}%</Text></View>
            </View>
            <Text style={styles.passbookNotice}>{loan.data.termUnits / 7}주 동안 {loan.data.installmentCount}회 나누어 상환해요.</Text>
            {loan.data.pendingApplicationStatus ? <Text style={styles.pendingNotice}>대출 신청이 접수되었어요. 선생님 승인을 기다려요.</Text> : <View style={styles.loanApplyRow}>
              <TextInput accessibilityLabel="대출 신청 금액" value={loanAmount} onChangeText={setLoanAmount} keyboardType="number-pad" style={styles.loanAmountInput} />
              <Pressable disabled={!canSubmitLoan || applyLoanMutation.isPending} onPress={() => applyLoanMutation.mutate({ amount: requestedLoanAmount, installments: loan.data.installmentCount })} style={[styles.passbookButton, (!canSubmitLoan || applyLoanMutation.isPending) && styles.disabled]}><Text style={styles.passbookButtonText}>{applyLoanMutation.isPending ? "신청 중" : "대출 신청"}</Text></Pressable>
            </View>}
            {!loan.data.canApply && !loan.data.pendingApplicationStatus ? <Text style={styles.pendingNotice}>현재는 대출을 신청할 수 없어요. 신용점수와 기존 대출을 확인해 주세요.</Text> : null}
            {loan.data.loans.map((item) => <View key={item.loanId} style={styles.loanRow}><Text style={styles.accountDetailLabel}>{item.status === "OVERDUE" ? "연체" : item.status === "REPAID" ? "상환 완료" : "상환 중"}</Text><Text style={styles.loanMetricDetail}>가입 금액 {item.principalAmount.toLocaleString("ko-KR")} · 남은 원금 {item.outstandingPrincipal.toLocaleString("ko-KR")}</Text><Text style={styles.loanMetricDetail}>예상 이자 {item.outstandingInterest.toLocaleString("ko-KR")} · 만기 {item.maturesAt || "확인 중"}</Text></View>)}
          </> : <Text style={styles.passbookNotice}>대출 정보를 불러오지 못했어요.</Text>}
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
                  onPress={() => setSelectedProduct(product)}
                  style={styles.passbookButton}
                >
                  <Text style={styles.passbookButtonText}>정보 확인하기</Text>
                </Pressable>
              </View>
            ))}
          {selectedProduct && selectedProduct.productType === activeTab ? (
            <View style={styles.productDetail}>
              <View style={styles.productDetailHeading}>
                <View><Text style={styles.productDetailTitle}>{selectedProduct.label}</Text><Text style={styles.productRate}>연 {(selectedProduct.interestRateBps / 100).toFixed(1)}%</Text></View>
                <Pressable onPress={() => setSelectedProduct(null)}><Text style={styles.detailClose}>닫기</Text></Pressable>
              </View>
              <View style={styles.productDetailGrid}>
                <Metric label="이율" value={`연 ${(selectedProduct.interestRateBps / 100).toFixed(1)}%`} />
                <Metric label="가입 기간" value={productTermLabel(selectedProduct)} />
                <Metric label="예상 만기일" value={expectedMaturity(selectedProduct)} />
                <Metric label="가입 최소 금액" value={`${selectedProduct.minimumAmount ?? "확인 중"} ${identity?.currencyName ?? "비빔"}`} />
                <Metric label="가입 최대 금액" value={`${selectedProduct.maximumAmount ?? "확인 중"} ${identity?.currencyName ?? "비빔"}`} />
                {selectedProduct.productType === "SAVINGS" ? <Metric label="회차별 납입액" value={`${selectedProduct.installmentAmount ?? "확인 중"} ${identity?.currencyName ?? "비빔"}`} /> : null}
              </View>
              <Pressable
                disabled={subscribe.isPending || (selectedProduct.productType === "SAVINGS" && !selectedProduct.installmentAmount)}
                onPress={() => subscribe.mutate({ product: selectedProduct, amount: selectedProduct.productType === "SAVINGS" ? selectedProduct.installmentAmount! : selectedProduct.minimumAmount ?? 100 })}
                style={[styles.passbookButton, (subscribe.isPending || (selectedProduct.productType === "SAVINGS" && !selectedProduct.installmentAmount)) && styles.disabled]}
              >
                <Text style={styles.passbookButtonText}>{subscribe.isPending ? "가입 처리 중" : "이 상품 가입하기"}</Text>
              </Pressable>
            </View>
          ) : null}
        </View>
      )}
      {products.isError ? <Text style={styles.error}>{products.error.message}</Text> : null}
      {accounts.isError ? <Text style={styles.error}>{accounts.error.message}</Text> : null}
      {subscribe.isError ? <Text style={styles.error}>{subscribe.error.message}</Text> : null}
      {loan.isError ? <Text style={styles.error}>{loan.error.message}</Text> : null}
      {applyLoanMutation.isError ? <Text style={styles.error}>{applyLoanMutation.error.message}</Text> : null}
    </Screen>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <View style={styles.productMetric}><Text style={styles.productMetricLabel}>{label}</Text><Text style={styles.productMetricValue}>{value}</Text></View>;
}

function PassbookCover({ title, subtitle, color, children, numberLabel = "통장 01" }: { title: string; subtitle: string; color: string; children: ReactNode; numberLabel?: string }) {
  return (
    <View style={[styles.passbook, { backgroundColor: color }]}>
      <View style={styles.passbookSpine} />
      <View style={styles.passbookTop}>
        <Text style={styles.passbookLogo}>이코은행</Text>
        <Text style={styles.passbookNumber}>{numberLabel}</Text>
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
  productDetail: { gap: 16, marginTop: 18, padding: 18, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 12, backgroundColor: colors.white },
  productDetailHeading: { flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between", gap: 16 },
  productDetailTitle: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  detailClose: { color: colors.muted600, fontSize: 14, fontWeight: "800" },
  productDetailGrid: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  productMetric: { width: "48%", gap: 4, padding: 11, borderRadius: 8, backgroundColor: colors.sky100 },
  productMetricLabel: { color: colors.muted600, fontSize: 12, fontWeight: "800" },
  productMetricValue: { color: colors.ink900, fontSize: 14, fontWeight: "900" },
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
  loanGrid: { flexDirection: "row", gap: 12 },
  loanMetric: { flex: 1, gap: 4, padding: 12, borderWidth: 1, borderColor: "#E8E2D2", borderRadius: 6 },
  loanMetricDetail: { color: colors.muted600, fontSize: 13, fontWeight: "700" },
  loanApplyRow: { flexDirection: "row", alignItems: "center", gap: 10 },
  loanAmountInput: { flex: 1, minHeight: 42, paddingHorizontal: 12, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 6, color: colors.ink900, fontSize: 16, fontWeight: "800" },
  pendingNotice: { color: colors.green500, fontSize: 14, fontWeight: "800" },
  loanRow: { gap: 4, paddingTop: 10, borderTopWidth: 1, borderTopColor: "#E8E2D2" },
  loanProductBadge: { alignSelf: "flex-start", paddingHorizontal: 9, paddingVertical: 5, borderRadius: 999, backgroundColor: "#E5F0F6", color: "#35647C", fontSize: 12, fontWeight: "900" },
  emptyText: { paddingVertical: 20, borderTopWidth: 1, borderTopColor: colors.lineSoft, color: colors.muted600, fontSize: 15, fontWeight: "700" },
  error: { paddingTop: 12, color: colors.red500, fontWeight: "700" }
});
