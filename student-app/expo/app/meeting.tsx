import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { castVote, fetchLatestVote } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function MeetingScreen() {
  const { identity } = useAuth();
  const [voteOpen, setVoteOpen] = useState(false);
  const [selectedOptionId, setSelectedOptionId] = useState<string | null>(null);
  const currency = identity?.currencyName ?? "비빔";
  const vote = useQuery({
    queryKey: ["class-vote-latest", identity?.classroomId],
    queryFn: () => fetchLatestVote(identity!.classroomId),
    enabled: Boolean(identity?.classroomId),
    refetchInterval: 3_000
  });
  const ballot = useMutation({
    mutationFn: (optionId: string) => castVote(identity!.classroomId, vote.data!, optionId),
    onSuccess: async () => {
      setVoteOpen(false);
      setSelectedOptionId(null);
      await vote.refetch();
    }
  });
  const current = vote.data;
  const winner = current?.status === "RESULT_PUBLISHED"
    ? current.options.find((option) => option.optionId === current.selectedOptionId)
    : undefined;

  return (
    <Screen title="마을 회의" right={<BackButton />}>
      <StateCard tone="sky">
        <Text style={styles.lead}>우리 반 공동기금을 어디에 쓸까요</Text>
        <Text style={styles.description}>선생님이 투표를 열면 친구들과 함께 사용처를 정해요. 제출하기 전까지 선택을 바꿀 수 있어요.</Text>
      </StateCard>

      {vote.isPending ? (
        <View style={styles.state}><ActivityIndicator color={colors.yellow500} size="large" /><Text style={styles.description}>열린 투표를 확인하고 있어요.</Text></View>
      ) : vote.isError ? (
        <StateCard title="투표를 불러오지 못했어요">
          <Text style={styles.description}>{vote.error.message}</Text>
          <Pressable accessibilityRole="button" onPress={() => vote.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 불러오기</Text></Pressable>
        </StateCard>
      ) : !current ? (
        <StateCard title="지금 참여할 투표가 없어요">
          <Text style={styles.description}>선생님이 공동기금 사용처 투표를 만들고 시작하면 자동으로 나타나요.</Text>
        </StateCard>
      ) : current.status === "READY" ? (
        <StateCard title="선생님이 투표를 준비하고 있어요" tone="cream">
          <Text style={styles.description}>투표가 시작되면 선택할 수 있는 사용처가 이 화면에 공개돼요.</Text>
        </StateCard>
      ) : current.status === "CLOSED" ? (
        <StateCard title="투표가 마감됐어요" tone="cream">
          <Text style={styles.description}>{current.hasVoted ? "내 투표가 안전하게 반영됐어요. " : ""}선생님이 결과를 공개하고 공동기금 사용을 확정할 때까지 기다려 주세요.</Text>
        </StateCard>
      ) : current.status === "RESULT_PUBLISHED" ? (
        <>
          <StateCard title="투표 결과가 공개됐어요" tone="cream">
            <Text style={styles.resultTitle}>{winner?.label ?? current.selectedUse ?? "선정된 사용처"}</Text>
            {winner?.description ? <Text style={styles.description}>{winner.description}</Text> : null}
            <Text style={styles.fund}>사용 금액 {winner?.budgetAmount.toLocaleString("ko-KR") ?? 0} {currency}</Text>
            <Text style={styles.description}>현재 공동기금 {current.publicFundBalance.toLocaleString("ko-KR")} {currency}</Text>
          </StateCard>
          <View style={styles.resultList}>
            {current.options.map((option) => (
              <View key={option.optionId} style={[styles.resultRow, option.optionId === current.selectedOptionId && styles.resultWinner]}>
                <Text style={styles.optionTitle}>{option.label}</Text>
                <Text style={styles.resultCount}>{option.voteCount ?? 0}표</Text>
              </View>
            ))}
          </View>
        </>
      ) : (
        <>
          <StateCard title={current.title} tone="cream">
            <Text style={styles.description}>{current.description}</Text>
            <Text style={styles.fund}>공동기금 {current.publicFundBalance.toLocaleString("ko-KR")} {currency}</Text>
          </StateCard>
          {!current.eligible ? (
            <Text accessibilityRole="alert" style={styles.notice}>이번 투표의 참여 대상이 아니에요.</Text>
          ) : current.hasVoted ? (
            <Text accessibilityLiveRegion="polite" style={styles.complete}>투표를 완료했어요. 마감 후 결과가 공개됩니다.</Text>
          ) : (
            <Pressable accessibilityRole="button" onPress={() => { setSelectedOptionId(null); setVoteOpen(true); }} style={styles.voteButton}>
              <Text style={styles.voteButtonText}>사용처 투표하기</Text>
            </Pressable>
          )}
          <Modal animationType="fade" onRequestClose={() => setVoteOpen(false)} transparent visible={voteOpen}>
            <View style={styles.backdrop}>
              <View style={styles.sheet}>
                <Text style={styles.sheetTitle}>{current.title}</Text>
                <Text style={styles.description}>사용처를 하나 고른 뒤 제출해 주세요. 제출 전에는 여러 번 바꿔도 괜찮아요.</Text>
                <View accessibilityRole="radiogroup">
                {current.options.map((option) => (
                  <Pressable
                    accessibilityRole="radio"
                    accessibilityState={{ checked: selectedOptionId === option.optionId }}
                    disabled={ballot.isPending}
                    key={option.optionId}
                    onPress={() => setSelectedOptionId(option.optionId)}
                    style={[styles.option, selectedOptionId === option.optionId && styles.optionSelected, ballot.isPending && styles.disabled]}
                  >
                    <Text style={styles.optionTitle}>{option.label}</Text>
                    <Text style={styles.description}>{option.description}</Text>
                    <Text style={styles.budget}>예산 {option.budgetAmount.toLocaleString("ko-KR")} {currency}</Text>
                  </Pressable>
                ))}
                </View>
                {ballot.isError ? <Text accessibilityLiveRegion="assertive" style={styles.error}>{ballot.error.message}</Text> : null}
                <View style={styles.sheetActions}>
                  <Pressable accessibilityRole="button" disabled={ballot.isPending} onPress={() => setVoteOpen(false)} style={styles.close}><Text style={styles.closeText}>취소</Text></Pressable>
                  <Pressable accessibilityRole="button" disabled={!selectedOptionId || ballot.isPending} onPress={() => selectedOptionId && ballot.mutate(selectedOptionId)} style={[styles.submit, (!selectedOptionId || ballot.isPending) && styles.disabled]}><Text style={styles.submitText}>{ballot.isPending ? "제출 중" : "투표 제출"}</Text></Pressable>
                </View>
              </View>
            </View>
          </Modal>
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  lead: { color: colors.ink900, fontSize: 20, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 15, lineHeight: 22 },
  state: { minHeight: 240, alignItems: "center", justifyContent: "center", gap: 14 },
  fund: { marginTop: 8, color: colors.orange500, fontSize: 20, fontWeight: "900" },
  voteButton: { alignSelf: "flex-start", paddingHorizontal: 20, paddingVertical: 13, borderRadius: radii.control, backgroundColor: colors.yellow500 },
  voteButtonText: { color: colors.ink900, fontSize: 16, fontWeight: "900" },
  complete: { padding: 16, borderRadius: radii.control, backgroundColor: "#EAF8F3", color: colors.green700, fontSize: 15, fontWeight: "900" },
  notice: { padding: 16, borderRadius: radii.control, backgroundColor: "#FFF4DC", color: colors.orange500, fontSize: 15, fontWeight: "900" },
  retry: { alignSelf: "flex-start", marginTop: 4, paddingHorizontal: 18, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.green500 },
  retryText: { color: colors.white, fontWeight: "900" },
  resultTitle: { color: colors.green700, fontSize: 24, fontWeight: "900" },
  resultList: { overflow: "hidden", borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.card, backgroundColor: colors.white },
  resultRow: { minHeight: 58, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 18, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  resultWinner: { backgroundColor: "#FFF7D6" },
  resultCount: { color: colors.purple500, fontSize: 16, fontWeight: "900" },
  backdrop: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24, backgroundColor: "rgba(20,35,50,0.42)" },
  sheet: { width: "100%", maxWidth: 700, maxHeight: "90%", padding: 24, borderRadius: radii.panel, backgroundColor: colors.white },
  sheetTitle: { marginBottom: 10, color: colors.ink900, fontSize: 23, fontWeight: "900" },
  option: { marginTop: 12, padding: 16, gap: 6, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control },
  optionSelected: { borderWidth: 2, borderColor: colors.green700, backgroundColor: colors.washMint },
  optionTitle: { flex: 1, color: colors.ink900, fontSize: 18, fontWeight: "900" },
  budget: { color: colors.purple500, fontWeight: "900" },
  disabled: { opacity: 0.5 },
  error: { marginTop: 12, color: colors.red500, fontSize: 14, fontWeight: "800" },
  sheetActions: { flexDirection: "row", justifyContent: "flex-end", gap: 10, marginTop: 18 },
  close: { paddingHorizontal: 18, paddingVertical: 11, borderRadius: radii.control, backgroundColor: "#EEF2F6" },
  closeText: { color: colors.ink900, fontWeight: "900" },
  submit: { paddingHorizontal: 20, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.green700 },
  submitText: { color: colors.white, fontWeight: "900" }
});
