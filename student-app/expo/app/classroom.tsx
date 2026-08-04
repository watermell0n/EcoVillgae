import { useMutation, useQuery } from "@tanstack/react-query";
import { router } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { castVote, fetchGuidedActivity, fetchVote } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function ClassroomScreen() {
  const { identity } = useAuth(); const { access } = useClassroom(); const [voteOpen, setVoteOpen] = useState(false);
  const voteId = access?.resources.voteId; const activityId = access?.resources.guidedActivityId;
  const activity = useQuery({ queryKey: ["guided-activity", identity?.classroomId, activityId], queryFn: () => fetchGuidedActivity(identity!.classroomId, activityId!), enabled: Boolean(identity && activityId) });
  const vote = useQuery({ queryKey: ["class-vote", identity?.classroomId, voteId], queryFn: () => fetchVote(identity!.classroomId, voteId!), enabled: Boolean(identity && voteId) });
  const ballot = useMutation({ mutationFn: (optionId: string) => castVote(identity!.classroomId, vote.data!, optionId), onSuccess: () => { setVoteOpen(false); vote.refetch(); } });
  return <Screen title="선생님 수업" right={<BackButton />}>
    <Pressable onPress={() => router.push("/materials")} style={styles.material}><Text style={styles.materialTitle}>수업 자료 열기</Text><Text style={styles.description}>오늘 수업에서 사용할 자료를 미리 살펴봐요.</Text></Pressable>
    {activityId ? <StateCard tone="sky"><Text style={styles.activity}>{activity.isPending ? "수업 상태를 확인하고 있어요." : activity.data?.status === "LIVE" ? "선생님과 함께하는 수업이 진행 중이에요." : "수업 시작을 기다리고 있어요."}</Text></StateCard> : null}
    {!voteId ? <StateCard title="지금 참여할 투표가 없어요"><Text style={styles.description}>선생님이 활동을 시작하면 이곳에 나타나요.</Text></StateCard> : vote.isPending ? <ActivityIndicator color={colors.yellow500} /> : vote.data ? <><StateCard title={vote.data.title} tone="cream"><Text style={styles.description}>{vote.data.description}</Text><Text style={styles.fund}>공동기금 {vote.data.publicFundBalance} 비빔</Text></StateCard><Pressable onPress={() => setVoteOpen(true)} style={styles.voteButton}><Text style={styles.voteButtonText}>{vote.data.hasVoted ? "투표 내용 확인" : "사용처 투표하기"}</Text></Pressable><Modal transparent visible={voteOpen} animationType="fade" onRequestClose={() => setVoteOpen(false)}><View style={styles.backdrop}><View style={styles.sheet}><Text style={styles.sheetTitle}>{vote.data.title}</Text><Text style={styles.description}>{vote.data.description}</Text>{vote.data.options.map((option) => <Pressable disabled={ballot.isPending || vote.data.hasVoted || vote.data.status !== "OPEN"} key={option.optionId} onPress={() => ballot.mutate(option.optionId)} style={[styles.option, (ballot.isPending || vote.data.hasVoted || vote.data.status !== "OPEN") && styles.disabled]}><Text style={styles.optionTitle}>{option.label}</Text><Text style={styles.description}>{option.description}</Text><Text style={styles.budget}>예산 {option.budgetAmount} 비빔</Text></Pressable>)}<Pressable onPress={() => setVoteOpen(false)} style={styles.close}><Text style={styles.closeText}>닫기</Text></Pressable></View></View></Modal></> : <StateCard title="투표를 불러오지 못했어요" />}
  </Screen>;
}
const styles = StyleSheet.create({ material: { padding: 18, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.card, backgroundColor: colors.white }, materialTitle: { color: colors.ink900, fontSize: 18, fontWeight: "900" }, activity: { color: colors.ink900, fontSize: 17, fontWeight: "800" }, description: { color: colors.muted600, fontSize: 15, lineHeight: 22 }, fund: { marginTop: 8, color: colors.orange500, fontSize: 20, fontWeight: "900" }, voteButton: { alignSelf: "flex-start", paddingHorizontal: 20, paddingVertical: 13, borderRadius: radii.control, backgroundColor: colors.yellow500 }, voteButtonText: { color: colors.ink900, fontSize: 16, fontWeight: "900" }, backdrop: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24, backgroundColor: "rgba(20,35,50,0.42)" }, sheet: { width: "100%", maxWidth: 700, padding: 24, borderRadius: radii.panel, backgroundColor: colors.white }, sheetTitle: { marginBottom: 10, color: colors.ink900, fontSize: 23, fontWeight: "900" }, option: { marginTop: 12, padding: 16, gap: 6, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control }, optionTitle: { color: colors.ink900, fontSize: 18, fontWeight: "900" }, budget: { color: colors.purple500, fontWeight: "900" }, disabled: { opacity: 0.5 }, close: { alignSelf: "flex-end", marginTop: 18, paddingHorizontal: 18, paddingVertical: 11, borderRadius: radii.control, backgroundColor: "#EEF2F6" }, closeText: { color: colors.ink900, fontWeight: "900" } });
