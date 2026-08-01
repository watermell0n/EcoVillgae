import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { createTrade } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function TradeScreen() {
  const { identity } = useAuth();
  const [amount, setAmount] = useState("30");
  const [description, setDescription] = useState("친구에게 30비빔 보내기");
  const trade = useMutation({ mutationFn: () => createTrade(identity!.classroomId, description.trim(), Number(amount)) });
  const invalid = !description.trim() || !Number.isFinite(Number(amount)) || Number(amount) <= 0;

  return (
    <Screen title="친구 거래소" subtitle="SCR-STU-010 · 제출 후 상대 학생과 교사의 승인을 기다립니다." right={<BackButton />}>
      <View style={styles.columns}>
        <StateCard title="거래 제안 만들기">
          <Text style={styles.label}>보낼 비빔</Text>
          <TextInput keyboardType="number-pad" onChangeText={setAmount} style={styles.input} value={amount} />
          <Text style={styles.label}>거래 메모</Text>
          <TextInput onChangeText={setDescription} style={styles.input} value={description} />
          <Pressable disabled={invalid || trade.isPending} onPress={() => trade.mutate()} style={[styles.action, (invalid || trade.isPending) && styles.disabled]}>
            <Text style={styles.actionText}>{trade.isPending ? "보내는 중" : "제안 보내기"}</Text>
          </Pressable>
          {trade.isError ? <Text style={styles.error}>{trade.error.message}</Text> : null}
        </StateCard>
        <StateCard title="거래 상태" tone="sky">
          {trade.data ? (
            <><Text style={styles.status}>{trade.data.status}</Text><Text style={styles.description}>거래 {trade.data.tradeId.slice(0, 8)} · 상태 버전 {trade.data.stateVersion}</Text></>
          ) : <Text style={styles.description}>제안을 보내면 서버가 확정한 상태가 여기에 표시돼요.</Text>}
        </StateCard>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  columns: { flexDirection: "row", gap: 18 },
  label: { color: colors.ink900, fontSize: 14, fontWeight: "800" },
  input: { minWidth: 400, minHeight: 48, paddingHorizontal: 14, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control, backgroundColor: colors.washBlue, color: colors.ink900, fontSize: 16 },
  action: { alignItems: "center", padding: 13, borderRadius: radii.control, backgroundColor: colors.green500 },
  actionText: { color: colors.white, fontWeight: "900" },
  disabled: { opacity: 0.5 },
  error: { color: colors.red500, fontWeight: "700" },
  status: { color: colors.purple500, fontSize: 24, fontWeight: "900" },
  description: { maxWidth: 430, color: colors.muted600, lineHeight: 22 }
});
