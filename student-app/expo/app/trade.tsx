import { useMutation, useQuery } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { Alert, Platform, Pressable, StyleSheet, Text, TextInput, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { fetchTradeClassmates } from "@/features/trade/api";
import { useTrades, type TradeRequest } from "@/features/trade/TradeProvider";
import { createTrade } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

type Tab = "request" | "received" | "progress";

export default function TradeScreen() {
  const { identity } = useAuth();
  const currencyName = identity?.currencyName ?? "비빔";
  const { trades, refresh, decideIncomingTrade } = useTrades();
  const [tab, setTab] = useState<Tab>("request");
  const [friendId, setFriendId] = useState("");
  const [friendQuery, setFriendQuery] = useState("");
  const [amount, setAmount] = useState("10");
  const [reason, setReason] = useState("");
  const classmates = useQuery({
    queryKey: ["trade-classmates", identity?.classroomId],
    queryFn: () => fetchTradeClassmates(identity!.classroomId),
    enabled: Boolean(identity?.classroomId)
  });

  const received = useMemo(() => trades.filter((trade) => (
    trade.direction === "INCOMING" && trade.status === "AWAITING_COUNTERPART"
  )), [trades]);
  const progress = useMemo(() => trades.filter((trade) => (
    trade.direction === "OUTGOING" || trade.status !== "AWAITING_COUNTERPART"
  )), [trades]);
  const filteredClassmates = useMemo(() => {
    const query = friendQuery.trim();
    return query
      ? (classmates.data ?? []).filter((friend) => friend.nickname.includes(query))
      : [];
  }, [classmates.data, friendQuery]);
  const parsedAmount = Number(amount);
  const selectedFriend = classmates.data?.find((friend) => friend.studentId === friendId) ?? null;
  const canRequest = Boolean(
    selectedFriend
      && reason.trim()
      && Number.isInteger(parsedAmount)
      && parsedAmount > 0
  );

  const requestTrade = useMutation({
    mutationFn: async () => {
      if (!identity?.classroomId || !canRequest) {
        throw new Error("친구와 금액, 거래 사유를 확인해 주세요.");
      }
      await createTrade(identity.classroomId, friendId, reason.trim(), parsedAmount);
    },
    onSuccess: async () => {
      setReason("");
      setAmount("10");
      setFriendId("");
      setFriendQuery("");
      await refresh();
      setTab("progress");
    }
  });
  const decide = useMutation({
    mutationFn: ({ tradeId, decision }: { tradeId: string; decision: "ACCEPT" | "REJECT" }) => (
      decideIncomingTrade(tradeId, decision)
    )
  });

  const confirmTradeRequest = () => {
    if (!selectedFriend || !canRequest) return;
    const message = `${selectedFriend.rosterNumber}번 ${selectedFriend.nickname} 학생에게 ${parsedAmount.toLocaleString("ko-KR")} ${currencyName}을 보낼까요?\n\n거래 사유: ${reason.trim()}`;
    if (Platform.OS === "web") {
      if (globalThis.confirm(message)) requestTrade.mutate();
      return;
    }
    Alert.alert("거래 신청 확인", message, [
      { text: "다시 확인", style: "cancel" },
      { text: "거래 신청", onPress: () => requestTrade.mutate() }
    ]);
  };

  return <Screen title="거래소" right={<BackButton />}>
    <View accessibilityRole="tablist" style={styles.tabRow}>
      <TabButton active={tab === "request"} label="거래 신청" onPress={() => setTab("request")} />
      <TabButton active={tab === "received"} label={`받은 거래${received.length ? ` ${received.length}` : ""}`} onPress={() => setTab("received")} />
      <TabButton active={tab === "progress"} label="내 거래 현황" onPress={() => setTab("progress")} />
    </View>

    {tab === "request" ? <View style={styles.form}>
      <Text style={styles.sectionTitle}>거래 신청</Text>
      <Text style={styles.fieldLabel}>친구 검색</Text>
      <TextInput accessibilityLabel="친구 검색" value={friendQuery} onChangeText={setFriendQuery} placeholder="친구 이름 검색" placeholderTextColor="#9AAABD" style={styles.friendSearch} />
      <View accessibilityRole="radiogroup" style={styles.friendList}>
        {filteredClassmates.map((friend) => <Pressable
          accessibilityRole="radio"
          accessibilityState={{ checked: friendId === friend.studentId }}
          aria-checked={friendId === friend.studentId}
          key={friend.studentId}
          onPress={() => setFriendId(friend.studentId)}
          style={[styles.friend, friendId === friend.studentId && styles.friendSelected]}
        >
          <Text style={styles.friendAvatar}>{friend.nickname.slice(0, 1)}</Text>
          <Text style={styles.friendName}>{friend.rosterNumber}번 {friend.nickname}</Text>
        </Pressable>)}
        {friendQuery.trim() && !filteredClassmates.length ? <Text style={styles.noFriend}>
          {classmates.isPending ? "친구 목록을 불러오는 중이에요." : "찾는 친구가 없어요."}
        </Text> : null}
      </View>
      {selectedFriend ? <Text accessibilityLiveRegion="polite" style={styles.selectedFriend}>
        받는 학생: {selectedFriend.rosterNumber}번 {selectedFriend.nickname}
      </Text> : null}
      {classmates.isError ? <Text style={styles.error}>{classmates.error.message}</Text> : null}
      <Text style={styles.fieldLabel}>보낼 금액</Text>
      <View style={styles.amountField}>
        <TextInput accessibilityLabel="거래 금액" keyboardType="number-pad" value={amount} onChangeText={setAmount} style={styles.amountInput} />
        <Text style={styles.amountSuffix}>{currencyName}</Text>
      </View>
      <Text style={styles.fieldLabel}>거래 사유</Text>
      <TextInput accessibilityLabel="거래 사유" value={reason} onChangeText={setReason} placeholder="예: 모둠 활동 재료를 함께 준비해요" placeholderTextColor="#9AAABD" multiline style={styles.reasonInput} />
      {!canRequest ? <Text style={styles.validationHint}>친구·금액·거래 사유를 모두 입력하면 신청할 수 있어요.</Text> : null}
      {requestTrade.isError ? <Text accessibilityLiveRegion="assertive" style={styles.error}>{requestTrade.error.message}</Text> : null}
      <Pressable accessibilityRole="button" disabled={!canRequest || requestTrade.isPending} onPress={confirmTradeRequest} style={[styles.submit, (!canRequest || requestTrade.isPending) && styles.submitDisabled]}>
        <Text style={styles.submitText}>{requestTrade.isPending ? "신청 중이에요" : "거래 신청하기"}</Text>
      </Pressable>
    </View> : null}

    {tab === "received" ? <>
      {decide.isError ? <Text style={styles.error}>{decide.error.message}</Text> : null}
      <TradeList empty="받은 거래 신청이 없어요." trades={received} action={(trade) => <View style={styles.actions}>
        <SmallButton label="거절" tone="light" onPress={() => decide.mutate({ tradeId: trade.id, decision: "REJECT" })} />
        <SmallButton label="수락" tone="green" onPress={() => decide.mutate({ tradeId: trade.id, decision: "ACCEPT" })} />
      </View>} />
    </> : null}
    {tab === "progress" ? <TradeList empty="아직 거래 내역이 없어요." trades={progress} /> : null}
  </Screen>;
}

function TabButton({ active, label, onPress }: { active: boolean; label: string; onPress(): void }) {
  return <Pressable accessibilityRole="tab" accessibilityState={{ selected: active }} aria-selected={active} onPress={onPress} style={[styles.tab, active && styles.tabActive]}><Text style={[styles.tabText, active && styles.tabTextActive]}>{label}</Text></Pressable>;
}

function TradeList({ trades, empty, action }: { trades: TradeRequest[]; empty: string; action?: (trade: TradeRequest) => ReactNode }) {
  const { identity } = useAuth();
  const currencyName = identity?.currencyName ?? "비빔";
  if (!trades.length) return <View style={styles.empty}><Text style={styles.emptyText}>{empty}</Text></View>;
  return <View style={styles.list}>{trades.map((trade) => <View key={trade.id} style={styles.tradeRow}>
    <View style={styles.tradeAvatar}><Text style={styles.tradeAvatarText}>{trade.counterpartName.slice(0, 1)}</Text></View>
    <View style={styles.tradeInfo}><Text style={styles.tradeName}>{trade.counterpartName}</Text><Text style={styles.tradeReason}>{trade.reason}</Text><Text style={styles.tradeStatus}>{statusLabel(trade.status)}</Text></View>
    <View style={styles.tradeRight}><Text style={styles.tradeAmount}>{trade.amount} {currencyName}</Text>{action?.(trade)}</View>
  </View>)}</View>;
}

function SmallButton({ label, tone, onPress }: { label: string; tone: "green" | "light"; onPress(): void }) {
  return <Pressable accessibilityRole="button" onPress={onPress} style={[styles.smallButton, tone === "green" && styles.smallButtonGreen]}><Text style={[styles.smallButtonText, tone === "green" && styles.smallButtonTextGreen]}>{label}</Text></Pressable>;
}

function statusLabel(status: string) {
  return ({
    AWAITING_COUNTERPART: "친구 응답 대기",
    AWAITING_TEACHER: "선생님 승인 대기",
    SETTLING: "정산 중",
    COMPLETED: "거래 완료",
    CANCELED: "신청 취소",
    DECLINED: "친구가 거절했어요",
    EXPIRED: "기한 만료",
    REJECTED: "선생님이 거절했어요",
    FAILED: "정산 실패"
  } as Record<string, string>)[status] ?? "진행 중";
}

const styles = StyleSheet.create({
  tabRow: { flexDirection: "row", gap: 8, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  tab: { paddingHorizontal: 16, paddingVertical: 14, borderBottomWidth: 3, borderBottomColor: "transparent" },
  tabActive: { borderBottomColor: colors.green700 },
  tabText: { color: colors.muted700, fontSize: 16, fontWeight: "800" },
  tabTextActive: { color: colors.ink900 },
  form: { maxWidth: 820, paddingTop: 6, gap: 11 },
  sectionTitle: { marginBottom: 6, color: colors.ink900, fontSize: 21, fontWeight: "900" },
  fieldLabel: { marginTop: 8, color: colors.muted700, fontSize: 14, fontWeight: "800" },
  friendList: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  friend: { minWidth: 132, flexDirection: "row", alignItems: "center", gap: 9, padding: 10, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white },
  friendSelected: { borderColor: colors.green700, backgroundColor: colors.washMint },
  friendAvatar: { width: 30, height: 30, paddingTop: 6, borderRadius: 15, overflow: "hidden", backgroundColor: "#E1EAF3", color: colors.ink900, fontSize: 14, fontWeight: "900", textAlign: "center" },
  friendName: { color: colors.ink900, fontSize: 15, fontWeight: "800" },
  friendSearch: { height: 46, paddingHorizontal: 14, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white, color: colors.ink900, fontSize: 16 },
  noFriend: { paddingVertical: 16, color: colors.muted700, fontSize: 15, fontWeight: "700" },
  selectedFriend: { alignSelf: "flex-start", paddingHorizontal: 12, paddingVertical: 8, borderRadius: radii.control, backgroundColor: colors.washMint, color: colors.green700, fontSize: 14, fontWeight: "900" },
  amountField: { width: "100%", maxWidth: 360, minWidth: 0, flexDirection: "row", alignItems: "center", borderBottomWidth: 2, borderBottomColor: colors.lineSoft },
  amountInput: { flex: 1, minWidth: 0, paddingVertical: 10, color: colors.ink900, fontSize: 21, fontWeight: "900" },
  amountSuffix: { flexShrink: 0, marginLeft: 8, paddingBottom: 2, color: colors.muted700, fontSize: 16, fontWeight: "800" },
  reasonInput: { minHeight: 88, padding: 14, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white, color: colors.ink900, fontSize: 16, textAlignVertical: "top" },
  validationHint: { color: colors.muted700, fontSize: 13, fontWeight: "700" },
  submit: { alignSelf: "flex-start", minHeight: 48, justifyContent: "center", marginTop: 10, paddingHorizontal: 25, paddingVertical: 14, borderRadius: radii.control, backgroundColor: colors.green700 },
  submitDisabled: { opacity: 0.55 },
  submitText: { color: colors.white, fontSize: 16, fontWeight: "900" },
  error: { color: colors.red700, fontSize: 14, fontWeight: "700" },
  list: { maxWidth: 880, borderTopWidth: 1, borderTopColor: colors.lineSoft },
  tradeRow: { minHeight: 100, flexDirection: "row", alignItems: "center", gap: 14, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  tradeAvatar: { width: 46, height: 46, alignItems: "center", justifyContent: "center", borderRadius: 23, backgroundColor: "#E7F1FA" },
  tradeAvatarText: { color: "#54849B", fontSize: 18, fontWeight: "900" },
  tradeInfo: { flex: 1, gap: 3 },
  tradeName: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  tradeReason: { color: colors.muted700, fontSize: 14 },
  tradeStatus: { color: colors.green700, fontSize: 13, fontWeight: "800" },
  tradeRight: { alignItems: "flex-end", gap: 8 },
  tradeAmount: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  actions: { flexDirection: "row", gap: 6 },
  smallButton: { paddingHorizontal: 11, paddingVertical: 7, borderRadius: 10, backgroundColor: "#F0F4F8" },
  smallButtonGreen: { backgroundColor: colors.green700 },
  smallButtonText: { color: colors.muted700, fontSize: 13, fontWeight: "800" },
  smallButtonTextGreen: { color: colors.white },
  empty: { minHeight: 170, alignItems: "center", justifyContent: "center", borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  emptyText: { color: colors.muted700, fontSize: 16, fontWeight: "700" }
});
