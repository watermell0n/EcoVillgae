import { useQuery } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchLoanOverview } from "@/features/student/api";
import { colors } from "@/theme/tokens";

const levelLabels: Record<string, string> = {
  GOOD: "우량",
  NORMAL: "보통",
  CAUTION: "주의",
  RESTRICTED: "제한"
};

function nextLevel(score: number) {
  if (score >= 750) return "현재 최고 등급이에요.";
  const target = score >= 600 ? 750 : score >= 450 ? 600 : 450;
  return `다음 등급까지 ${target - score}점 남았어요.`;
}

export default function CreditScreen() {
  const { identity } = useAuth();
  const credit = useQuery({
    queryKey: ["loan-overview", identity?.classroomId],
    queryFn: () => fetchLoanOverview(identity!.classroomId),
    enabled: Boolean(identity)
  });

  return <Screen title="신용등급" right={<BackButton />}>
    {credit.isPending ? <View style={styles.center}><ActivityIndicator color={colors.purple500} /><Text style={styles.helper}>신용 정보를 불러오고 있어요.</Text></View> : credit.isError ? <StateCard title="신용 정보를 불러오지 못했어요"><Text style={styles.error}>{credit.error.message}</Text><Pressable onPress={() => credit.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 불러오기</Text></Pressable></StateCard> : credit.data ? <>
      <StateCard tone="mint">
        <Text style={styles.eyebrow}>내 신용점수</Text>
        <Text style={styles.score}>{credit.data.creditScore}점</Text>
        <Text style={styles.level}>{levelLabels[credit.data.creditLevel] ?? credit.data.creditLevel}</Text>
        <Text style={styles.helper}>{nextLevel(credit.data.creditScore)}</Text>
      </StateCard>
      <StateCard title="신용이 대출에 미치는 영향">
        <Text style={styles.copy}>현재 신청 가능 한도는 {credit.data.availableLimit.toLocaleString("ko-KR")} {identity?.currencyName}이고, 적용 예정 금리는 {(credit.data.interestRateBps / 100).toFixed(2)}%예요.</Text>
        <Text style={styles.copy}>제때 갚으면 점수가 오르고, 연체하면 내려가요. 모든 대출을 상환하면 완납 점수도 받을 수 있어요.</Text>
      </StateCard>
      <StateCard title="최근 신용 변화">
        {credit.data.creditEvents.length ? credit.data.creditEvents.map((event) => <View key={event.eventId} style={styles.eventRow}><View><Text style={styles.eventTitle}>{event.reason}</Text><Text style={styles.eventDate}>{new Date(event.occurredAt).toLocaleDateString("ko-KR")}</Text></View><Text style={[styles.delta, event.scoreDelta < 0 && styles.negative]}>{event.scoreDelta > 0 ? "+" : ""}{event.scoreDelta}점</Text></View>) : <Text style={styles.helper}>아직 신용점수 변화 기록이 없어요.</Text>}
      </StateCard>
    </> : null}
  </Screen>;
}

const styles = StyleSheet.create({
  center: { alignItems: "center", gap: 12, paddingVertical: 30 },
  eyebrow: { color: colors.muted600, fontSize: 14, fontWeight: "800" },
  score: { marginTop: 6, color: colors.purple500, fontSize: 46, fontWeight: "900" },
  level: { marginTop: 4, color: colors.ink900, fontSize: 20, fontWeight: "900" },
  helper: { color: colors.muted600, fontSize: 14, lineHeight: 21 },
  copy: { marginTop: 8, color: colors.muted600, fontSize: 15, lineHeight: 23 },
  eventRow: { minHeight: 60, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 14, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  eventTitle: { color: colors.ink900, fontSize: 14, fontWeight: "800" },
  eventDate: { marginTop: 3, color: colors.muted600, fontSize: 12 },
  delta: { color: colors.green500, fontSize: 16, fontWeight: "900" },
  negative: { color: colors.red500 },
  error: { color: colors.red500, fontWeight: "700" },
  retry: { alignSelf: "flex-start", marginTop: 12, paddingHorizontal: 16, paddingVertical: 10, borderRadius: 8, backgroundColor: colors.purple500 },
  retryText: { color: colors.white, fontWeight: "900" }
});
