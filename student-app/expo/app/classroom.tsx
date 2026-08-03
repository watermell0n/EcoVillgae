import { useMutation, useQuery } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { castVote, fetchGuidedActivity, fetchVote } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function ClassroomScreen() {
  const { identity } = useAuth();
  const { access } = useClassroom();
  const voteId = access?.resources.voteId;
  const activityId = access?.resources.guidedActivityId;
  const activity = useQuery({ queryKey: ["guided-activity", identity?.classroomId, activityId], queryFn: () => fetchGuidedActivity(identity!.classroomId, activityId!), enabled: Boolean(identity && activityId) });
  const vote = useQuery({ queryKey: ["class-vote", identity?.classroomId, voteId], queryFn: () => fetchVote(identity!.classroomId, voteId!), enabled: Boolean(identity && voteId) });
  const ballot = useMutation({ mutationFn: (optionId: string) => castVote(identity!.classroomId, vote.data!, optionId), onSuccess: () => vote.refetch() });

  return (
    <Screen title="선생님 수업" right={<BackButton />}>
      {activityId ? <StateCard tone="sky"><Text style={styles.activity}>{activity.isPending ? "수업 상태를 확인하고 있어요." : activity.data?.status === "LIVE" ? "선생님과 함께하는 수업이 진행 중이에요." : "수업 시작을 기다리고 있어요."}</Text></StateCard> : null}
      {!voteId ? (
        <StateCard title="지금 참여할 투표가 없어요"><Text style={styles.description}>선생님이 활동을 시작하면 이곳에 나타나요.</Text></StateCard>
      ) : vote.isPending ? <ActivityIndicator color={colors.yellow500} /> : vote.isError ? (
        <StateCard title="투표를 불러오지 못했어요"><Text style={styles.error}>{vote.error.message}</Text></StateCard>
      ) : vote.data ? (
        <>
          <StateCard title={vote.data.title} tone="cream"><Text style={styles.description}>{vote.data.description}</Text><Text style={styles.fund}>공동기금 {vote.data.publicFundBalance} 비빔</Text></StateCard>
          <View style={styles.options}>
            {vote.data.options.map((option) => (
              <Pressable disabled={ballot.isPending || ballot.isSuccess || vote.data.hasVoted || vote.data.status !== "OPEN"} key={option.optionId} onPress={() => ballot.mutate(option.optionId)} style={[styles.option, (vote.data.hasVoted || vote.data.status !== "OPEN") && styles.disabled]}>
                <Text style={styles.optionTitle}>{option.label}</Text><Text style={styles.description}>{option.description}</Text><Text style={styles.budget}>예산 {option.budgetAmount} 비빔</Text>
              </Pressable>
            ))}
          </View>
          {ballot.isSuccess || vote.data.hasVoted ? <Text style={styles.success}>투표를 제출했어요.</Text> : null}
          {ballot.isError ? <Text style={styles.error}>{ballot.error.message}</Text> : null}
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  activity: { color: colors.ink900, fontSize: 17, fontWeight: "800" },
  description: { color: colors.muted600, fontSize: 15, lineHeight: 22 },
  fund: { color: colors.orange500, fontSize: 20, fontWeight: "900" },
  options: { flexDirection: "row", flexWrap: "wrap", gap: 16 },
  option: { flexGrow: 1, minWidth: 260, minHeight: 150, padding: 20, gap: 8, borderRadius: radii.card, borderWidth: 1, borderColor: colors.lineSoft, backgroundColor: colors.white },
  optionTitle: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  budget: { color: colors.purple500, fontSize: 17, fontWeight: "900", marginTop: "auto" },
  disabled: { opacity: 0.5 },
  success: { color: colors.green500, fontSize: 16, fontWeight: "900" },
  error: { color: colors.red500, fontSize: 15, fontWeight: "800" }
});
