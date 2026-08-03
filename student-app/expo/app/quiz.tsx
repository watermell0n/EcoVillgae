import { useMutation, useQuery } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { fetchQuizSession, submitQuizAnswer } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function QuizScreen() {
  const { identity } = useAuth();
  const { access } = useClassroom();
  const quizSessionId = access?.resources.quizSessionId;
  const session = useQuery({
    queryKey: ["quiz-session", identity?.classroomId, quizSessionId],
    queryFn: () => fetchQuizSession(identity!.classroomId, quizSessionId!),
    enabled: Boolean(identity?.classroomId && quizSessionId)
  });
  const question = session.data?.questions[0];
  const answer = useMutation({
    mutationFn: (optionId: string) => submitQuizAnswer(identity!.classroomId, session.data!.quizSessionId, question!.resourceId, optionId)
  });

  return (
    <Screen title="경제생활 퀴즈" right={<BackButton />}>
      {!quizSessionId ? (
        <StateCard title="지금은 준비된 퀴즈가 없어요"><Text style={styles.helper}>선생님이 퀴즈를 열면 이곳에서 풀 수 있어요.</Text></StateCard>
      ) : session.isPending ? (
        <View style={styles.center}><ActivityIndicator color={colors.green500} /><Text style={styles.helper}>퀴즈를 불러오고 있어요.</Text></View>
      ) : session.isError ? (
        <StateCard title="퀴즈를 불러오지 못했어요"><Text style={styles.error}>{session.error.message}</Text><Pressable onPress={() => session.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 불러오기</Text></Pressable></StateCard>
      ) : question ? (
        <StateCard title="준비된 퀴즈가 있어요">
          <View style={styles.quiz}>
            <Text style={styles.question}>{question.prompt}</Text>
            {question.options.map((option, index) => {
              const canSubmit = Boolean(option.optionId);
              return <Pressable disabled={!canSubmit || answer.isPending || answer.isSuccess} key={`${option.optionId ?? option.label}-${index}`} onPress={() => option.optionId && answer.mutate(option.optionId)} style={[styles.option, (!canSubmit || answer.isSuccess) && styles.disabled]}><Text style={styles.optionText}>{option.label}</Text></Pressable>;
            })}
            {question.options.some((option) => !option.optionId) ? <Text style={styles.helper}>선택지 식별자가 아직 제공되지 않아 답안을 제출할 수 없어요.</Text> : null}
            {answer.isSuccess ? <Text style={styles.success}>답안을 제출했어요. 결과 공개를 기다려 주세요.</Text> : null}
            {answer.isError ? <Text style={styles.error}>{answer.error.message}</Text> : null}
          </View>
        </StateCard>
      ) : <StateCard title="지금은 준비된 퀴즈가 없어요"><Text style={styles.helper}>선생님이 퀴즈를 열면 이곳에서 풀 수 있어요.</Text></StateCard>}
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { minHeight: 280, alignItems: "center", justifyContent: "center", gap: 12 },
  quiz: { gap: 12 },
  question: { color: colors.ink900, fontSize: 25, fontWeight: "900", marginBottom: 8 },
  option: { minHeight: 54, justifyContent: "center", paddingHorizontal: 18, borderRadius: radii.control, borderWidth: 1, borderColor: colors.lineSoft, backgroundColor: colors.washBlue },
  optionText: { color: colors.ink900, fontSize: 17, fontWeight: "800" },
  disabled: { opacity: 0.5 },
  success: { color: colors.green500, fontSize: 15, fontWeight: "900" },
  error: { color: colors.red500, fontWeight: "700" },
  helper: { color: colors.muted600, fontSize: 16, lineHeight: 23 },
  retry: { alignSelf: "flex-start", paddingHorizontal: 18, paddingVertical: 10, borderRadius: radii.control, backgroundColor: colors.green500 },
  retryText: { color: colors.white, fontWeight: "900" }
});
