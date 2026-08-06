import { useMutation } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { useTrades, type TradeRequest, type TradeStatus } from "@/features/trade/TradeProvider";
import { createTrade } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

const classmates = ["김하늘", "박서윤", "이도윤", "최민지", "정서준", "한유진", "오지후", "윤서아", "강민재", "서윤아", "임도현", "배지민", "차유나", "문시우", "권유진", "신지호", "김도연", "박하준", "이예린", "최도현"].map((name, index) => ({ id: `mock-student-${String(index + 1).padStart(2, "0")}`, name }));
type Tab = "request" | "received" | "progress";

export default function TradeScreen() {
  const { identity } = useAuth();
  const currencyName = identity?.currencyName ?? "비빔";
  const { trades, createLocalTrade, decideIncomingTrade } = useTrades();
  const [tab, setTab] = useState<Tab>("request");
  const [friendId, setFriendId] = useState("");
  const [friendQuery, setFriendQuery] = useState("");
  const [amount, setAmount] = useState("10");
  const [reason, setReason] = useState("");
  const received = useMemo(() => trades.filter((trade) => trade.direction === "INCOMING" && trade.status === "AWAITING_COUNTERPART"), [trades]);
  const progress = useMemo(() => trades.filter((trade) => trade.direction === "OUTGOING" || trade.status !== "AWAITING_COUNTERPART"), [trades]);
  const filteredClassmates = useMemo(() => {
    const query = friendQuery.trim();
    return query ? classmates.filter((friend) => friend.name.includes(query)) : [];
  }, [friendQuery]);
  const requestTrade = useMutation({
    mutationFn: async () => {
      const selected = classmates.find((friend) => friend.id === friendId);
      const parsedAmount = Number(amount);
      if (!identity?.classroomId || !selected || !reason.trim() || !Number.isInteger(parsedAmount) || parsedAmount < 1) throw new Error("친구와 금액, 거래 사유를 확인해 주세요.");
      const result = await createTrade(identity.classroomId, selected.id, reason.trim(), parsedAmount);
      createLocalTrade({ id: result.tradeId, counterpartId: selected.id, counterpartName: selected.name, amount: parsedAmount, reason: reason.trim(), status: result.status as TradeStatus });
    },
    onSuccess: () => { setReason(""); setAmount("10"); setTab("progress"); }
  });

  return <Screen title="거래소" right={<BackButton />}>
    <View style={styles.tabRow}><TabButton active={tab === "request"} label="거래 신청" onPress={() => setTab("request")} /><TabButton active={tab === "received"} label={`받은 거래${received.length ? ` ${received.length}` : ""}`} onPress={() => setTab("received")} /><TabButton active={tab === "progress"} label="내 거래 현황" onPress={() => setTab("progress")} /></View>
    {tab === "request" ? <View style={styles.form}>
      <Text style={styles.sectionTitle}>거래 신청</Text><Text style={styles.fieldLabel}>친구 검색</Text>
      <TextInput accessibilityLabel="친구 검색" value={friendQuery} onChangeText={setFriendQuery} placeholder="친구 이름 검색" placeholderTextColor="#9AAABD" style={styles.friendSearch} />
      <View style={styles.friendList}>{filteredClassmates.map((friend) => <Pressable key={friend.id} onPress={() => setFriendId(friend.id)} style={[styles.friend, friendId === friend.id && styles.friendSelected]}><Text style={styles.friendAvatar}>{friend.name.slice(0, 1)}</Text><Text style={styles.friendName}>{friend.name}</Text></Pressable>)}{friendQuery.trim() && !filteredClassmates.length ? <Text style={styles.noFriend}>찾는 친구가 없어요.</Text> : null}</View>
      <Text style={styles.fieldLabel}>보낼 금액</Text><View style={styles.amountField}><TextInput accessibilityLabel="거래 금액" keyboardType="number-pad" value={amount} onChangeText={setAmount} style={styles.amountInput} /><Text style={styles.amountSuffix}>{currencyName}</Text></View>
      <Text style={styles.fieldLabel}>거래 사유</Text><TextInput accessibilityLabel="거래 사유" value={reason} onChangeText={setReason} placeholder="예: 모둠 활동 자료를 함께 준비해요" placeholderTextColor="#9AAABD" multiline style={styles.reasonInput} />
      {requestTrade.isError ? <Text style={styles.error}>{requestTrade.error.message}</Text> : null}<Pressable disabled={requestTrade.isPending} onPress={() => requestTrade.mutate()} style={[styles.submit, requestTrade.isPending && styles.submitDisabled]}><Text style={styles.submitText}>{requestTrade.isPending ? "신청 중이에요" : "거래 신청하기"}</Text></Pressable>
    </View> : null}
    {tab === "received" ? <TradeList empty="받은 거래 신청이 없어요." trades={received} action={(trade) => <View style={styles.actions}><SmallButton label="거절" tone="light" onPress={() => decideIncomingTrade(trade.id, "DECLINED")} /><SmallButton label="수락" tone="green" onPress={() => decideIncomingTrade(trade.id, "AWAITING_TEACHER")} /></View>} /> : null}
    {tab === "progress" ? <TradeList empty="아직 보낸 거래 신청이 없어요." trades={progress} /> : null}
  </Screen>;
}

function TabButton({ active, label, onPress }: { active: boolean; label: string; onPress(): void }) { return <Pressable onPress={onPress} style={[styles.tab, active && styles.tabActive]}><Text style={[styles.tabText, active && styles.tabTextActive]}>{label}</Text></Pressable>; }
function TradeList({ trades, empty, action }: { trades: TradeRequest[]; empty: string; action?: (trade: TradeRequest) => ReactNode }) { const { identity } = useAuth(); const currencyName = identity?.currencyName ?? "비빔"; if (!trades.length) return <View style={styles.empty}><Text style={styles.emptyText}>{empty}</Text></View>; return <View style={styles.list}>{trades.map((trade) => <View key={trade.id} style={styles.tradeRow}><View style={styles.tradeAvatar}><Text style={styles.tradeAvatarText}>{trade.counterpartName.slice(0, 1)}</Text></View><View style={styles.tradeInfo}><Text style={styles.tradeName}>{trade.counterpartName}</Text><Text style={styles.tradeReason}>{trade.reason}</Text><Text style={styles.tradeStatus}>{statusLabel(trade.status)}</Text></View><View style={styles.tradeRight}><Text style={styles.tradeAmount}>{trade.amount} {currencyName}</Text>{action?.(trade)}</View></View>)}</View>; }
function SmallButton({ label, tone, onPress }: { label: string; tone: "green" | "light"; onPress(): void }) { return <Pressable onPress={onPress} style={[styles.smallButton, tone === "green" && styles.smallButtonGreen]}><Text style={[styles.smallButtonText, tone === "green" && styles.smallButtonTextGreen]}>{label}</Text></Pressable>; }
function statusLabel(status: string) { return ({ AWAITING_COUNTERPART: "친구 응답 대기", AWAITING_TEACHER: "선생님 확인 대기", COMPLETED: "거래 완료", DECLINED: "친구가 거절했어요", REJECTED: "선생님이 거절했어요" } as Record<string, string>)[status] ?? "진행 중"; }

const styles = StyleSheet.create({
  headingRow: { flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between", gap: 18 }, eyebrow: { color: colors.muted600, fontSize: 14, fontWeight: "800" }, heading: { marginTop: 5, color: colors.ink900, fontSize: 25, fontWeight: "900" }, balance: { paddingHorizontal: 14, paddingVertical: 9, borderRadius: radii.chip, backgroundColor: colors.washMint, color: colors.green500, fontSize: 15, fontWeight: "900" },
  tabRow: { flexDirection: "row", gap: 8, borderBottomWidth: 1, borderBottomColor: colors.lineSoft }, tab: { paddingHorizontal: 16, paddingVertical: 14, borderBottomWidth: 3, borderBottomColor: "transparent" }, tabActive: { borderBottomColor: colors.green500 }, tabText: { color: colors.muted600, fontSize: 16, fontWeight: "800" }, tabTextActive: { color: colors.ink900 },
  form: { maxWidth: 820, paddingTop: 6, gap: 11 }, sectionTitle: { marginBottom: 6, color: colors.ink900, fontSize: 21, fontWeight: "900" }, fieldLabel: { marginTop: 8, color: colors.muted600, fontSize: 14, fontWeight: "800" }, friendList: { flexDirection: "row", flexWrap: "wrap", gap: 10 }, friend: { minWidth: 132, flexDirection: "row", alignItems: "center", gap: 9, padding: 10, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white }, friendSelected: { borderColor: colors.green500, backgroundColor: colors.washMint }, friendAvatar: { width: 30, height: 30, paddingTop: 6, borderRadius: 15, overflow: "hidden", backgroundColor: "#E1EAF3", color: colors.ink900, fontSize: 14, fontWeight: "900", textAlign: "center" }, friendName: { color: colors.ink900, fontSize: 15, fontWeight: "800" },
  friendSearch: { height: 46, paddingHorizontal: 14, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white, color: colors.ink900, fontSize: 16 }, noFriend: { paddingVertical: 16, color: colors.muted600, fontSize: 15, fontWeight: "700" },
  amountField: { width: 230, flexDirection: "row", alignItems: "center", borderBottomWidth: 2, borderBottomColor: colors.lineSoft }, amountInput: { flex: 1, paddingVertical: 10, color: colors.ink900, fontSize: 21, fontWeight: "900" }, amountSuffix: { paddingBottom: 2, color: colors.muted600, fontSize: 16, fontWeight: "800" }, reasonInput: { minHeight: 88, padding: 14, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.white, color: colors.ink900, fontSize: 16, textAlignVertical: "top" }, submit: { alignSelf: "flex-start", marginTop: 10, paddingHorizontal: 25, paddingVertical: 14, borderRadius: radii.control, backgroundColor: colors.green500 }, submitDisabled: { opacity: 0.55 }, submitText: { color: colors.white, fontSize: 16, fontWeight: "900" }, error: { color: colors.red500, fontSize: 14, fontWeight: "700" },
  list: { maxWidth: 880, borderTopWidth: 1, borderTopColor: colors.lineSoft }, tradeRow: { minHeight: 100, flexDirection: "row", alignItems: "center", gap: 14, borderBottomWidth: 1, borderBottomColor: colors.lineSoft }, tradeAvatar: { width: 46, height: 46, alignItems: "center", justifyContent: "center", borderRadius: 23, backgroundColor: "#E7F1FA" }, tradeAvatarText: { color: "#54849B", fontSize: 18, fontWeight: "900" }, tradeInfo: { flex: 1, gap: 3 }, tradeName: { color: colors.ink900, fontSize: 17, fontWeight: "900" }, tradeReason: { color: colors.muted600, fontSize: 14 }, tradeStatus: { color: colors.green500, fontSize: 13, fontWeight: "800" }, tradeRight: { alignItems: "flex-end", gap: 8 }, tradeAmount: { color: colors.ink900, fontSize: 17, fontWeight: "900" }, actions: { flexDirection: "row", gap: 6 }, smallButton: { paddingHorizontal: 11, paddingVertical: 7, borderRadius: 10, backgroundColor: "#F0F4F8" }, smallButtonGreen: { backgroundColor: colors.green500 }, smallButtonText: { color: colors.muted600, fontSize: 13, fontWeight: "800" }, smallButtonTextGreen: { color: colors.white }, empty: { minHeight: 170, alignItems: "center", justifyContent: "center", borderBottomWidth: 1, borderBottomColor: colors.lineSoft }, emptyText: { color: colors.muted600, fontSize: 16, fontWeight: "700" }
});
