import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { fetchQuizSession, submitQuizAnswer } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

/** 이미 답한 문항을 다시 고르면 서버가 막습니다. 학생이 이해할 수 있는 말로 바꿔 줍니다. */
function answerErrorMessage(error: Error) {
  if (error.message.includes("already has an answer")) {
    return "이 문제는 이미 답을 냈어요. 다음 문제로 넘어가요.";
  }
  if (error.message.includes("not accepting answers")) {
    return "선생님이 퀴즈를 마감했어요.";
  }
  return error.message;
}

export default function QuizScreen() {
  const { identity } = useAuth();
  const { access } = useClassroom();
  const [questionIndex, setQuestionIndex] = useState(0);
  const [started, setStarted] = useState(false);
  const [selectedOptionId, setSelectedOptionId] = useState<string | null>(null);
  const [answered, setAnswered] = useState(false);
  const quizSessionId = access?.resources.quizSessionId;
  const session = useQuery({
    queryKey: ["quiz-session", identity?.classroomId, quizSessionId],
    queryFn: () => fetchQuizSession(identity!.classroomId, quizSessionId!),
    enabled: Boolean(identity?.classroomId && quizSessionId)
  });
  const questions = session.data?.questions ?? [];
  const question = questions[questionIndex];
  const answer = useMutation({
    // 제출 경로는 퀴즈 운영 방식(게임형·평가형)에 따라 달라집니다.
    mutationFn: (optionId: string) => submitQuizAnswer(
      identity!.classroomId,
      session.data!.quizSessionId,
      question!.resourceId,
      optionId,
      session.data!.mode
    ),
    onSuccess: () => setAnswered(true)
  });

  useEffect(() => { setQuestionIndex(0); setStarted(false); setSelectedOptionId(null); setAnswered(false); }, [quizSessionId]);

  const moveQuestion = (nextIndex: number) => {
    if (nextIndex < 0 || nextIndex >= questions.length) return;
    setQuestionIndex(nextIndex);
    setSelectedOptionId(null);
    setAnswered(false);
    answer.reset();
  };

  // 마지막 문항에서는 다 푼 화면으로 넘어갑니다. 그 전에는 다음 문항으로 이동합니다.
  const finishOrAdvance = () => {
    if (questionIndex === questions.length - 1) {
      setQuestionIndex(questions.length);
      setSelectedOptionId(null);
      setAnswered(false);
      answer.reset();
      return;
    }
    moveQuestion(questionIndex + 1);
  };

  return (
    <Screen title="경제 퀴즈" right={<BackButton />}>
      {!quizSessionId ? (
        <StateCard title="지금은 준비된 퀴즈가 없어요"><Text style={styles.helper}>선생님이 퀴즈를 열면 이곳에서 풀 수 있어요.</Text></StateCard>
      ) : session.isPending ? (
        <View style={styles.center}><ActivityIndicator color={colors.green500} /><Text style={styles.helper}>퀴즈를 불러오고 있어요.</Text></View>
      ) : session.isError ? (
        <StateCard title="퀴즈를 불러오지 못했어요"><Text style={styles.error}>{session.error.message}</Text><Pressable onPress={() => session.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 불러오기</Text></Pressable></StateCard>
      ) : !started ? (
        <View style={styles.quizLobby}>
          <Text style={styles.lobbyEyebrow}>선생님이 준비한 퀴즈</Text>
          <Text style={styles.lobbyTitle}>경제 개념 확인 퀴즈</Text>
          <Text style={styles.lobbyCopy}>총 {questions.length}문제예요. 배너를 눌러 시작해요.</Text>
          <Pressable onPress={() => setStarted(true)} style={styles.startButton}><Text style={styles.startButtonText}>퀴즈 시작하기</Text></Pressable>
        </View>
      ) : question ? (
        <View style={styles.quizShell}>
          <View style={styles.progressRow}>
            {questions.map((item, index) => <View key={item.resourceId} style={[styles.progressDot, index === questionIndex && styles.progressDotActive, index < questionIndex && styles.progressDotDone]}><Text style={[styles.progressNumber, index === questionIndex && styles.progressNumberActive]}>{index + 1}</Text></View>)}
          </View>
          <View style={styles.quizCard}>
            <View style={styles.quizMeta}><Text style={styles.count}>{questionIndex + 1} / {questions.length}</Text><Text style={styles.reward}>정답수당 +10비빔</Text></View>
            <Text style={styles.question}>{question.prompt}</Text>
            <View style={styles.options}>
              {question.options.map((option, index) => <Pressable disabled={!option.optionId || answer.isPending || answered} key={`${option.optionId ?? option.label}-${index}`} onPress={() => { if (option.optionId) { setSelectedOptionId(option.optionId); answer.mutate(option.optionId); } }} style={({ pressed }) => [styles.option, selectedOptionId === option.optionId && styles.optionSelected, pressed && styles.optionPressed, (!option.optionId || answer.isPending || answered) && styles.optionDisabled]}><Text style={styles.optionText}>{String.fromCharCode(9312 + index)}  {option.label}</Text></Pressable>)}
            </View>
            {answer.isError ? <Text style={styles.error}>{answerErrorMessage(answer.error)}</Text> : null}
            <View style={styles.quizActions}>
              {/* 답을 고르지 않아도 넘어갈 수 있습니다. 건너뛴 문항은 이전 문제로 돌아와 풀 수 있습니다. */}
              <Pressable disabled={questionIndex === 0 || answer.isPending} onPress={() => moveQuestion(questionIndex - 1)} style={[styles.secondaryButton, (questionIndex === 0 || answer.isPending) && styles.buttonDisabled]}><Text style={styles.secondaryButtonText}>이전 문제</Text></Pressable>
              <Pressable disabled={answer.isPending} onPress={() => finishOrAdvance()} style={[styles.nextButton, answer.isPending && styles.buttonDisabled]}><Text style={styles.nextButtonText}>{questionIndex === questions.length - 1 ? "퀴즈 마치기" : "다음 문제"}</Text></Pressable>
            </View>
          </View>
        </View>
      ) : (
        <StateCard title={questions.length ? "퀴즈를 모두 풀었어요" : "지금은 준비된 퀴즈가 없어요"}><Text style={styles.helper}>{questions.length ? "선생님이 결과를 공개하면 확인할 수 있어요." : "선생님이 퀴즈를 열면 이곳에서 풀 수 있어요."}</Text></StateCard>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { minHeight: 280, alignItems: "center", justifyContent: "center", gap: 12 },
  quizLobby: { width: "100%", maxWidth: 760, alignSelf: "center", padding: 36, borderRadius: radii.panel, backgroundColor: colors.white, shadowColor: colors.ink900, shadowOffset: { width: 0, height: 10 }, shadowOpacity: 0.1, shadowRadius: 22, elevation: 6 },
  lobbyEyebrow: { color: colors.muted600, fontSize: 14, fontWeight: "800" },
  lobbyTitle: { marginTop: 8, color: colors.ink900, fontSize: 28, fontWeight: "900" },
  lobbyCopy: { marginTop: 12, color: colors.muted600, fontSize: 16 },
  startButton: { alignSelf: "flex-start", marginTop: 28, paddingHorizontal: 24, paddingVertical: 14, borderRadius: radii.control, backgroundColor: colors.orange500 },
  startButtonText: { color: colors.white, fontSize: 16, fontWeight: "900" },
  quizShell: { flex: 1, alignItems: "center", paddingTop: 8, gap: 22 },
  progressRow: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 10 },
  progressDot: { width: 34, height: 34, alignItems: "center", justifyContent: "center", borderRadius: 17, backgroundColor: "#E3EAF2" },
  progressDotActive: { borderWidth: 3, borderColor: "#FFD8BD", backgroundColor: colors.orange500 },
  progressDotDone: { backgroundColor: colors.green500 },
  progressNumber: { color: "#9AABBE", fontSize: 14, fontWeight: "900" },
  progressNumberActive: { color: colors.white },
  quizCard: { width: "100%", maxWidth: 760, paddingHorizontal: 38, paddingVertical: 32, borderRadius: radii.panel, backgroundColor: colors.white, shadowColor: colors.ink900, shadowOffset: { width: 0, height: 10 }, shadowOpacity: 0.1, shadowRadius: 22, elevation: 6 },
  quizMeta: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 12 },
  count: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: radii.chip, backgroundColor: "#EDF3FB", color: colors.muted600, fontSize: 13, fontWeight: "900" },
  reward: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: radii.chip, backgroundColor: "#FFF1E9", color: colors.orange500, fontSize: 13, fontWeight: "900" },
  question: { marginTop: 26, marginBottom: 26, color: colors.ink900, fontSize: 24, fontWeight: "900", lineHeight: 34, textAlign: "center" },
  options: { flexDirection: "row", flexWrap: "wrap", gap: 12 },
  option: { flexGrow: 1, flexBasis: "45%", minHeight: 62, justifyContent: "center", paddingHorizontal: 20, borderWidth: 1, borderColor: "#D9E4F0", borderRadius: radii.control, backgroundColor: colors.white },
  optionPressed: { borderColor: colors.orange500, backgroundColor: "#FFF6F1" },
  optionSelected: { borderColor: colors.green500, backgroundColor: colors.washMint },
  optionDisabled: { opacity: 0.52 },
  optionText: { color: colors.ink900, fontSize: 17, fontWeight: "800" },
  quizActions: { flexDirection: "row", justifyContent: "space-between", marginTop: 24 },
  secondaryButton: { paddingHorizontal: 18, paddingVertical: 12, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.control },
  secondaryButtonText: { color: colors.muted600, fontWeight: "900" },
  nextButton: { paddingHorizontal: 20, paddingVertical: 12, borderRadius: radii.control, backgroundColor: colors.green500 },
  nextButtonText: { color: colors.white, fontWeight: "900" },
  buttonDisabled: { opacity: 0.4 },
  error: { marginTop: 16, color: colors.red500, fontWeight: "700", textAlign: "center" },
  helper: { marginTop: 14, color: colors.muted600, fontSize: 15, lineHeight: 22, textAlign: "center" },
  retry: { alignSelf: "center", marginTop: 12, paddingHorizontal: 18, paddingVertical: 10, borderRadius: radii.control, backgroundColor: colors.green500 },
  retryText: { color: colors.white, fontWeight: "900" }
});
