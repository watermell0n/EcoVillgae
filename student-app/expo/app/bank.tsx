import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { ActivityIndicator, Alert, Platform, Pressable, StyleSheet, Text, TextInput, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { applyLoan, cancelLoanApplication, fetchFinanceAccounts, fetchFinancialProducts, fetchLoanOverview, repayLoan, subscribeDeposit, subscribeSavings, terminateDeposit, type DepositAccount, type FinancialProduct } from "@/features/student/api";
import { colors } from "@/theme/tokens";
import { ApiError } from "@/api/client";

type BankTab = "ACCOUNT" | "DEPOSIT" | "SAVINGS" | "LOAN";

function formatBankDate(value?: string) {
  if (!value) return "일정을 확인 중이에요";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "일정을 확인 중이에요";
  return date.toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric" });
}

function accountNumber(prefix: "예금" | "적금", resourceId: string) {
  const suffix = resourceId.replace(/[^a-zA-Z0-9]/g, "").slice(-6).toUpperCase();
  return `${prefix} ${suffix || "000000"}`;
}

export default function BankScreen() {
  const { identity } = useAuth();
  const currencyName = identity?.currencyName ?? "비빔";
  const { access } = useClassroom();
  const queryClient = useQueryClient();
  const products = useQuery({ queryKey: ["financial-products", identity?.classroomId], queryFn: () => fetchFinancialProducts(identity!.classroomId), enabled: Boolean(identity) });
  const accounts = useQuery({ queryKey: ["finance", identity?.classroomId], queryFn: () => fetchFinanceAccounts(identity!.classroomId), enabled: Boolean(identity) });
  const loan = useQuery({ queryKey: ["loan-overview", identity?.classroomId], queryFn: () => fetchLoanOverview(identity!.classroomId), enabled: Boolean(identity && access?.activeCodes.has("LOAN")) });

  const subscribe = useMutation({
    mutationFn: async ({ product, amount }: { product: FinancialProduct; amount: number }) => {
      // Always submit the latest published terms. A product may have changed while
      // this detail card was open or while the realtime connection was reconnecting.
      const latestProducts = await fetchFinancialProducts(identity!.classroomId);
      queryClient.setQueryData(["financial-products", identity!.classroomId], latestProducts);
      const latest = latestProducts.find((item) => item.resourceId === product.resourceId);
      if (!latest) throw new Error("이 상품은 더 이상 가입할 수 없어요.");
      if (latest.productType === "SAVINGS") {
        return subscribeSavings(identity!.classroomId, latest, latest.installmentAmount ?? amount);
      }
      const minimum = latest.minimumAmount ?? 1;
      const maximum = latest.maximumAmount ?? Number.MAX_SAFE_INTEGER;
      if (!Number.isInteger(amount) || amount < minimum || amount > maximum) {
        throw new Error(`예금 금액은 ${minimum.toLocaleString("ko-KR")}~${maximum.toLocaleString("ko-KR")} ${currencyName} 사이로 입력해 주세요.`);
      }
      return subscribeDeposit(identity!.classroomId, latest, amount);
    },
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({ queryKey: ["finance", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] });
      showBankMessage(
        variables.product.productType === "SAVINGS" ? "적금 가입 완료" : "예금 가입 완료",
        variables.product.productType === "SAVINGS"
          ? `${variables.product.label} 적금에 가입했어요.`
          : `${variables.product.label} 예금에 돈을 맡겼어요.`
      );
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.code === "PRODUCT_VERSION_CONFLICT") {
        await products.refetch();
        showBankMessage(
          "상품 조건 변경",
          "선생님이 상품 조건을 변경해서 최신 정보로 새로고침했어요. 내용을 확인한 뒤 다시 눌러 주세요."
        );
      } else if (error instanceof ApiError && error.code === "SAVINGS_PRODUCT_ALREADY_SUBSCRIBED") {
        await accounts.refetch();
        showBankMessage("이미 가입한 적금", "같은 적금 상품에는 한 번만 가입할 수 있어요.");
      }
    }
  });
  const applyLoanMutation = useMutation({
    mutationFn: ({ amount, installments }: { amount: number; installments: number }) => applyLoan(identity!.classroomId, amount, installments),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["loan-overview", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] });
    }
  });
  const cancelLoanMutation = useMutation({
    mutationFn: (applicationId: string) => cancelLoanApplication(identity!.classroomId, applicationId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["loan-overview", identity?.classroomId] })
  });
  const repaymentMutation = useMutation({
    mutationFn: ({ loanId, stateVersion, command }: { loanId: string; stateVersion: number; command: "RETRY_DUE_PAYMENT" | "PREPAY_ALL" }) => repayLoan(identity!.classroomId, loanId, stateVersion, command),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["loan-overview", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] });
    }
  });
  const terminateDepositMutation = useMutation({
    mutationFn: (account: DepositAccount) => terminateDeposit(identity!.classroomId, account.resourceId, account.stateVersion),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["finance", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["loan-overview", identity?.classroomId] });
      showBankMessage("예금 중도 해지 완료", "예금 원금과 중도 해지 이율로 계산한 이자가 지갑에 들어왔어요. 신용점수는 최대 10점 내려갔어요.");
    },
    onError: (error: Error) => showBankMessage("예금 중도 해지 실패", error.message)
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
  const [depositAmount, setDepositAmount] = useState("");
  const [loanAmount, setLoanAmount] = useState("100");

  // 교사가 상품을 수정·중지한 뒤 목록이 갱신되면 열어 둔 상세 정보도 같은 데이터로 맞춥니다.
  useEffect(() => {
    if (!selectedProduct || !products.data) return;
    const latest = products.data.find((product) => product.resourceId === selectedProduct.resourceId);
    setSelectedProduct(latest ?? null);
  }, [products.data, selectedProduct?.resourceId]);

  useEffect(() => {
    if (selectedProduct?.productType !== "DEPOSIT") return;
    setDepositAmount(String(selectedProduct.minimumAmount ?? 1));
  }, [selectedProduct?.resourceId]);

  const activeTab = availableTabs.some((tab) => tab.id === selected) ? selected : availableTabs[0]?.id;
  const activeProducts = products.data?.filter((product) => (
    ["PUBLISHED", "ACTIVE"].includes(product.status)
      && product.productType === activeTab
  )) ?? [];
  const subscribedSavingsProductIds = new Set(
    accounts.data?.savingsAccounts.map((account) => account.productId).filter(Boolean) ?? []
  );
  const hasAccounts = Boolean(
    accounts.data && (accounts.data.depositAccounts.length > 0 || accounts.data.savingsAccounts.length > 0)
  );
  const requestedLoanAmount = Number(loanAmount);
  const requestedDepositAmount = Number(depositAmount);
  const canSubmitDeposit = selectedProduct?.productType !== "DEPOSIT" || Boolean(
    Number.isInteger(requestedDepositAmount)
      && requestedDepositAmount >= (selectedProduct.minimumAmount ?? 1)
      && requestedDepositAmount <= (selectedProduct.maximumAmount ?? Number.MAX_SAFE_INTEGER)
  );
  const canSubmitLoan = Boolean(loan.data?.canApply && Number.isInteger(requestedLoanAmount) && requestedLoanAmount >= loan.data.installmentCount && requestedLoanAmount <= loan.data.availableLimit);
  const productTermLabel = (product: FinancialProduct) => product.productType === "SAVINGS"
    ? `${product.installmentCount ?? (product.termUnits ? Math.ceil(product.termUnits / 7) : "-")}주`
    : `${product.termUnits ?? "-"}일`;
  const expectedMaturity = (product: FinancialProduct) => {
    if (!product.termUnits) return "상품 조건 확인 중";
    const date = new Date();
    date.setDate(date.getDate() + product.termUnits);
    return date.toLocaleDateString("ko-KR");
  };
  const confirmSubscription = (product: FinancialProduct) => {
    const subscriptionAmount = product.productType === "SAVINGS"
      ? product.installmentAmount ?? 0
      : requestedDepositAmount;
    const message = product.productType === "SAVINGS"
      ? `${product.label}에 가입할까요?\n\n회차별 납입액: ${subscriptionAmount.toLocaleString("ko-KR")} ${currencyName}\n가입 기간: ${productTermLabel(product)}`
      : `${product.label}에 돈을 맡길까요?\n\n예금 금액: ${subscriptionAmount.toLocaleString("ko-KR")} ${currencyName}\n가입 기간: ${productTermLabel(product)}\n예상 만기일: ${expectedMaturity(product)}`;
    confirmBankAction("가입 내용 확인", message, () => subscribe.mutate({ product, amount: subscriptionAmount }));
  };

  return (
    <Screen title="이코 은행" right={<BackButton />}>
      <View accessibilityRole="tablist" style={styles.tabs}>
        {availableTabs.map((tab) => (
          <Pressable key={tab.id} accessibilityRole="tab" accessibilityState={{ selected: activeTab === tab.id }} aria-selected={activeTab === tab.id} onPress={() => setSelected(tab.id)} style={[styles.tab, activeTab === tab.id && styles.tabActive]}>
            <Text style={[styles.tabText, activeTab === tab.id && styles.tabTextActive]}>{tab.label}</Text>
          </Pressable>
        ))}
      </View>
      {activeTab === "ACCOUNT" ? (
        <View style={styles.accountsSection}>
          {accounts.isPending ? <ActivityIndicator color={colors.green500} /> : !hasAccounts ? <Text style={styles.emptyText}>아직 만든 예금·적금 통장이 없어요. 위의 예금 또는 적금 탭에서 원하는 상품을 선택해 주세요.</Text> : accounts.data?.depositAccounts.map((account) => (
            <PassbookCover key={account.resourceId} title={account.label} subtitle="만기까지 안전하게 보관해요" color="#6FAE83" numberLabel={accountNumber("예금", account.resourceId)}>
              <View style={styles.accountDetail}><Text style={styles.accountDetailLabel}>맡긴 금액</Text><Text style={styles.accountDetailValue}>{account.principalAmount.toLocaleString("ko-KR")} {currencyName}</Text></View>
              <View style={styles.accountSchedule}><Text style={styles.accountDetailLabel}>만기일</Text><Text style={styles.scheduleValue}>{formatBankDate(account.maturesAt)}</Text></View>
              {account.status === "ACTIVE" && new Date(account.maturesAt).getTime() > Date.now() ? <Pressable accessibilityRole="button" disabled={terminateDepositMutation.isPending} onPress={() => confirmBankAction("예금 중도 해지", `만기 전에 예금을 해지할까요?\n\n맡긴 원금: ${account.principalAmount.toLocaleString("ko-KR")} ${currencyName}\n중도 해지 이율로 이자가 계산되며 신용점수가 최대 10점 내려갑니다.`, () => terminateDepositMutation.mutate(account))} style={[styles.secondaryButton, terminateDepositMutation.isPending && styles.disabled]}><Text style={styles.secondaryButtonText}>{terminateDepositMutation.isPending ? "해지 중" : "만기 전 중도 해지"}</Text></Pressable> : null}
              {account.status === "EARLY_TERMINATED" ? <Text style={styles.passbookNotice}>중도 해지된 예금이에요.</Text> : null}
            </PassbookCover>
          ))}
          {accounts.data?.savingsAccounts.map((account) => (
            <PassbookCover key={account.resourceId} title={account.label} subtitle={`${account.paidInstallmentCount}/${account.installmentCount}회 납입 완료`} color="#D89A57" numberLabel={accountNumber("적금", account.resourceId)}>
              <View style={styles.accountDetail}><Text style={styles.accountDetailLabel}>지금까지 납입한 금액</Text><Text style={styles.accountDetailValue}>{account.principalBalance.toLocaleString("ko-KR")} {currencyName}</Text></View>
              <View style={styles.savingsTerms}>
                <Metric label="가입 금리" value={`연 ${(account.interestRateBps / 100).toFixed(1)}%`} />
                <Metric label="만기일" value={formatBankDate(account.maturesAt)} />
                <Metric
                  label={account.nextInstallmentStatus === "MISSED" ? "미납일" : "다음 납입일"}
                  value={formatBankDate(account.nextInstallmentAt)}
                />
                <Metric label="다음 납입 금액" value={`${account.installmentAmount.toLocaleString("ko-KR")} ${currencyName}`} />
              </View>
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
            {loan.data.pendingApplicationStatus ? <View style={styles.pendingBlock}><Text style={styles.pendingNotice}>대출 신청이 접수되었어요. 선생님 승인을 기다려요.</Text><Pressable accessibilityRole="button" disabled={!loan.data.pendingApplicationId || cancelLoanMutation.isPending} onPress={() => loan.data?.pendingApplicationId && cancelLoanMutation.mutate(loan.data.pendingApplicationId)} style={styles.secondaryButton}><Text style={styles.secondaryButtonText}>신청 취소</Text></Pressable></View> : <View style={styles.loanApplyRow}>
              <TextInput accessibilityLabel="대출 신청 금액" value={loanAmount} onChangeText={setLoanAmount} keyboardType="number-pad" style={styles.loanAmountInput} />
              <Pressable accessibilityRole="button" disabled={!canSubmitLoan || applyLoanMutation.isPending} onPress={() => confirmBankAction("대출 신청 확인", `${requestedLoanAmount.toLocaleString("ko-KR")} ${currencyName}을 대출 신청할까요?\n\n상환 횟수: ${loan.data.installmentCount}회\n적용 금리: ${(loan.data.interestRateBps / 100).toFixed(2)}%`, () => applyLoanMutation.mutate({ amount: requestedLoanAmount, installments: loan.data.installmentCount }))} style={[styles.passbookButton, (!canSubmitLoan || applyLoanMutation.isPending) && styles.disabled]}><Text style={styles.passbookButtonText}>{applyLoanMutation.isPending ? "신청 중" : "대출 신청"}</Text></Pressable>
            </View>}
            {!loan.data.canApply && !loan.data.pendingApplicationStatus ? <Text style={styles.pendingNotice}>현재는 대출을 신청할 수 없어요. 신용점수와 기존 대출을 확인해 주세요.</Text> : null}
            {loan.data.loans.map((item) => <View key={item.loanId} style={styles.loanRow}>
              <Text style={styles.accountDetailLabel}>{item.status === "OVERDUE" ? "연체" : item.status === "REPAID" ? "상환 완료" : "상환 중"}</Text>
              <Text style={styles.loanMetricDetail}>대출금 {item.principalAmount.toLocaleString("ko-KR")} · 남은 원금 {item.outstandingPrincipal.toLocaleString("ko-KR")}</Text>
              <Text style={styles.loanMetricDetail}>남은 이자 {item.outstandingInterest.toLocaleString("ko-KR")} · 적용금리 {(item.appliedInterestRateBps / 100).toFixed(2)}%</Text>
              {item.installments.map((installment) => <View key={installment.installmentId} style={styles.installmentRow}><Text style={styles.installmentTitle}>{installment.installmentNumber}회차 · {installment.status === "PAID" ? "납부 완료" : installment.status === "MISSED" ? "미납" : "납부 예정"}</Text><Text style={styles.loanMetricDetail}>{(installment.scheduledPrincipal + installment.scheduledInterest).toLocaleString("ko-KR")} {currencyName} · {new Date(installment.dueAt).toLocaleDateString("ko-KR")}</Text>{installment.status === "MISSED" ? <Text style={styles.overdueText}>재납부 기한 {new Date(installment.graceUntil).toLocaleDateString("ko-KR")}</Text> : null}</View>)}
              {["ACTIVE", "OVERDUE"].includes(item.status) ? <View style={styles.loanActions}>
                {item.installments.some((installment) => installment.status === "MISSED") ? <Pressable accessibilityRole="button" disabled={repaymentMutation.isPending} onPress={() => confirmBankAction("미납 회차 납부 확인", "미납된 회차의 원금과 이자를 지금 납부할까요?", () => repaymentMutation.mutate({ loanId: item.loanId, stateVersion: item.stateVersion, command: "RETRY_DUE_PAYMENT" }))} style={styles.passbookButton}><Text style={styles.passbookButtonText}>미납 회차 다시 납부</Text></Pressable> : null}
                <Pressable accessibilityRole="button" disabled={repaymentMutation.isPending} onPress={() => confirmBankAction("대출 전액 상환", `남은 ${(item.outstandingPrincipal + item.outstandingInterest).toLocaleString("ko-KR")} ${currencyName}을 지금 상환할까요?`, () => repaymentMutation.mutate({ loanId: item.loanId, stateVersion: item.stateVersion, command: "PREPAY_ALL" }))} style={styles.secondaryButton}><Text style={styles.secondaryButtonText}>전액 상환</Text></Pressable>
              </View> : null}
            </View>)}
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
                  accessibilityRole="button"
                  onPress={() => setSelectedProduct(product)}
                  style={styles.passbookButton}
                >
                  <Text style={styles.passbookButtonText}>{product.productType === "SAVINGS" && subscribedSavingsProductIds.has(product.resourceId) ? "가입 정보 보기" : "정보 확인하기"}</Text>
                </Pressable>
              </View>
            ))}
          {selectedProduct && selectedProduct.productType === activeTab ? (
            <View style={styles.productDetail}>
              <View style={styles.productDetailHeading}>
                <View><Text style={styles.productDetailTitle}>{selectedProduct.label}</Text><Text style={styles.productRate}>연 {(selectedProduct.interestRateBps / 100).toFixed(1)}%</Text></View>
                <Pressable accessibilityRole="button" onPress={() => setSelectedProduct(null)}><Text style={styles.detailClose}>닫기</Text></Pressable>
              </View>
              <View style={styles.productDetailGrid}>
                <Metric label="이율" value={`연 ${(selectedProduct.interestRateBps / 100).toFixed(1)}%`} />
                <Metric label="가입 기간" value={productTermLabel(selectedProduct)} />
                <Metric label="예상 만기일" value={expectedMaturity(selectedProduct)} />
                <Metric label="최소 금액" value={`${selectedProduct.minimumAmount ?? "확인 중"} ${currencyName}`} />
                <Metric label="최대 금액" value={`${selectedProduct.maximumAmount ?? "확인 중"} ${currencyName}`} />
                {selectedProduct.productType === "SAVINGS" ? <Metric label="회차별 납입액" value={`${selectedProduct.installmentAmount ?? "확인 중"} ${currencyName}`} /> : null}
              </View>
              {selectedProduct.productType === "DEPOSIT" ? (
                <View style={styles.depositAmountRow}>
                  <View style={styles.depositAmountCopy}>
                    <Text style={styles.depositAmountLabel}>맡길 금액</Text>
                    <Text style={styles.depositAmountHelp}>최소·최대 금액 안에서 직접 정할 수 있어요.</Text>
                  </View>
                  <View style={styles.depositAmountField}>
                    <TextInput
                      accessibilityLabel="예금할 금액"
                      inputMode="numeric"
                      keyboardType="number-pad"
                      value={depositAmount}
                      onChangeText={setDepositAmount}
                      style={styles.depositAmountInput}
                    />
                    <Text style={styles.depositAmountSuffix}>{currencyName}</Text>
                  </View>
                </View>
              ) : null}
              <Pressable
                accessibilityRole="button"
                disabled={subscribe.isPending
                  || !canSubmitDeposit
                  || (selectedProduct.productType === "SAVINGS" && (!selectedProduct.installmentAmount || subscribedSavingsProductIds.has(selectedProduct.resourceId)))}
                onPress={() => confirmSubscription(selectedProduct)}
                style={[styles.passbookButton, (subscribe.isPending || !canSubmitDeposit || (selectedProduct.productType === "SAVINGS"
                  && (!selectedProduct.installmentAmount || subscribedSavingsProductIds.has(selectedProduct.resourceId)))) && styles.disabled]}
              >
                <Text style={styles.passbookButtonText}>{subscribe.isPending
                  ? "처리 중"
                  : selectedProduct.productType === "SAVINGS" && subscribedSavingsProductIds.has(selectedProduct.resourceId)
                    ? "이미 가입한 적금"
                    : selectedProduct.productType === "SAVINGS" ? "이 적금 시작하기" : "이 예금에 돈 맡기기"}</Text>
              </Pressable>
            </View>
          ) : null}
        </View>
      )}
      {products.isError ? <Text accessibilityLiveRegion="assertive" style={styles.error}>{products.error.message}</Text> : null}
      {accounts.isError ? <Text accessibilityLiveRegion="assertive" style={styles.error}>{accounts.error.message}</Text> : null}
      {subscribe.isError ? <Text accessibilityLiveRegion="assertive" style={styles.error}>{subscriptionErrorMessage(subscribe.error)}</Text> : null}
      {loan.isError ? <Text accessibilityLiveRegion="assertive" style={styles.error}>{loan.error.message}</Text> : null}
      {applyLoanMutation.isError ? <Text accessibilityLiveRegion="assertive" style={styles.error}>{applyLoanMutation.error.message}</Text> : null}
      {cancelLoanMutation.isError ? <Text accessibilityLiveRegion="assertive" style={styles.error}>{cancelLoanMutation.error.message}</Text> : null}
      {repaymentMutation.isError ? <Text accessibilityLiveRegion="assertive" style={styles.error}>{repaymentMutation.error.message}</Text> : null}
    </Screen>
  );
}

function confirmBankAction(title: string, message: string, onConfirm: () => void) {
  if (Platform.OS === "web") {
    if (globalThis.confirm(`${title}\n\n${message}`)) onConfirm();
    return;
  }
  Alert.alert(title, message, [
    { text: "다시 확인", style: "cancel" },
    { text: "확인", onPress: onConfirm }
  ]);
}

function showBankMessage(title: string, message: string) {
  if (Platform.OS === "web") {
    globalThis.alert(`${title}\n${message}`);
    return;
  }
  Alert.alert(title, message);
}

function subscriptionErrorMessage(error: Error) {
  if (!(error instanceof ApiError)) return error.message;
  if (error.code === "PRODUCT_VERSION_CONFLICT") return "상품 조건을 최신 정보로 바꿨어요. 다시 확인해 주세요.";
  if (error.code === "SAVINGS_PRODUCT_ALREADY_SUBSCRIBED") return "같은 적금 상품에는 한 번만 가입할 수 있어요.";
  return error.message;
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
  tabActive: { borderBottomColor: colors.green700 },
  tabText: { color: colors.muted700, fontSize: 16, fontWeight: "800", textAlign: "center" },
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
  passbookNotice: { color: colors.muted700, fontSize: 15, lineHeight: 23, fontWeight: "700" },
  productLine: { minHeight: 86, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 18, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  productCopy: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 15 },
  productTitle: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  productRate: { color: colors.green700, fontSize: 15, fontWeight: "900" },
  passbookButton: { minHeight: 42, alignItems: "center", justifyContent: "center", paddingHorizontal: 18, borderRadius: 6, backgroundColor: colors.ink900 },
  passbookButtonText: { color: colors.white, fontSize: 14, fontWeight: "900" },
  productDetail: { gap: 16, marginTop: 18, padding: 18, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 12, backgroundColor: colors.white },
  productDetailHeading: { flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between", gap: 16 },
  productDetailTitle: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  detailClose: { color: colors.muted700, fontSize: 14, fontWeight: "800" },
  productDetailGrid: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  productMetric: { width: "48%", gap: 4, padding: 11, borderRadius: 8, backgroundColor: colors.sky100 },
  productMetricLabel: { color: colors.muted700, fontSize: 12, fontWeight: "800" },
  productMetricValue: { color: colors.ink900, fontSize: 14, fontWeight: "900" },
  depositAmountRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 12, padding: 14, borderRadius: 8, backgroundColor: colors.sky100 },
  depositAmountCopy: { flex: 1, minWidth: 180, gap: 3 },
  depositAmountLabel: { color: colors.ink900, fontSize: 15, fontWeight: "900" },
  depositAmountHelp: { color: colors.muted700, fontSize: 12, fontWeight: "700" },
  depositAmountField: { minWidth: 180, flexDirection: "row", alignItems: "center", borderBottomWidth: 2, borderBottomColor: colors.green700 },
  depositAmountInput: { flex: 1, minHeight: 44, color: colors.ink900, fontSize: 20, fontWeight: "900", textAlign: "right" },
  depositAmountSuffix: { flexShrink: 0, marginLeft: 8, color: colors.muted700, fontSize: 14, fontWeight: "800" },
  disabled: { opacity: 0.45 },
  accountsSection: { width: "100%", maxWidth: 900, gap: 18, paddingTop: 6 },
  sectionTitle: { paddingBottom: 12, color: colors.ink900, fontSize: 21, fontWeight: "900" },
  accountLine: { minHeight: 70, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 20, borderTopWidth: 1, borderTopColor: colors.lineSoft },
  accountTitle: { color: colors.ink900, fontSize: 16, fontWeight: "900" },
  accountMeta: { marginTop: 4, color: colors.muted700, fontSize: 13, fontWeight: "700" },
  accountValue: { color: colors.green700, fontSize: 18, fontWeight: "900" },
  accountDetail: { gap: 4, paddingBottom: 12, borderBottomWidth: 1, borderBottomColor: "#E8E2D2" },
  accountDetailLabel: { color: colors.muted700, fontSize: 13, fontWeight: "800" },
  accountDetailValue: { color: colors.ink900, fontSize: 24, fontWeight: "900" },
  accountSchedule: { gap: 4 },
  scheduleValue: { color: colors.ink900, fontSize: 16, fontWeight: "900" },
  savingsTerms: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  loanGrid: { flexDirection: "row", gap: 12 },
  loanMetric: { flex: 1, gap: 4, padding: 12, borderWidth: 1, borderColor: "#E8E2D2", borderRadius: 6 },
  loanMetricDetail: { color: colors.muted700, fontSize: 13, fontWeight: "700" },
  loanApplyRow: { flexDirection: "row", alignItems: "center", gap: 10 },
  loanAmountInput: { flex: 1, minHeight: 42, paddingHorizontal: 12, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 6, color: colors.ink900, fontSize: 16, fontWeight: "800" },
  pendingNotice: { color: colors.green700, fontSize: 14, fontWeight: "800" },
  pendingBlock: { gap: 8, alignItems: "flex-start" },
  loanRow: { gap: 4, paddingTop: 10, borderTopWidth: 1, borderTopColor: "#E8E2D2" },
  installmentRow: { gap: 2, marginTop: 5, padding: 9, borderRadius: 6, backgroundColor: colors.sky100 },
  installmentTitle: { color: colors.ink900, fontSize: 13, fontWeight: "900" },
  overdueText: { color: colors.red700, fontSize: 12, fontWeight: "800" },
  loanActions: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 },
  secondaryButton: { minHeight: 42, alignItems: "center", justifyContent: "center", paddingHorizontal: 16, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 6, backgroundColor: colors.white },
  secondaryButtonText: { color: colors.ink900, fontSize: 14, fontWeight: "900" },
  loanProductBadge: { alignSelf: "flex-start", paddingHorizontal: 9, paddingVertical: 5, borderRadius: 999, backgroundColor: "#E5F0F6", color: "#35647C", fontSize: 12, fontWeight: "900" },
  emptyText: { paddingVertical: 20, borderTopWidth: 1, borderTopColor: colors.lineSoft, color: colors.muted700, fontSize: 15, fontWeight: "700" },
  error: { paddingTop: 12, color: colors.red700, fontWeight: "700" }
});
