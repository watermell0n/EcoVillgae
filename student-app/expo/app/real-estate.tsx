import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { ActivityIndicator, Alert, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, useWindowDimensions, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { fetchWallet } from "@/features/student/api";
import {
  applicationStatusLabel,
  applySubscriptionOffer,
  cancelPropertySaleListing,
  cancelSubscriptionApplication,
  contractTypeLabel,
  createPropertySaleListing,
  depositSubscription,
  fetchMyProperties,
  fetchPropertyLayout,
  fetchSubscriptionAccount,
  fetchSubscriptionApplications,
  fetchSubscriptionOffers,
  openSubscriptionAccount,
  propertyStatusLabel,
  requestPropertyContract,
  selectableContractTypes,
  type ContractType,
  type Property
} from "@/features/property/api";
import { colors, radii } from "@/theme/tokens";

const SEAT_GAP = 10;

/**
 * 화면 어디를 보고 있어도 놓치지 않도록 신청 결과를 대화상자로 알립니다.
 * 웹의 react-native-web에는 Alert가 없어 브라우저 alert로 대신합니다.
 */
function announce(title: string, message: string) {
  if (Platform.OS === "web") {
    (globalThis as { alert?: (text: string) => void }).alert?.(`${title}\n\n${message}`);
    return;
  }
  Alert.alert(title, message);
}

function toneFor(property: Property, mine: boolean) {
  if (mine) return styles.seatMine;
  if (property.status === "AVAILABLE") return styles.seatDirect;
  if (property.status === "LISTED_FOR_SALE") return styles.seatResale;
  if (property.status === "SUBSCRIPTION_OPEN") return styles.seatSubscription;
  return styles.seatOwned;
}

export default function RealEstateScreen() {
  const { width } = useWindowDimensions();
  const { identity } = useAuth();
  const { access, enabled } = useClassroom();
  const queryClient = useQueryClient();
  const classroomId = identity?.classroomId;
  const currency = identity?.currencyName ?? "비빔";
  const realEstateEnabled = Boolean(access?.activeCodes.has("REAL_ESTATE"));
  const subscriptionEnabled = enabled("subscription") && realEstateEnabled;
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [selectedContractType, setSelectedContractType] = useState<ContractType>("SALE");
  const [depositInput, setDepositInput] = useState("10");
  const [resaleInput, setResaleInput] = useState("100");

  // 선생님이 계약을 승인하면 좌석판과 내 자리가 바뀝니다. 학생이 새로고침하지 않아도
  // 몇 초 안에 반영되도록 주기적으로 다시 읽습니다.
  const layout = useQuery({
    queryKey: ["property-layout", classroomId],
    queryFn: () => fetchPropertyLayout(classroomId!),
    enabled: Boolean(classroomId && realEstateEnabled),
    refetchInterval: 10_000
  });
  const mine = useQuery({
    queryKey: ["properties-mine", classroomId],
    queryFn: () => fetchMyProperties(classroomId!),
    enabled: Boolean(classroomId && realEstateEnabled),
    refetchInterval: 10_000
  });
  const account = useQuery({
    queryKey: ["subscription-account", classroomId],
    queryFn: () => fetchSubscriptionAccount(classroomId!),
    enabled: Boolean(classroomId && subscriptionEnabled)
  });
  const offers = useQuery({
    queryKey: ["subscription-offers", classroomId],
    queryFn: () => fetchSubscriptionOffers(classroomId!),
    enabled: Boolean(classroomId && subscriptionEnabled)
  });
  const applications = useQuery({
    queryKey: ["subscription-applications", classroomId],
    queryFn: () => fetchSubscriptionApplications(classroomId!),
    enabled: Boolean(classroomId && subscriptionEnabled)
  });
  // 계약에 낼 돈이 있는지 미리 보고 버튼을 막습니다. 눌러서 실패하는 것보다 낫습니다.
  const wallet = useQuery({
    queryKey: ["wallet", classroomId],
    queryFn: () => fetchWallet(classroomId!),
    enabled: Boolean(classroomId && realEstateEnabled),
    refetchInterval: 10_000
  });

  const mineIds = useMemo(() => new Set((mine.data ?? []).map((property) => property.propertyId)), [mine.data]);
  const selected = layout.data?.properties.find((property) => property.propertyId === selectedId) ?? null;
  const columns = Math.max(layout.data?.columnCount ?? 1, 1);
  const contentWidth = Math.min(width >= 768 ? width - 330 : width - 48, 1050);
  const seatWidth = Math.max(72, Math.min(150, Math.floor((contentWidth - (columns - 1) * SEAT_GAP) / columns)));
  const seatGridWidth = columns * seatWidth + (columns - 1) * SEAT_GAP;
  const wonApplication = selected ? applications.data?.find((item) => item.status === "WON" && item.assignedPropertyId === selected.propertyId) : undefined;
  const hasActiveApplication = applications.data?.some((item) => ["APPLIED", "WON"].includes(item.status)) ?? false;
  const contractTypes = selected ? selectableContractTypes(selected) : [];

  useEffect(() => {
    if (!contractTypes.length) return;
    if (!contractTypes.includes(selectedContractType)) setSelectedContractType(contractTypes[0]);
  }, [selectedId, selectedContractType, contractTypes]);

  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ["property-layout", classroomId] }),
    queryClient.invalidateQueries({ queryKey: ["properties-mine", classroomId] }),
    queryClient.invalidateQueries({ queryKey: ["subscription-offers", classroomId] }),
    queryClient.invalidateQueries({ queryKey: ["subscription-applications", classroomId] }),
    queryClient.invalidateQueries({ queryKey: ["subscription-account", classroomId] })
  ]);

  const purchase = useMutation({
    mutationFn: ({ property, contractType, applicationId }: { property: Property; contractType: ContractType; applicationId?: string }) => requestPropertyContract(property.propertyId, contractType, applicationId),
    onSuccess: async (_result, variables) => {
      const message = variables.property.status === "SUBSCRIPTION_OPEN"
        ? "당첨 자리 구매를 신청했어요. 선생님 승인 후 내 자리가 돼요."
        : `${contractTypeLabel(variables.contractType)} 계약을 신청해 돈이 빠져나갔어요. 선생님 승인 후 내 자리에 반영돼요.`;
      setNotice(message);
      announce("계약 신청 완료", message);
      await refresh();
      await queryClient.invalidateQueries({ queryKey: ["wallet", classroomId] });
    },
    onError: (error: Error) => {
      setNotice(error.message);
      announce("계약 신청 실패", error.message);
    }
  });
  const resale = useMutation({
    mutationFn: ({ propertyId, price }: { propertyId: string; price: number }) => createPropertySaleListing(propertyId, price),
    onSuccess: async () => { setNotice("친구들이 구매할 수 있도록 재판매 매물을 열었어요."); await refresh(); },
    onError: (error: Error) => setNotice(error.message)
  });
  const cancelResale = useMutation({
    mutationFn: cancelPropertySaleListing,
    onSuccess: async () => { setNotice("재판매를 취소했어요."); await refresh(); },
    onError: (error: Error) => setNotice(error.message)
  });
  const openAccount = useMutation({
    mutationFn: () => openSubscriptionAccount(classroomId!),
    onSuccess: async () => { setNotice("청약통장을 개설했어요."); await refresh(); },
    onError: (error: Error) => setNotice(error.message)
  });
  const deposit = useMutation({
    mutationFn: (amount: number) => depositSubscription(classroomId!, amount),
    onSuccess: async () => { setNotice("청약통장에 납입했어요."); await refresh(); await queryClient.invalidateQueries({ queryKey: ["wallet", classroomId] }); },
    onError: (error: Error) => setNotice(error.message)
  });
  const apply = useMutation({
    mutationFn: applySubscriptionOffer,
    onSuccess: async () => { setNotice("청약 신청을 완료했어요."); await refresh(); },
    onError: (error: Error) => setNotice(error.message)
  });
  const cancelApplication = useMutation({
    mutationFn: cancelSubscriptionApplication,
    onSuccess: async () => { setNotice("청약 신청을 취소했어요."); await refresh(); },
    onError: (error: Error) => setNotice(error.message)
  });

  const depositAmount = Number(depositInput);
  const resalePrice = Number(resaleInput);
  const canContractSelected = Boolean(selected && (
    (["AVAILABLE", "LISTED_FOR_SALE", "LISTED_FOR_RENT"].includes(selected.status) && contractTypes.includes(selectedContractType))
      || (selected.status === "SUBSCRIPTION_OPEN" && wonApplication && selectedContractType === "SALE")
  ));

  /** 이 계약에 지금 내야 하는 돈입니다. 월세·전세는 보증금을 먼저 내고, 월세는 이후 주기마다 자동으로 나갑니다. */
  const contractCost = (() => {
    if (!selected) return 0;
    if (selected.status === "SUBSCRIPTION_OPEN") return 0;
    if (selectedContractType === "SALE") return selected.salePrice;
    return selected.depositAmount;
  })();
  const selectedContractPrice = !selected
    ? 0
    : selectedContractType === "SALE"
      ? selected.salePrice
      : selectedContractType === "JEONSE"
        ? selected.depositAmount
        : selected.monthlyRent;
  const balance = wallet.data?.balance ?? 0;
  const affordsContract = !wallet.isPending && balance >= contractCost;

  if (!realEstateEnabled) {
    return <Screen title="부동산" right={<BackButton />}><StateCard title="부동산 모듈이 꺼져 있어요"><Text style={styles.description}>선생님이 부동산 모듈을 열면 교실 좌석판을 볼 수 있어요. 청약은 부동산 모듈과 함께 켜져야 합니다.</Text></StateCard></Screen>;
  }

  return (
    <Screen title="부동산·청약" subtitle="교실 좌석 소유권" right={<BackButton />}>
      {notice ? <Pressable accessibilityRole="button" accessibilityLiveRegion="polite" onPress={() => setNotice(null)} style={styles.notice}><Text style={styles.noticeText}>{notice}</Text><Text style={styles.noticeClose}>닫기</Text></Pressable> : null}

      <StateCard tone="sky">
        <Text style={styles.lead}>교실 자리를 직접 보고 골라요</Text>
        <Text style={styles.description}>초록색은 선생님 직판, 주황색은 친구의 재판매, 보라색은 청약 자리예요. 한 학생은 한 자리만 소유할 수 있어요.</Text>
      </StateCard>

        <View style={styles.section}>
          <View style={styles.sectionHead}><Text style={styles.sectionTitle}>우리 반 좌석판</Text><View style={styles.legend}><Text style={styles.directText}>● 직판</Text><Text style={styles.resaleText}>● 재판매</Text>{subscriptionEnabled ? <Text style={styles.subscriptionText}>● 청약</Text> : null}<Text style={styles.ownedText}>● 소유됨</Text></View></View>
        {layout.isPending ? <ActivityIndicator color={colors.green500} /> : layout.error ? <StateCard title="좌석판을 불러오지 못했어요"><Text style={styles.description}>{layout.error.message}</Text></StateCard> : layout.data?.properties.length ? <>
          <ScrollView horizontal showsHorizontalScrollIndicator contentContainerStyle={styles.seatScrollContent}>
            <View style={[styles.seatStage, { width: seatGridWidth }]}>
              <View style={styles.board}><Text style={styles.boardText}>칠판</Text></View>
              <View style={styles.seatGrid}>
                {layout.data.properties.map((property) => {
                  const isMine = mineIds.has(property.propertyId);
                  return <Pressable
                    accessibilityRole="button"
                    key={property.propertyId}
                    onPress={() => setSelectedId(property.propertyId)}
                    style={({ pressed }) => [styles.seat, { width: seatWidth }, toneFor(property, isMine), selectedId === property.propertyId && styles.seatSelected, pressed && styles.pressed]}
                  >
                    <Text style={styles.seatCoordinate}>{property.coordinate}</Text>
                    <Text numberOfLines={1} style={styles.seatStatus}>{isMine ? "내 자리" : propertyStatusLabel(property.status)}</Text>
                    {property.salePrice > 0 && ["AVAILABLE", "LISTED_FOR_SALE"].includes(property.status) ? <Text style={styles.seatPrice}>{property.salePrice} {currency}</Text> : null}
                  </Pressable>;
                })}
              </View>
            </View>
          </ScrollView>
        </> : <StateCard title="아직 좌석판이 없어요"><Text style={styles.description}>선생님이 좌석판을 만들면 여기에 표시돼요.</Text></StateCard>}
      </View>

      {selected ? <StateCard tone={mineIds.has(selected.propertyId) ? "mint" : "cream"}>
        <View style={styles.detailHead}><View><Text style={styles.cardTitle}>{selected.coordinate} · {selected.displayName}</Text><Text style={styles.cardMeta}>{mineIds.has(selected.propertyId) ? "내가 소유한 자리" : propertyStatusLabel(selected.status)}</Text></View>{selectedContractPrice > 0 ? <Text style={styles.detailPrice}>{selectedContractPrice.toLocaleString("ko-KR")} {currency}{selectedContractType === "RENT" ? `/${selected.paymentCycleDays}일` : ""}</Text> : null}</View>
        {!mineIds.has(selected.propertyId) && selected.status !== "SUBSCRIPTION_OPEN" && contractTypes.length ? <>
          <View accessibilityRole="radiogroup" style={styles.contractTypes}>{contractTypes.map((type) => <Pressable accessibilityRole="radio" accessibilityState={{ checked: selectedContractType === type }} aria-checked={selectedContractType === type} key={type} onPress={() => setSelectedContractType(type)} style={[styles.contractType, selectedContractType === type && styles.contractTypeSelected]}><Text style={[styles.contractTypeText, selectedContractType === type && styles.contractTypeTextSelected]}>{contractTypeLabel(type)}</Text></Pressable>)}</View>
          <Text style={styles.description}>{selectedContractType === "SALE"
            ? `매매가 ${selected.salePrice.toLocaleString("ko-KR")} ${currency}`
            : selectedContractType === "JEONSE"
              ? `보증금 ${selected.depositAmount.toLocaleString("ko-KR")} ${currency} · ${selected.contractTermDays}일`
              : `보증금 ${selected.depositAmount.toLocaleString("ko-KR")} ${currency} · ${selected.monthlyRent.toLocaleString("ko-KR")} ${currency}/${selected.paymentCycleDays}일 · ${selected.contractTermDays}일`}</Text>
        </> : null}
        {canContractSelected ? <>
          <Pressable accessibilityRole="button" disabled={purchase.isPending || !affordsContract} onPress={() => purchase.mutate({ property: selected, contractType: selected.status === "SUBSCRIPTION_OPEN" ? "SALE" : selectedContractType, applicationId: wonApplication?.applicationId })} style={[styles.primary, (purchase.isPending || !affordsContract) && styles.disabled]}><Text style={styles.primaryText}>{selected.status === "SUBSCRIPTION_OPEN" ? "당첨 자리 구매 신청" : `${contractTypeLabel(selectedContractType)} 계약 신청`}</Text></Pressable>
          {!affordsContract ? <Text style={styles.description}>지금 낼 돈이 {contractCost.toLocaleString("ko-KR")} {currency}인데 내 지갑에는 {balance.toLocaleString("ko-KR")} {currency}이 있어요. 돈을 더 모은 뒤 신청할 수 있어요.</Text> : null}
        </> : null}
        {!mineIds.has(selected.propertyId) && !canContractSelected ? <Text style={styles.description}>{selected.status === "SUBSCRIPTION_OPEN" ? "청약 당첨 후 구매를 신청할 수 있어요." : selected.isPublic ? "이 자리에는 현재 신청 가능한 매매·전세·월세 조건이 없어요." : "선생님이 아직 이 자리를 공개하지 않았어요."}</Text> : null}
      </StateCard> : null}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>내 자리</Text>
        {mine.isPending ? <ActivityIndicator color={colors.orange500} /> : mine.data?.length ? mine.data.map((property) => <StateCard key={property.propertyId} tone="mint">
          <Text style={styles.cardTitle}>{property.coordinate} · {property.displayName}</Text><Text style={styles.cardMeta}>{propertyStatusLabel(property.status)}</Text>
          {property.status === "STUDENT_OWNED" ? <View style={styles.inlineForm}><TextInput accessibilityLabel="재판매 가격" inputMode="numeric" keyboardType="number-pad" value={resaleInput} onChangeText={setResaleInput} style={styles.input} /><Pressable disabled={!Number.isInteger(resalePrice) || resalePrice < 1 || resale.isPending} onPress={() => resale.mutate({ propertyId: property.propertyId, price: resalePrice })} style={styles.orangeButton}><Text style={styles.primaryText}>재판매 열기</Text></Pressable></View> : null}
          {property.status === "LISTED_FOR_SALE" ? <Pressable disabled={cancelResale.isPending} onPress={() => cancelResale.mutate(property.propertyId)} style={styles.secondary}><Text style={styles.secondaryText}>재판매 취소</Text></Pressable> : null}
        </StateCard>) : <StateCard title="아직 내 자리가 없어요"><Text style={styles.description}>좌석판에서 직판 또는 재판매 자리를 골라 보세요.</Text></StateCard>}
      </View>

      {subscriptionEnabled ? <View style={styles.section}>
        <Text style={styles.sectionTitle}>청약통장</Text>
        {account.isPending ? <ActivityIndicator color={colors.purple500} /> : account.isError ? <StateCard title="청약통장 정보를 불러오지 못했어요"><Text style={styles.description}>{account.error.message}</Text><Pressable onPress={() => account.refetch()} style={styles.secondary}><Text style={styles.secondaryText}>다시 확인</Text></Pressable></StateCard> : account.data ? <StateCard tone="cream">
          <Text style={styles.balance}>{account.data.totalDepositAmount.toLocaleString("ko-KR")} {currency}</Text><Text style={styles.cardMeta}>납입 {account.data.depositCount}회</Text>
          <View style={styles.inlineForm}><TextInput accessibilityLabel="청약통장 납입 금액" inputMode="numeric" keyboardType="number-pad" value={depositInput} onChangeText={setDepositInput} style={styles.input} /><Pressable disabled={!Number.isInteger(depositAmount) || depositAmount < 1 || deposit.isPending} onPress={() => deposit.mutate(depositAmount)} style={styles.primary}><Text style={styles.primaryText}>납입하기</Text></Pressable></View>
        </StateCard> : <StateCard title="청약통장이 아직 없어요"><Text style={styles.description}>통장을 개설하고 분양가 이상을 예치하면 청약에 신청할 수 있어요.</Text><Pressable disabled={openAccount.isPending} onPress={() => openAccount.mutate()} style={styles.primary}><Text style={styles.primaryText}>청약통장 개설</Text></Pressable></StateCard>}

        <Text style={styles.subTitle}>진행 중인 청약</Text>
        {offers.data?.filter((offer) => offer.status === "OPEN").length ? offers.data.filter((offer) => offer.status === "OPEN").map((offer) => {
          const application = applications.data?.find((item) => item.offerId === offer.offerId);
          return <StateCard key={offer.offerId}>
            <Text style={styles.cardTitle}>{offer.title}</Text><Text style={styles.cardMeta}>공급 {offer.supplyQuantity}석 · 신청 {offer.applicantCount}명 · {new Date(offer.closesAt).toLocaleString("ko-KR")} 마감</Text>
            <Text style={styles.offerPrice}>분양가 {offer.salePrice.toLocaleString("ko-KR")} {currency}</Text><Text style={styles.description}>조건: 납입 {offer.minimumDepositCount}회 · 예치금 {Math.max(offer.minimumBalance, offer.salePrice).toLocaleString("ko-KR")} {currency} 이상</Text>
            {application ? <View style={styles.applicationRow}><Text style={styles.applicationStatus}>{applicationStatusLabel(application.status)}</Text>{application.status === "APPLIED" ? <Pressable onPress={() => cancelApplication.mutate(application.applicationId)} style={styles.secondary}><Text style={styles.secondaryText}>신청 취소</Text></Pressable> : null}</View> : (() => {
              // 분양가(또는 최소 예치금) 이상을 넣어 두지 않으면 서버가 거절합니다.
              // 눌러서 실패하기 전에 버튼을 잠그고 얼마가 모자란지 알려 줍니다.
              const required = Math.max(offer.minimumBalance, offer.salePrice);
              const deposited = account.data?.totalDepositAmount ?? 0;
              const enoughDeposit = deposited >= required;
              const enoughCount = (account.data?.depositCount ?? 0) >= offer.minimumDepositCount;
              const blocked = !account.data || hasActiveApplication || apply.isPending || !enoughDeposit || !enoughCount;
              return <>
                <Pressable disabled={blocked} onPress={() => apply.mutate(offer.offerId)} style={[styles.primary, blocked && styles.disabled]}><Text style={styles.primaryText}>{hasActiveApplication ? "다른 청약 진행 중" : "청약 신청"}</Text></Pressable>
                {account.data && !hasActiveApplication && !enoughDeposit ? <Text style={styles.description}>청약통장에 {required.toLocaleString("ko-KR")} {currency} 이상 있어야 신청할 수 있어요. 지금은 {deposited.toLocaleString("ko-KR")} {currency}예요.</Text> : null}
                {account.data && !hasActiveApplication && enoughDeposit && !enoughCount ? <Text style={styles.description}>납입을 {offer.minimumDepositCount}회 이상 해야 신청할 수 있어요.</Text> : null}
              </>;
            })()}
          </StateCard>;
        }) : <StateCard title="진행 중인 청약이 없어요"><Text style={styles.description}>선생님이 청약 공급을 열면 여기에 공고가 나타나요.</Text></StateCard>}

        {applications.data?.some((item) => ["WON", "LOST", "PURCHASED"].includes(item.status)) ? <><Text style={styles.subTitle}>청약 결과</Text>{applications.data.filter((item) => ["WON", "LOST", "PURCHASED"].includes(item.status)).map((item) => <StateCard key={item.applicationId} tone={item.status === "WON" || item.status === "PURCHASED" ? "mint" : undefined}><Text style={styles.cardTitle}>{applicationStatusLabel(item.status)}</Text><Text style={styles.cardMeta}>신청 당시 납입 {item.depositCount}회 · 예치금 {item.depositAmount.toLocaleString("ko-KR")} {currency}</Text>{item.status === "WON" && item.assignedPropertyId ? <Pressable onPress={() => setSelectedId(item.assignedPropertyId!)} style={styles.primary}><Text style={styles.primaryText}>당첨 자리 확인</Text></Pressable> : null}</StateCard>)}</> : null}
      </View> : <StateCard><Text style={styles.cardTitle}>청약 모듈은 꺼져 있어요</Text><Text style={styles.description}>현재는 교사 직판과 학생 간 재판매만 이용할 수 있어요.</Text></StateCard>}
    </Screen>
  );
}

const styles = StyleSheet.create({
  lead: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  description: { color: colors.muted700, fontSize: 15, lineHeight: 22 },
  notice: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 12, paddingHorizontal: 16, paddingVertical: 13, borderRadius: radii.control, backgroundColor: "#FFF5D8" },
  noticeText: { flex: 1, color: colors.ink900, fontSize: 15, fontWeight: "800" },
  noticeClose: { color: colors.orange700, fontWeight: "900" },
  section: { gap: 12 }, sectionHead: { gap: 8 }, sectionTitle: { color: colors.ink900, fontSize: 22, fontWeight: "900" }, subTitle: { marginTop: 4, color: colors.ink900, fontSize: 18, fontWeight: "900" },
  legend: { flexDirection: "row", flexWrap: "wrap", gap: 12 }, directText: { color: colors.green700, fontWeight: "800" }, resaleText: { color: colors.orange700, fontWeight: "800" }, subscriptionText: { color: colors.purple500, fontWeight: "800" }, ownedText: { color: colors.muted700, fontWeight: "800" },
  board: { width: "60%", alignSelf: "center", alignItems: "center", padding: 9, borderWidth: 4, borderColor: "#91A7B5", borderRadius: 6, backgroundColor: colors.white }, boardText: { color: colors.muted700, fontSize: 12, fontWeight: "900", letterSpacing: 8 },
  seatScrollContent: { minWidth: "100%", alignItems: "center" }, seatStage: { gap: 18 }, seatGrid: { flexDirection: "row", flexWrap: "wrap", gap: SEAT_GAP }, seat: { minHeight: 88, alignItems: "center", justifyContent: "center", padding: 8, borderWidth: 1.5, borderRadius: 13 }, seatDirect: { borderColor: colors.green500, backgroundColor: colors.washMint }, seatResale: { borderColor: colors.orange500, backgroundColor: "#FFF3E4" }, seatSubscription: { borderColor: colors.purple500, backgroundColor: "#F2EDFF" }, seatOwned: { borderColor: "#AAB6C1", backgroundColor: "#F0F3F6" }, seatMine: { borderColor: "#2B82B5", backgroundColor: colors.washBlue }, seatSelected: { borderWidth: 3 }, pressed: { opacity: .75 },
  seatCoordinate: { color: colors.ink900, fontSize: 17, fontWeight: "900" }, seatStatus: { marginTop: 3, color: colors.muted700, fontSize: 11, fontWeight: "800" }, seatPrice: { marginTop: 4, color: colors.ink900, fontSize: 11, fontWeight: "900" },
  detailHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 16 }, detailPrice: { color: colors.green700, fontSize: 20, fontWeight: "900" }, cardTitle: { color: colors.ink900, fontSize: 18, fontWeight: "900" }, cardMeta: { color: colors.muted700, fontSize: 14, fontWeight: "700" }, offerPrice: { color: colors.purple500, fontSize: 18, fontWeight: "900" }, balance: { color: colors.purple500, fontSize: 30, fontWeight: "900" },
  inlineForm: { marginTop: 8, flexDirection: "row", alignItems: "center", flexWrap: "wrap", gap: 10 }, input: { minWidth: 120, minHeight: 48, paddingHorizontal: 14, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white, color: colors.ink900, fontSize: 17, fontWeight: "800" },
  primary: { alignSelf: "flex-start", minHeight: 48, justifyContent: "center", marginTop: 10, paddingHorizontal: 18, borderRadius: radii.control, backgroundColor: colors.green700 }, orangeButton: { minHeight: 48, justifyContent: "center", paddingHorizontal: 18, borderRadius: radii.control, backgroundColor: colors.orange700 }, primaryText: { color: colors.white, fontSize: 15, fontWeight: "900" }, secondary: { alignSelf: "flex-start", minHeight: 44, justifyContent: "center", marginTop: 8, paddingHorizontal: 16, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white }, secondaryText: { color: colors.ink900, fontWeight: "900" }, disabled: { opacity: .45 },
  applicationRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 12 }, applicationStatus: { color: colors.purple500, fontSize: 16, fontWeight: "900" },
  contractTypes: { marginTop: 14, flexDirection: "row", flexWrap: "wrap", gap: 8 }, contractType: { minHeight: 44, justifyContent: "center", paddingHorizontal: 16, paddingVertical: 10, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.chip, backgroundColor: colors.white }, contractTypeSelected: { borderColor: colors.green700, backgroundColor: colors.washMint }, contractTypeText: { color: colors.muted700, fontWeight: "900" }, contractTypeTextSelected: { color: colors.green700 }
});
