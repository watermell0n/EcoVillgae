import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, TextInput, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import {
  applicationStatusLabel,
  contractTypeLabel,
  depositSubscription,
  fetchAvailableProperties,
  fetchMyProperties,
  fetchSubscriptionAccount,
  fetchSubscriptionApplications,
  openSubscriptionAccount,
  propertyStatusLabel,
  requestPropertyContract,
  selectableContractTypes,
  type ContractType,
  type Property
} from "@/features/property/api";
import { colors, radii } from "@/theme/tokens";

export default function RealEstateScreen() {
  const { identity } = useAuth();
  const { enabled } = useClassroom();
  const queryClient = useQueryClient();
  const classroomId = identity?.classroomId;
  const currency = identity?.currencyName ?? "비빔";
  const subscriptionEnabled = enabled("subscription");
  const [selected, setSelected] = useState<Property | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [depositInput, setDepositInput] = useState("10");

  const available = useQuery({
    queryKey: ["properties-available", classroomId],
    queryFn: () => fetchAvailableProperties(classroomId!),
    enabled: Boolean(classroomId)
  });
  const mine = useQuery({
    queryKey: ["properties-mine", classroomId],
    queryFn: () => fetchMyProperties(classroomId!),
    enabled: Boolean(classroomId)
  });
  const account = useQuery({
    queryKey: ["subscription-account", classroomId],
    queryFn: () => fetchSubscriptionAccount(classroomId!),
    enabled: Boolean(classroomId) && subscriptionEnabled
  });
  const applications = useQuery({
    queryKey: ["subscription-applications", classroomId],
    queryFn: () => fetchSubscriptionApplications(classroomId!),
    enabled: Boolean(classroomId) && subscriptionEnabled
  });

  const refreshProperties = () => {
    queryClient.invalidateQueries({ queryKey: ["properties-available", classroomId] });
    queryClient.invalidateQueries({ queryKey: ["properties-mine", classroomId] });
  };

  const contract = useMutation({
    mutationFn: ({ property, type }: { property: Property; type: ContractType }) =>
      requestPropertyContract(property.propertyId, type, applications.data?.find((item) => item.status === "WON" && item.assignedPropertyId === property.propertyId)?.applicationId),
    onSuccess: () => {
      setSelected(null);
      setNotice("계약을 신청했어요. 선생님이 승인하면 시작돼요.");
      refreshProperties();
    },
    onError: (error: Error) => setNotice(error.message)
  });

  const openAccount = useMutation({
    mutationFn: () => openSubscriptionAccount(classroomId!),
    onSuccess: () => {
      setNotice("청약통장을 개설했어요.");
      account.refetch();
    },
    onError: (error: Error) => setNotice(error.message)
  });

  const deposit = useMutation({
    mutationFn: (amount: number) => depositSubscription(classroomId!, amount),
    onSuccess: () => {
      setNotice("청약통장에 납입했어요.");
      account.refetch();
      queryClient.invalidateQueries({ queryKey: ["wallet", classroomId] });
    },
    onError: (error: Error) => setNotice(error.message)
  });

  const depositAmount = Number(depositInput);
  const depositValid = Number.isInteger(depositAmount) && depositAmount >= 1;

  return (
    <Screen title="부동산" right={<BackButton />}>
      {notice ? (
        <Pressable onPress={() => setNotice(null)} style={styles.notice}>
          <Text style={styles.noticeText}>{notice}</Text>
          <Text style={styles.noticeClose}>닫기</Text>
        </Pressable>
      ) : null}

      <StateCard tone="sky">
        <Text style={styles.lead}>교실 자리를 빌리거나 살 수 있어요</Text>
        <Text style={styles.description}>
          월세는 정해진 주기마다 임대료를 내고, 전세는 보증금을 맡겨요. 매매는 자리를 완전히 사는 거예요.
          계약을 신청하면 선생님이 확인한 뒤 시작돼요.
        </Text>
      </StateCard>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>내 자리</Text>
        {mine.isPending ? (
          <ActivityIndicator color={colors.orange500} />
        ) : mine.error ? (
          <StateCard title="내 자리를 불러오지 못했어요"><Text style={styles.description}>{mine.error.message}</Text></StateCard>
        ) : mine.data?.length ? (
          <View style={styles.grid}>
            {mine.data.map((property) => (
              <StateCard key={property.propertyId} tone="mint">
                <Text style={styles.cardTitle}>{property.displayName}</Text>
                <Text style={styles.cardMeta}>{property.coordinate} · {propertyStatusLabel(property.status)}</Text>
              </StateCard>
            ))}
          </View>
        ) : (
          <StateCard title="아직 내 자리가 없어요"><Text style={styles.description}>아래에서 마음에 드는 자리를 골라 계약을 신청해요.</Text></StateCard>
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>계약할 수 있는 자리</Text>
        {available.isPending ? (
          <ActivityIndicator color={colors.orange500} />
        ) : available.error ? (
          <StateCard title="자리 목록을 불러오지 못했어요">
            <Text style={styles.description}>{available.error.message}</Text>
            <Pressable onPress={() => available.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 불러오기</Text></Pressable>
          </StateCard>
        ) : available.data?.length ? (
          <View style={styles.grid}>
            {available.data.map((property) => {
              const types = selectableContractTypes(property);
              return (
                <Pressable
                  accessibilityRole="button"
                  disabled={types.length === 0}
                  key={property.propertyId}
                  onPress={() => { setNotice(null); setSelected(property); }}
                  style={({ pressed }) => [styles.property, types.length === 0 && styles.disabled, pressed && styles.pressed]}
                >
                  <Text style={styles.cardTitle}>{property.displayName}</Text>
                  <Text style={styles.cardMeta}>{property.coordinate} · {propertyStatusLabel(property.status)}</Text>
                  <View style={styles.priceRows}>
                    {property.allowsRent && property.monthlyRent > 0 ? <Text style={styles.price}>월세 {property.monthlyRent} {currency} / {property.paymentCycleDays}일</Text> : null}
                    {property.allowsJeonse && property.depositAmount > 0 ? <Text style={styles.price}>전세 보증금 {property.depositAmount} {currency}</Text> : null}
                    {property.allowsSale && property.salePrice > 0 ? <Text style={styles.price}>매매 {property.salePrice} {currency}</Text> : null}
                  </View>
                  <Text style={styles.action}>{types.length === 0 ? "지금은 신청할 수 없어요" : "계약 신청하기"}</Text>
                </Pressable>
              );
            })}
          </View>
        ) : (
          <StateCard title="지금 계약할 수 있는 자리가 없어요"><Text style={styles.description}>선생님이 자리를 열면 여기에 나타나요.</Text></StateCard>
        )}
      </View>

      {subscriptionEnabled ? (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>청약통장</Text>
          {account.isPending ? (
            <ActivityIndicator color={colors.purple500} />
          ) : account.error ? (
            <StateCard title="청약통장을 불러오지 못했어요"><Text style={styles.description}>{account.error.message}</Text></StateCard>
          ) : account.data ? (
            <StateCard tone="cream">
              <Text style={styles.balance}>{account.data.totalDepositAmount} {currency}</Text>
              <Text style={styles.cardMeta}>납입 {account.data.depositCount}회
                {account.data.autoDepositAmount ? ` · 자동납입 ${account.data.autoDepositAmount} ${currency} / ${account.data.autoDepositCycleDays}일` : ""}
              </Text>
              <View style={styles.depositRow}>
                <TextInput
                  accessibilityLabel="납입 금액"
                  inputMode="numeric"
                  keyboardType="number-pad"
                  onChangeText={setDepositInput}
                  style={styles.input}
                  value={depositInput}
                />
                <Pressable
                  disabled={!depositValid || deposit.isPending}
                  onPress={() => deposit.mutate(depositAmount)}
                  style={[styles.primary, (!depositValid || deposit.isPending) && styles.disabled]}
                >
                  <Text style={styles.primaryText}>{deposit.isPending ? "납입 중" : "납입하기"}</Text>
                </Pressable>
              </View>
              {!depositValid ? <Text style={styles.hint}>1 이상의 숫자를 넣어 주세요.</Text> : null}
            </StateCard>
          ) : (
            <StateCard title="청약통장이 아직 없어요">
              <Text style={styles.description}>청약통장을 만들고 꾸준히 납입하면 새 자리 청약에 신청할 수 있어요.</Text>
              <Pressable disabled={openAccount.isPending} onPress={() => openAccount.mutate()} style={[styles.primary, openAccount.isPending && styles.disabled]}>
                <Text style={styles.primaryText}>{openAccount.isPending ? "개설 중" : "청약통장 개설하기"}</Text>
              </Pressable>
            </StateCard>
          )}

          {applications.data?.length ? (
            <View style={styles.grid}>
              {applications.data.map((application) => (
                <StateCard key={application.applicationId}>
                  <Text style={styles.cardTitle}>{applicationStatusLabel(application.status)}</Text>
                  <Text style={styles.cardMeta}>납입 {application.depositCount}회 · {application.depositAmount} {currency}</Text>
                </StateCard>
              ))}
            </View>
          ) : null}
        </View>
      ) : null}

      <Modal animationType="fade" onRequestClose={() => setSelected(null)} transparent visible={Boolean(selected)}>
        <View style={styles.backdrop}>
          <View style={styles.sheet}>
            <Text style={styles.sheetTitle}>{selected?.displayName}</Text>
            <Text style={styles.description}>어떤 방법으로 계약할지 골라요. 신청하면 선생님 승인을 기다려요.</Text>
            {selected ? selectableContractTypes(selected).map((type) => (
              <Pressable
                disabled={contract.isPending}
                key={type}
                onPress={() => contract.mutate({ property: selected, type })}
                style={[styles.option, contract.isPending && styles.disabled]}
              >
                <Text style={styles.optionTitle}>{contractTypeLabel(type)}</Text>
                <Text style={styles.description}>
                  {type === "RENT" ? `${selected.paymentCycleDays}일마다 ${selected.monthlyRent} ${currency}` : null}
                  {type === "JEONSE" ? `보증금 ${selected.depositAmount} ${currency} · ${selected.contractTermDays}일` : null}
                  {type === "SALE" ? `${selected.salePrice} ${currency}에 사기` : null}
                </Text>
              </Pressable>
            )) : null}
            <Pressable onPress={() => setSelected(null)} style={styles.close}><Text style={styles.closeText}>닫기</Text></Pressable>
          </View>
        </View>
      </Modal>
    </Screen>
  );
}

const styles = StyleSheet.create({
  lead: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 15, lineHeight: 22 },
  section: { gap: 12 },
  sectionTitle: { color: colors.ink900, fontSize: 22, fontWeight: "900" },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 14 },
  property: {
    minWidth: 250,
    flexGrow: 1,
    flexBasis: "30%",
    gap: 6,
    padding: 18,
    borderWidth: 1.5,
    borderColor: colors.orange500,
    borderRadius: radii.card,
    backgroundColor: colors.white
  },
  pressed: { opacity: 0.8 },
  disabled: { opacity: 0.5 },
  cardTitle: { color: colors.ink900, fontSize: 18, fontWeight: "900" },
  cardMeta: { color: colors.muted600, fontSize: 14, fontWeight: "700" },
  priceRows: { marginTop: 4, gap: 3 },
  price: { color: colors.ink900, fontSize: 15, fontWeight: "800" },
  action: { marginTop: 10, color: colors.orange500, fontSize: 15, fontWeight: "900" },
  balance: { color: colors.purple500, fontSize: 30, fontWeight: "900" },
  depositRow: { marginTop: 8, flexDirection: "row", alignItems: "center", gap: 10 },
  input: {
    minWidth: 120,
    minHeight: 48,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: colors.lineSoft,
    borderRadius: radii.control,
    backgroundColor: colors.white,
    color: colors.ink900,
    fontSize: 17,
    fontWeight: "800"
  },
  hint: { color: colors.red500, fontSize: 13, fontWeight: "700" },
  primary: { alignSelf: "flex-start", minHeight: 48, justifyContent: "center", paddingHorizontal: 20, borderRadius: radii.control, backgroundColor: colors.green500 },
  primaryText: { color: colors.white, fontSize: 16, fontWeight: "900" },
  retry: { alignSelf: "flex-start", marginTop: 4, paddingHorizontal: 18, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.green500 },
  retryText: { color: colors.white, fontWeight: "900" },
  notice: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 12, paddingHorizontal: 16, paddingVertical: 13, borderRadius: radii.control, backgroundColor: "#FFF5D8" },
  noticeText: { flex: 1, color: colors.ink900, fontSize: 15, fontWeight: "800" },
  noticeClose: { color: colors.orange500, fontWeight: "900" },
  backdrop: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24, backgroundColor: "rgba(20,35,50,0.42)" },
  sheet: { width: "100%", maxWidth: 640, padding: 24, borderRadius: radii.panel, backgroundColor: colors.white },
  sheetTitle: { marginBottom: 8, color: colors.ink900, fontSize: 23, fontWeight: "900" },
  option: { marginTop: 12, padding: 16, gap: 5, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control },
  optionTitle: { color: colors.ink900, fontSize: 18, fontWeight: "900" },
  close: { alignSelf: "flex-end", marginTop: 18, paddingHorizontal: 18, paddingVertical: 11, borderRadius: radii.control, backgroundColor: "#EEF2F6" },
  closeText: { color: colors.ink900, fontWeight: "900" }
});
