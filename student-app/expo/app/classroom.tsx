import { useMutation, useQuery } from "@tanstack/react-query";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { castVote, fetchVote } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function ClassroomScreen() {
  const { identity } = useAuth();
  const vote = useQuery({ queryKey: ["class-vote", identity?.classroomId], queryFn: () => fetchVote(identity!.classroomId), enabled: Boolean(identity) });
  const ballot = useMutation({ mutationFn: (optionId: string) => castVote(identity!.classroomId, vote.data!.voteId, optionId) });

  return (
    <Screen title="선생님 수업" subtitle="SCR-STU-012 · GUIDED 모드에서는 선생님이 연 활동만 표시합니다." right={<BackButton />}>
      <StateCard title={vote.data?.title ?? "수업 활동을 확인하고 있어요"} tone="cream">
        <Text style={styles.description}>{vote.data?.description ?? "잠시만 기다려 주세요."}</Text>
        {vote.data ? <Text style={styles.fund}>공동기금 {vote.data.publicFundBalance} 비빔</Text> : null}
      </StateCard>
      <View style={styles.options}>
        {vote.data?.options.map((option) => (
          <Pressable
            disabled={ballot.isPending || ballot.isSuccess}
            key={option.optionId}
            onPress={() => ballot.mutate(option.optionId)}
            style={[styles.option, ballot.isSuccess && styles.disabled]}
          >
            <Text style={styles.optionTitle}>{option.label}</Text>
            <Text style={styles.description}>{option.description}</Text>
            <Text style={styles.budget}>예산 {option.budgetAmount} 비빔</Text>
          </Pressable>
        ))}
      </View>
      {ballot.isSuccess ? <Text style={styles.success}>익명 투표가 안전하게 제출됐어요.</Text> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  description: { color: colors.muted600, fontSize: 15, lineHeight: 22 },
  fund: { color: colors.orange500, fontSize: 20, fontWeight: "900" },
  options: { flexDirection: "row", gap: 16 },
  option: { flex: 1, minHeight: 150, padding: 20, gap: 8, borderRadius: radii.card, borderWidth: 1, borderColor: colors.lineSoft, backgroundColor: colors.white },
  optionTitle: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  budget: { color: colors.purple500, fontSize: 17, fontWeight: "900", marginTop: "auto" },
  disabled: { opacity: 0.5 },
  success: { color: colors.green500, fontSize: 16, fontWeight: "900" }
});
