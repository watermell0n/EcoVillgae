import { useMutation, useQuery } from "@tanstack/react-query";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchQuizSession, submitQuizAnswer } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function QuizScreen() {
  const { identity } = useAuth();
  const session = useQuery({ queryKey: ["quiz-session", identity?.classroomId], queryFn: () => fetchQuizSession(identity!.classroomId), enabled: Boolean(identity) });
  const answer = useMutation({ mutationFn: () => submitQuizAnswer(identity!.classroomId, session.data!.quizSessionId, session.data!.questions[0].resourceId) });
  const question = session.data?.questions[0];

  return (
    <Screen title="경제생활 퀴즈" subtitle="SCR-STU-021 · 답안은 한 번 제출하면 서버 응답 전까지 다시 누를 수 없어요." right={<BackButton />}>
      <StateCard title={session.isPending ? "퀴즈를 확인하고 있어요" : question ? "준비된 퀴즈가 있어요" : "지금은 열린 퀴즈가 없어요"}>
        {question ? (
          <View style={styles.quiz}>
            <Text style={styles.question}>{question.prompt}</Text>
            {question.options.map((option) => (
              <Pressable disabled={answer.isPending || answer.isSuccess} key={option} onPress={() => answer.mutate()} style={[styles.option, answer.isSuccess && styles.disabled]}>
                <Text style={styles.optionText}>{option}</Text>
              </Pressable>
            ))}
            {answer.isSuccess ? <Text style={styles.success}>답안을 제출했어요. 결과 공개를 기다려 주세요.</Text> : null}
            {answer.isError ? <Text style={styles.error}>{answer.error.message}</Text> : null}
          </View>
        ) : <Text style={styles.helper}>선생님이 퀴즈를 시작하면 자동으로 이 화면이 열려요.</Text>}
      </StateCard>
    </Screen>
  );
}

const styles = StyleSheet.create({
  quiz: { gap: 12 },
  question: { color: colors.ink900, fontSize: 25, fontWeight: "900", marginBottom: 8 },
  option: { minHeight: 54, justifyContent: "center", paddingHorizontal: 18, borderRadius: radii.control, borderWidth: 1, borderColor: colors.lineSoft, backgroundColor: colors.washBlue },
  optionText: { color: colors.ink900, fontSize: 17, fontWeight: "800" },
  disabled: { opacity: 0.5 },
  success: { color: colors.green500, fontSize: 15, fontWeight: "900" },
  error: { color: colors.red500, fontWeight: "700" },
  helper: { color: colors.muted600, fontSize: 16 }
});
