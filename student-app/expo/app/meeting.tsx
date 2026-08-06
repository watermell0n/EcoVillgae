import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { castVote, fetchVote } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function MeetingScreen() {
  const { identity } = useAuth();
  const { access } = useClassroom();
  const [voteOpen, setVoteOpen] = useState(false);
  const voteId = access?.resources.voteId;
  const currency = identity?.currencyName ?? "비빔";

  const vote = useQuery({
    queryKey: ["class-vote", identity?.classroomId, voteId],
    queryFn: () => fetchVote(identity!.classroomId, voteId!),
    enabled: Boolean(identity && voteId)
  });
  const ballot = useMutation({
    mutationFn: (optionId: string) => castVote(identity!.classroomId, vote.data!, optionId),
    onSuccess: () => { setVoteOpen(false); vote.refetch(); }
  });

  return (
    <Screen title="마을 회의" right={<BackButton />}>
      <StateCard tone="sky">
        <Text style={styles.lead}>우리 반 공동기금을 어디에 쓸까요</Text>
        <Text style={styles.description}>친구들과 함께 사용처를 정해요. 한 번 투표하면 바꿀 수 없으니 잘 생각해서 골라요.</Text>
      </StateCard>

      {!voteId ? (
        <StateCard title="지금 참여할 투표가 없어요">
          <Text style={styles.description}>선생님이 투표를 열면 이곳에 나타나요.</Text>
        </StateCard>
      ) : vote.isPending ? (
        <ActivityIndicator color={colors.yellow500} />
      ) : vote.error ? (
        <StateCard title="투표를 불러오지 못했어요">
          <Text style={styles.description}>{vote.error.message}</Text>
          <Pressable onPress={() => vote.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 불러오기</Text></Pressable>
        </StateCard>
      ) : vote.data ? (
        <>
          <StateCard title={vote.data.title} tone="cream">
            <Text style={styles.description}>{vote.data.description}</Text>
            <Text style={styles.fund}>공동기금 {vote.data.publicFundBalance} {currency}</Text>
          </StateCard>
          <Pressable onPress={() => setVoteOpen(true)} style={styles.voteButton}>
            <Text style={styles.voteButtonText}>{vote.data.hasVoted ? "투표 내용 확인" : "사용처 투표하기"}</Text>
          </Pressable>
          <Modal animationType="fade" onRequestClose={() => setVoteOpen(false)} transparent visible={voteOpen}>
            <View style={styles.backdrop}>
              <View style={styles.sheet}>
                <Text style={styles.sheetTitle}>{vote.data.title}</Text>
                <Text style={styles.description}>{vote.data.description}</Text>
                {vote.data.options.map((option) => {
                  const closed = ballot.isPending || vote.data.hasVoted || vote.data.status !== "OPEN";
                  return (
                    <Pressable
                      disabled={closed}
                      key={option.optionId}
                      onPress={() => ballot.mutate(option.optionId)}
                      style={[styles.option, closed && styles.disabled]}
                    >
                      <Text style={styles.optionTitle}>{option.label}</Text>
                      <Text style={styles.description}>{option.description}</Text>
                      <Text style={styles.budget}>예산 {option.budgetAmount} {currency}</Text>
                    </Pressable>
                  );
                })}
                <Pressable onPress={() => setVoteOpen(false)} style={styles.close}><Text style={styles.closeText}>닫기</Text></Pressable>
              </View>
            </View>
          </Modal>
        </>
      ) : (
        <StateCard title="투표를 불러오지 못했어요" />
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  lead: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 15, lineHeight: 22 },
  fund: { marginTop: 8, color: colors.orange500, fontSize: 20, fontWeight: "900" },
  voteButton: { alignSelf: "flex-start", paddingHorizontal: 20, paddingVertical: 13, borderRadius: radii.control, backgroundColor: colors.yellow500 },
  voteButtonText: { color: colors.ink900, fontSize: 16, fontWeight: "900" },
  retry: { alignSelf: "flex-start", marginTop: 4, paddingHorizontal: 18, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.green500 },
  retryText: { color: colors.white, fontWeight: "900" },
  backdrop: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24, backgroundColor: "rgba(20,35,50,0.42)" },
  sheet: { width: "100%", maxWidth: 700, padding: 24, borderRadius: radii.panel, backgroundColor: colors.white },
  sheetTitle: { marginBottom: 10, color: colors.ink900, fontSize: 23, fontWeight: "900" },
  option: { marginTop: 12, padding: 16, gap: 6, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control },
  optionTitle: { color: colors.ink900, fontSize: 18, fontWeight: "900" },
  budget: { color: colors.purple500, fontWeight: "900" },
  disabled: { opacity: 0.5 },
  close: { alignSelf: "flex-end", marginTop: 18, paddingHorizontal: 18, paddingVertical: 11, borderRadius: radii.control, backgroundColor: "#EEF2F6" },
  closeText: { color: colors.ink900, fontWeight: "900" }
});
