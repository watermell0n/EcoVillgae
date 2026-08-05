import { useQuery } from "@tanstack/react-query";
import { router } from "expo-router";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { fetchGuidedActivity } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function ClassroomScreen() {
  const { identity } = useAuth();
  const { access, enabled } = useClassroom();
  const activityId = access?.resources.guidedActivityId;
  const quizSessionId = access?.resources.quizSessionId;
  const voteId = access?.resources.voteId;
  const activity = useQuery({ queryKey: ["guided-activity", identity?.classroomId, activityId], queryFn: () => fetchGuidedActivity(identity!.classroomId, activityId!), enabled: Boolean(identity && activityId) });
  const quizOpen = Boolean(quizSessionId) && enabled("quiz");
  const meetingOpen = Boolean(voteId) && enabled("meeting");

  return <Screen title="선생님 수업" right={<BackButton />}>
    {activityId
      ? <StateCard tone="sky"><Text style={styles.activity}>{activity.isPending ? "수업 상태를 확인하고 있어요." : activity.data?.status === "LIVE" ? "선생님과 함께하는 수업이 진행 중이에요." : "수업 시작을 기다리고 있어요."}</Text></StateCard>
      : <StateCard title="지금 진행 중인 수업이 없어요"><Text style={styles.description}>선생님이 수업을 시작하면 이곳에 바로 나타나요. 그동안 수업 자료를 미리 살펴봐요.</Text></StateCard>}

    <Pressable onPress={() => router.push("/materials")} style={styles.material}><Text style={styles.materialTitle}>수업 자료 열기</Text><Text style={styles.description}>오늘 수업에서 사용할 자료를 미리 살펴봐요.</Text></Pressable>

    <View style={styles.section}>
      <Text style={styles.sectionTitle}>수업에서 이어서 할 일</Text>
      {quizOpen || meetingOpen
        ? <View style={styles.grid}>
            {quizOpen ? <Pressable onPress={() => router.push("/quiz")} style={styles.link}><Text style={styles.linkTitle}>퀴즈 풀러가기</Text><Text style={styles.description}>선생님이 연 퀴즈가 진행 중이에요.</Text></Pressable> : null}
            {meetingOpen ? <Pressable onPress={() => router.push("/meeting")} style={styles.link}><Text style={styles.linkTitle}>마을 회의 참여하기</Text><Text style={styles.description}>공동기금 사용처 투표가 열려 있어요.</Text></Pressable> : null}
          </View>
        : <Text style={styles.description}>지금은 이어서 할 활동이 없어요.</Text>}
    </View>
  </Screen>;
}

const styles = StyleSheet.create({
  material: { padding: 18, gap: 6, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.card, backgroundColor: colors.white },
  materialTitle: { color: colors.ink900, fontSize: 18, fontWeight: "900" },
  activity: { color: colors.ink900, fontSize: 17, fontWeight: "800" },
  description: { color: colors.muted600, fontSize: 15, lineHeight: 22 },
  section: { gap: 12 },
  sectionTitle: { color: colors.ink900, fontSize: 22, fontWeight: "900" },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 14 },
  link: { minWidth: 260, flexGrow: 1, flexBasis: "45%", gap: 6, padding: 18, borderWidth: 1.5, borderColor: colors.green500, borderRadius: radii.card, backgroundColor: colors.white },
  linkTitle: { color: colors.ink900, fontSize: 18, fontWeight: "900" }
});
