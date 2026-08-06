import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, useWindowDimensions, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import {
  applicationStatusLabel,
  applySubscriptionOffer,
  cancelPropertySaleListing,
  cancelSubscriptionApplication,
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
  type Property
} from "@/features/property/api";
import { colors, radii } from "@/theme/tokens";

const SEAT_GAP = 10;

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
  const [depositInput, setDepositInput] = useState("10");
  const [resaleInput, setResaleInput] = useState("100");

  const layout = useQuery({
    queryKey: ["property-layout", classroomId],
    queryFn: () => fetchPropertyLayout(classroomId!),
    enabled: Boolean(classroomId && realEstateEnabled)
  });
  const mine = useQuery({
    queryKey: ["properties-mine", classroomId],
    queryFn: () => fetchMyProperties(classroomId!),
    enabled: Boolean(classroomId && realEstateEnabled)
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

  const mineIds = useMemo(() => new Set((mine.data ?? []).map((property) => property.propertyId)), [mine.data]);
  const selected = layout.data?.properties.find((property) => property.propertyId === selectedId) ?? null;
  const columns = Math.max(layout.data?.columnCount ?? 1, 1);
  const contentWidth = Math.min(width >= 768 ? width - 330 : width - 48, 1050);
  const seatWidth = Math.max(72, Math.min(150, Math.floor((contentWidth - (columns - 1) * SEAT_GAP) / columns)));
  const seatGridWidth = columns * seatWidth + (columns - 1) * SEAT_GAP;
  const wonApplication = selected ? applications.data?.find((item) => item.status === "WON" && item.assignedPropertyId === selected.propertyId) : undefined;
  const hasActiveApplication = applications.data?.some((item) => ["APPLIED", "WON"].includes(item.status)) ?? false;

  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ["property-layout", classroomId] }),
    queryClient.invalidateQueries({ queryKey: ["properties-mine", classroomId] }),
    queryClient.invalidateQueries({ queryKey: ["subscription-offers", classroomId] }),
    queryClient.invalidateQueries({ queryKey: ["subscription-applications", classroomId] }),
    queryClient.invalidateQueries({ queryKey: ["subscription-account", classroomId] })
  ]);

  const purchase = useMutation({
    mutationFn: ({ property, applicationId }: { property: Property; applicationId?: string }) => requestPropertyContract(property.propertyId, "SALE", applicationId),
    onSuccess: async () => { setNotice("구매를 신청했어요. 선생님이 승인하면 소유권이 이전돼요."); await refresh(); },
    onError: (error: Error) => setNotice(error.message)
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
  const canBuySelected = Boolean(selected && (
    (["AVAILABLE", "LISTED_FOR_SALE"].includes(selected.status) && selected.allowsSale && selected.salePrice > 0)
      || (selected.status === "SUBSCRIPTION_OPEN" && wonApplication)
  ));

  if (!realEstateEnabled) {
    return <Screen title="부동산" right={<BackButton />}><StateCard title="부동산 모듈이 꺼져 있어요"><Text style={styles.description}>선생님이 부동산 모듈을 열면 교실 좌석판을 볼 수 있어요. 청약은 부동산 모듈과 함께 켜져야 합니다.</Text></StateCard></Screen>;
  }

  return (
    <Screen title="부동산·청약" subtitle="교실 좌석 소유권" right={<BackButton />}>
      {notice ? <Pressable onPress={() => setNotice(null)} style={styles.notice}><Text style={styles.noticeText}>{notice}</Text><Text style={styles.noticeClose}>닫기</Text></Pressable> : null}

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
        <View style={styles.detailHead}><View><Text style={styles.cardTitle}>{selected.coordinate} · {selected.displayName}</Text><Text style={styles.cardMeta}>{mineIds.has(selected.propertyId) ? "내가 소유한 자리" : propertyStatusLabel(selected.status)}</Text></View>{selected.salePrice > 0 ? <Text style={styles.detailPrice}>{selected.salePrice.toLocaleString("ko-KR")} {currency}</Text> : null}</View>
        {canBuySelected ? <Pressable disabled={purchase.isPending} onPress={() => purchase.mutate({ property: selected, applicationId: wonApplication?.applicationId })} style={[styles.primary, purchase.isPending && styles.disabled]}><Text style={styles.primaryText}>{selected.status === "SUBSCRIPTION_OPEN" ? "당첨 자리 구매 신청" : "소유권 구매 신청"}</Text></Pressable> : null}
        {!mineIds.has(selected.propertyId) && !canBuySelected ? <Text style={styles.description}>{selected.status === "SUBSCRIPTION_OPEN" ? "청약 당첨 후 구매를 신청할 수 있어요." : "이미 소유자가 있어 지금은 구매할 수 없어요."}</Text> : null}
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
        {account.isPending ? <ActivityIndicator color={colors.purple500} /> : account.data ? <StateCard tone="cream">
          <Text style={styles.balance}>{account.data.totalDepositAmount.toLocaleString("ko-KR")} {currency}</Text><Text style={styles.cardMeta}>납입 {account.data.depositCount}회</Text>
          <View style={styles.inlineForm}><TextInput accessibilityLabel="청약통장 납입 금액" inputMode="numeric" keyboardType="number-pad" value={depositInput} onChangeText={setDepositInput} style={styles.input} /><Pressable disabled={!Number.isInteger(depositAmount) || depositAmount < 1 || deposit.isPending} onPress={() => deposit.mutate(depositAmount)} style={styles.primary}><Text style={styles.primaryText}>납입하기</Text></Pressable></View>
        </StateCard> : <StateCard title="청약통장이 아직 없어요"><Text style={styles.description}>통장을 개설하고 분양가 이상을 예치하면 청약에 신청할 수 있어요.</Text><Pressable disabled={openAccount.isPending} onPress={() => openAccount.mutate()} style={styles.primary}><Text style={styles.primaryText}>청약통장 개설</Text></Pressable></StateCard>}

        <Text style={styles.subTitle}>진행 중인 청약</Text>
        {offers.data?.filter((offer) => offer.status === "OPEN").length ? offers.data.filter((offer) => offer.status === "OPEN").map((offer) => {
          const application = applications.data?.find((item) => item.offerId === offer.offerId);
          return <StateCard key={offer.offerId}>
            <Text style={styles.cardTitle}>{offer.title}</Text><Text style={styles.cardMeta}>공급 {offer.supplyQuantity}석 · 신청 {offer.applicantCount}명 · {new Date(offer.closesAt).toLocaleString("ko-KR")} 마감</Text>
            <Text style={styles.offerPrice}>분양가 {offer.salePrice.toLocaleString("ko-KR")} {currency}</Text><Text style={styles.description}>조건: 납입 {offer.minimumDepositCount}회 · 예치금 {Math.max(offer.minimumBalance, offer.salePrice).toLocaleString("ko-KR")} {currency} 이상</Text>
            {application ? <View style={styles.applicationRow}><Text style={styles.applicationStatus}>{applicationStatusLabel(application.status)}</Text>{application.status === "APPLIED" ? <Pressable onPress={() => cancelApplication.mutate(application.applicationId)} style={styles.secondary}><Text style={styles.secondaryText}>신청 취소</Text></Pressable> : null}</View> : <Pressable disabled={!account.data || hasActiveApplication || apply.isPending} onPress={() => apply.mutate(offer.offerId)} style={[styles.primary, (!account.data || hasActiveApplication || apply.isPending) && styles.disabled]}><Text style={styles.primaryText}>{hasActiveApplication ? "다른 청약 진행 중" : "청약 신청"}</Text></Pressable>}
          </StateCard>;
        }) : <StateCard title="진행 중인 청약이 없어요"><Text style={styles.description}>선생님이 청약 공급을 열면 여기에 공고가 나타나요.</Text></StateCard>}

        {applications.data?.some((item) => ["WON", "LOST", "PURCHASED"].includes(item.status)) ? <><Text style={styles.subTitle}>청약 결과</Text>{applications.data.filter((item) => ["WON", "LOST", "PURCHASED"].includes(item.status)).map((item) => <StateCard key={item.applicationId} tone={item.status === "WON" || item.status === "PURCHASED" ? "mint" : undefined}><Text style={styles.cardTitle}>{applicationStatusLabel(item.status)}</Text><Text style={styles.cardMeta}>신청 당시 납입 {item.depositCount}회 · 예치금 {item.depositAmount.toLocaleString("ko-KR")} {currency}</Text>{item.status === "WON" && item.assignedPropertyId ? <Pressable onPress={() => setSelectedId(item.assignedPropertyId!)} style={styles.primary}><Text style={styles.primaryText}>당첨 자리 확인</Text></Pressable> : null}</StateCard>)}</> : null}
      </View> : <StateCard><Text style={styles.cardTitle}>청약 모듈은 꺼져 있어요</Text><Text style={styles.description}>현재는 교사 직판과 학생 간 재판매만 이용할 수 있어요.</Text></StateCard>}
    </Screen>
  );
}

const styles = StyleSheet.create({
  lead: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 15, lineHeight: 22 },
  notice: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 12, paddingHorizontal: 16, paddingVertical: 13, borderRadius: radii.control, backgroundColor: "#FFF5D8" },
  noticeText: { flex: 1, color: colors.ink900, fontSize: 15, fontWeight: "800" },
  noticeClose: { color: colors.orange500, fontWeight: "900" },
  section: { gap: 12 }, sectionHead: { gap: 8 }, sectionTitle: { color: colors.ink900, fontSize: 22, fontWeight: "900" }, subTitle: { marginTop: 4, color: colors.ink900, fontSize: 18, fontWeight: "900" },
  legend: { flexDirection: "row", flexWrap: "wrap", gap: 12 }, directText: { color: colors.green500, fontWeight: "800" }, resaleText: { color: colors.orange500, fontWeight: "800" }, subscriptionText: { color: colors.purple500, fontWeight: "800" }, ownedText: { color: colors.muted600, fontWeight: "800" },
  board: { width: "60%", alignSelf: "center", alignItems: "center", padding: 9, borderWidth: 4, borderColor: "#91A7B5", borderRadius: 6, backgroundColor: colors.white }, boardText: { color: colors.muted600, fontSize: 12, fontWeight: "900", letterSpacing: 8 },
  seatScrollContent: { minWidth: "100%", alignItems: "center" }, seatStage: { gap: 18 }, seatGrid: { flexDirection: "row", flexWrap: "wrap", gap: SEAT_GAP }, seat: { minHeight: 88, alignItems: "center", justifyContent: "center", padding: 8, borderWidth: 1.5, borderRadius: 13 }, seatDirect: { borderColor: colors.green500, backgroundColor: colors.washMint }, seatResale: { borderColor: colors.orange500, backgroundColor: "#FFF3E4" }, seatSubscription: { borderColor: colors.purple500, backgroundColor: "#F2EDFF" }, seatOwned: { borderColor: "#AAB6C1", backgroundColor: "#F0F3F6" }, seatMine: { borderColor: "#2B82B5", backgroundColor: colors.washBlue }, seatSelected: { borderWidth: 3 }, pressed: { opacity: .75 },
  seatCoordinate: { color: colors.ink900, fontSize: 17, fontWeight: "900" }, seatStatus: { marginTop: 3, color: colors.muted600, fontSize: 11, fontWeight: "800" }, seatPrice: { marginTop: 4, color: colors.ink900, fontSize: 11, fontWeight: "900" },
  detailHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 16 }, detailPrice: { color: colors.green500, fontSize: 20, fontWeight: "900" }, cardTitle: { color: colors.ink900, fontSize: 18, fontWeight: "900" }, cardMeta: { color: colors.muted600, fontSize: 14, fontWeight: "700" }, offerPrice: { color: colors.purple500, fontSize: 18, fontWeight: "900" }, balance: { color: colors.purple500, fontSize: 30, fontWeight: "900" },
  inlineForm: { marginTop: 8, flexDirection: "row", alignItems: "center", flexWrap: "wrap", gap: 10 }, input: { minWidth: 120, minHeight: 48, paddingHorizontal: 14, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white, color: colors.ink900, fontSize: 17, fontWeight: "800" },
  primary: { alignSelf: "flex-start", minHeight: 46, justifyContent: "center", marginTop: 10, paddingHorizontal: 18, borderRadius: radii.control, backgroundColor: colors.green500 }, orangeButton: { minHeight: 48, justifyContent: "center", paddingHorizontal: 18, borderRadius: radii.control, backgroundColor: colors.orange500 }, primaryText: { color: colors.white, fontSize: 15, fontWeight: "900" }, secondary: { alignSelf: "flex-start", minHeight: 42, justifyContent: "center", marginTop: 8, paddingHorizontal: 16, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white }, secondaryText: { color: colors.ink900, fontWeight: "900" }, disabled: { opacity: .45 },
  applicationRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 12 }, applicationStatus: { color: colors.purple500, fontSize: 16, fontWeight: "900" }
});
