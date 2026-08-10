"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { AlertTriangle, Bot, Check, CheckCircle2, LoaderCircle, Play, RefreshCw, Sparkles } from "lucide-react";
import { ApiError, apiRequest } from "../api";
import type {
  NonsenseImportResponse,
  QuizDraftOperationResponse,
  QuizQuestion,
  QuizSessionCommandResponse
} from "../api";

type QuestionStatusResponse = {
  quiz_resource_id: string;
  status: QuizQuestion["status"];
  state_version: number;
};

type QuizStudent = {
  resourceId?: string;
  attendance: string;
  nickname: string;
};

type ManagedQuizSession = {
  id: string;
  mode: "GAME" | "TEST";
  contentType: "ECONOMY" | "NONSENSE";
  status: string;
  stateVersion: number;
};

type QuizSessionSummary = {
  quiz_session_id: string;
  mode: "GAME" | "TEST";
  content_type: "ECONOMY" | "NONSENSE";
  status: string;
  state_version: number;
  opened_at: string;
  questions: Array<{
    quiz_question_version_id?: string;
    prompt: string;
  }>;
};

type QuizResults = {
  result_version: number;
  status: string;
  summary: Record<string, unknown>;
  leaderboard: Array<{ resource_id: string; rank: number; nickname: string }>;
};

// 문제은행 목록에서 무엇을 보여 줄지 고르는 탭입니다.
type BankFilter = "recent" | "approved";

// 학생 화면에 연 퀴즈 한 건입니다. 세션 목록 API가 없어 교사 브라우저에 기록해 둡니다.
type OpenedQuiz = {
  sessionId: string;
  mode: "GAME" | "TEST";
  contentType: "ECONOMY" | "NONSENSE";
  openedAt: string;
  questions: Array<{ versionId: string; prompt: string }>;
};

// 새로고침하거나 학급을 나갔다 와도 "마지막에 만든 문항"과 "연 퀴즈"가 유지되게 합니다.
const lastBatchKey = (classroomId: string) => `ecovillage.quiz.lastBatch.${classroomId}`;
const openedQuizzesKey = (classroomId: string) => `ecovillage.quiz.openedQuizzes.${classroomId}`;

function readStored<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function writeStored(key: string, value: unknown) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // 저장 공간이 막혀 있어도 이번 화면 동작은 그대로 이어 갑니다.
  }
}

const STATUS_LABEL: Record<QuizQuestion["status"], string> = {
  DRAFT: "작성 중",
  REVIEW_REQUIRED: "검토 필요",
  APPROVED: "승인됨",
  PUBLISHED: "게시됨",
  RETIRED: "종료됨"
};

function quizErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return "AI 퀴즈 초안을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.";
  if (error.code === "AI_DISABLED") return "현재 이 학급의 AI 기능이 중지되어 있어요.";
  if (error.code === "AI_DAILY_LIMIT_EXCEEDED" || error.status === 429) return "오늘 사용할 수 있는 AI 퀴즈 생성 횟수를 모두 사용했어요.";
  if (error.code === "AI_TIMEOUT" || error.status === 503) return "AI가 제한 시간 안에 퀴즈를 만들지 못했어요. 다시 시도해 주세요.";
  if (error.code === "STATE_VERSION_CONFLICT") return "다른 화면에서 문항 상태가 변경됐어요. 문제은행을 새로고침해 주세요.";
  if (error.status === 403) return "해당 학급의 교사만 퀴즈 초안을 관리할 수 있어요.";
  return error.message;
}

function sessionErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return "퀴즈를 시작하지 못했어요. 서버 연결을 확인해 주세요.";
  if (error.code === "QUESTION_NOT_PUBLISHED") return "게시되지 않았거나 분류가 다른 문항이 포함됐어요. 문제은행을 새로고침해 주세요.";
  if (error.code === "PARTICIPANT_NOT_FOUND") return "학생 명단이 바뀌었어요. 새로고침한 뒤 다시 시작해 주세요.";
  if (error.code === "QUIZ_SESSION_ALREADY_EXISTS") return "이미 시작된 퀴즈예요. 잠시 후 다시 시도해 주세요.";
  if (error.code === "QUIZ_WORKFLOW_INCOMPLETE") return "이전 퀴즈의 마감·채점·결과 공개를 먼저 완료해 주세요.";
  if (error.code === "ACTIVITY_ALREADY_LIVE") return "진행 중인 수업이나 퀴즈를 먼저 종료해 주세요.";
  if (error.code === "AMBIGUOUS_ANSWER") return "정답이 하나가 아닌 문항이 있어요. 문항을 수정한 뒤 다시 시작해 주세요.";
  if (error.code === "PUBLIC_DATA_ONLY") return "평가형 퀴즈는 순위를 공개할 수 없어요.";
  if (error.code === "CLASS_NOT_ACTIVE") return "학급이 활성 상태일 때만 퀴즈를 시작할 수 있어요.";
  if (error.code === "MODULE_NOT_ACTIVE") {
    return error.message.includes("QUIZ_TEST")
      ? "평가형 퀴즈 기능이 아직 켜져 있지 않아요. ‘학급 설정 → 사용 기능’에서 ‘평가형 퀴즈’를 켜면 평가형으로 열 수 있어요."
      : "게임형 퀴즈 기능이 아직 켜져 있지 않아요. ‘학급 설정 → 사용 기능’에서 ‘퀴즈’를 켜 주세요.";
  }
  if (error.status === 403) return "퀴즈 시작은 담당 교사만 실행할 수 있어요.";
  return error.message;
}

export function QuizDraftWorkspace({
  classroomId,
  initialGrade,
  aiEnabled,
  students,
  activeQuizSessionId,
  registeredQuestionIds = [],
  onReload
}: Readonly<{
  classroomId: string;
  initialGrade: number;
  aiEnabled: boolean;
  students: readonly QuizStudent[];
  // 진행 중인 퀴즈 세션 id입니다. 활성 퀴즈 모듈 config에서 옵니다.
  activeQuizSessionId?: string;
  /** 난이도 문제은행에서 방금 등록한 문항 ID입니다. 목록을 즉시 다시 읽고 시작 목록에 담습니다. */
  registeredQuestionIds?: readonly string[];
  onReload?: () => Promise<void>;
}>) {
  const [grade, setGrade] = useState(initialGrade);
  const [topic, setTopic] = useState("합리적인 소비");
  const [learningObjective, setLearningObjective] = useState("필요와 욕구를 구분하고 계획적인 소비 방법을 설명한다.");
  const [contentType, setContentType] = useState<"ECONOMY" | "NONSENSE">("ECONOMY");
  const [questionType, setQuestionType] = useState<"MULTIPLE_CHOICE" | "OX">("MULTIPLE_CHOICE");
  const [questionCount, setQuestionCount] = useState(3);
  // 넌센스는 문제은행에서 가져오므로 AI 설정과 무관하고 주제·학습목표도 받지 않는다.
  const isNonsense = contentType === "NONSENSE";
  const submitBlocked = !isNonsense && !aiEnabled;
  const [questions, setQuestions] = useState<QuizQuestion[]>([]);
  const [generatedIds, setGeneratedIds] = useState<Set<string>>(new Set());
  const [bankFilter, setBankFilter] = useState<BankFilter>("recent");
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [changingId, setChangingId] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [selectedVersionIds, setSelectedVersionIds] = useState<string[]>([]);
  const [sessionMode, setSessionMode] = useState<"GAME" | "TEST">("GAME");
  const [rewardPerPerson, setRewardPerPerson] = useState("0");
  const [leaderboardPublic, setLeaderboardPublic] = useState(true);
  const [starting, setStarting] = useState(false);
  const [managedSessions, setManagedSessions] = useState<ManagedQuizSession[]>([]);
  const [sessionResultsById, setSessionResultsById] = useState<Record<string, QuizResults>>({});
  // 이미 학생 화면에 열어 준 문항입니다. 같은 문항으로 두 번 열지 않도록 잠급니다.
  const [openedVersionIds, setOpenedVersionIds] = useState<string[]>([]);
  const [openedQuizzes, setOpenedQuizzes] = useState<OpenedQuiz[]>([]);
  const [sessionError, setSessionError] = useState("");
  const [sessionMessage, setSessionMessage] = useState("");

  useEffect(() => setGrade(initialGrade), [initialGrade]);

  // keepError는 명령이 실패해 목록만 새로 읽을 때 씁니다.
  // 목록 조회가 성공했다고 방금 띄운 실패 안내까지 지우면 화면이 아무 반응 없는 것처럼 보입니다.
  const loadQuestions = useCallback(async (keepError = false) => {
    setLoading(true);
    try {
      const response = await apiRequest<{ questions: QuizQuestion[] }>(
        `/classrooms/${classroomId}/quiz-questions?limit=100`
      );
      setQuestions(response.questions);
      if (!keepError) setError("");
      return response.questions;
    } catch (caught) {
      setError(quizErrorMessage(caught));
      return [] as QuizQuestion[];
    } finally {
      setLoading(false);
    }
  }, [classroomId]);

  const loadSessions = useCallback(async () => {
    try {
      const response = await apiRequest<{ sessions: QuizSessionSummary[] }>(
        `/classrooms/${classroomId}/quiz-sessions`
      );
      const sessions = response.sessions.map((session) => ({
        id: session.quiz_session_id,
        mode: session.mode,
        contentType: session.content_type,
        status: session.status,
        stateVersion: session.state_version
      }));
      setManagedSessions(sessions);
      const serverQuizzes: OpenedQuiz[] = response.sessions.map((session) => ({
        sessionId: session.quiz_session_id,
        mode: session.mode,
        contentType: session.content_type,
        openedAt: session.opened_at,
        questions: session.questions.flatMap((question) => question.quiz_question_version_id ? [{
          versionId: question.quiz_question_version_id,
          prompt: question.prompt
        }] : [])
      }));
      setOpenedQuizzes(serverQuizzes);
      writeStored(openedQuizzesKey(classroomId), serverQuizzes);
      setOpenedVersionIds([...new Set(serverQuizzes.flatMap((quiz) => quiz.questions.map((item) => item.versionId)))]);

      const resultSessions = sessions.filter((session) => (
        ["REVIEWING", "RESULT_PUBLISHED", "CLOSED"].includes(session.status)
      ));
      const loadedResults = await Promise.all(resultSessions.map(async (session) => {
        try {
          const results = await apiRequest<QuizResults>(
            `/classrooms/${classroomId}/quiz-sessions/${session.id}/results`
          );
          return [session.id, results] as const;
        } catch {
          return null;
        }
      }));
      setSessionResultsById(Object.fromEntries(loadedResults.filter((item): item is readonly [string, QuizResults] => item !== null)));
    } catch (caught) {
      setSessionError(sessionErrorMessage(caught));
    }
  }, [classroomId]);

  useEffect(() => {
    // 마지막 생성분과 연 퀴즈는 브라우저에 남겨 두고 다시 읽습니다.
    setGeneratedIds(new Set(readStored<string[]>(lastBatchKey(classroomId), [])));
    setOpenedQuizzes(readStored<OpenedQuiz[]>(openedQuizzesKey(classroomId), []));
    setBankFilter("recent");
    setMessage("");
    setError("");
    setSelectedVersionIds([]);
    setOpenedVersionIds([]);
    setManagedSessions([]);
    setSessionResultsById({});
    setSessionError("");
    setSessionMessage("");
    void loadQuestions();
    void loadSessions();
  }, [classroomId, loadQuestions, loadSessions]);

  useEffect(() => {
    if (registeredQuestionIds.length === 0) return;
    let cancelled = false;
    void loadQuestions().then((loaded) => {
      if (cancelled) return;
      const newVersionIds = loaded
        .filter((question) => registeredQuestionIds.includes(question.resource_id))
        .filter((question) => question.status === "PUBLISHED" && !question.already_used_in_session)
        .map((question) => question.question_version_id);
      setSelectedVersionIds((current) => [...new Set([...current, ...newVersionIds])]);
    });
    return () => { cancelled = true; };
  }, [registeredQuestionIds, loadQuestions]);

  // 마지막 생성분을 기록합니다. 목록에서 사라진 문항은 다음에 읽을 때 자연히 걸러집니다.
  const rememberBatch = useCallback((ids: string[]) => {
    setGeneratedIds(new Set(ids));
    writeStored(lastBatchKey(classroomId), ids);
  }, [classroomId]);

  // 모듈 config가 바뀌면 서버의 전체 세션 목록을 다시 읽습니다. 특정 마지막 세션만
  // 복원하면 그 전에 마감한 퀴즈의 결과 공개 버튼이 사라질 수 있습니다.
  useEffect(() => {
    void loadSessions();
  }, [activeQuizSessionId, loadSessions]);

  const approvedQuestions = useMemo(
    () => questions.filter((question) => question.status === "APPROVED"),
    [questions]
  );

  // '최근 생성'은 언제나 마지막 한 번의 생성분만 보여 줍니다. 승인한 문항은 여기서 빠집니다.
  const recentQuestions = useMemo(
    () => questions.filter((question) => generatedIds.has(question.resource_id)),
    [generatedIds, questions]
  );

  const visibleQuestions = bankFilter === "approved" ? approvedQuestions : recentQuestions;

  const emptyBankCopy = bankFilter === "approved"
    ? "승인한 문항이 없어요. 문항을 검토한 뒤 '검토 후 승인'을 누르면 여기로 옵니다."
    : "최근에 만든 문항이 없어요. 위에서 문항을 생성하거나 가져오면 여기에 나타납니다.";

  // 세션 시작에 쓸 수 있는 문항은 게시된 것만입니다. 서버도 같은 조건을 검사합니다.
  const publishedQuestions = useMemo(
    () => questions.filter((question) => question.status === "PUBLISHED"),
    [questions]
  );

  // 목록이 새로 오면 게시 상태가 아니거나 이미 진행한 문항의 선택은 버립니다.
  useEffect(() => {
    setSelectedVersionIds((current) => current.filter((versionId) => (
      publishedQuestions.some((question) => (
        question.question_version_id === versionId && !question.already_used_in_session
      ))
    )));
  }, [publishedQuestions]);

  const selectedQuestions = useMemo(
    () => publishedQuestions.filter(
      (question) => selectedVersionIds.includes(question.question_version_id)
    ),
    [publishedQuestions, selectedVersionIds]
  );

  // 한 세션의 문항은 분류(경제/넌센스)가 모두 같아야 합니다. 서버가 다르면 거절합니다.
  const selectedContentTypes = useMemo(
    () => [...new Set(selectedQuestions.map((question) => question.content_type))],
    [selectedQuestions]
  );
  const mixedContentTypes = selectedContentTypes.length > 1;
  const participantIds = useMemo(
    () => students.flatMap((student) => student.resourceId ? [student.resourceId] : []),
    [students]
  );
  const openSessionId = managedSessions.find((session) => session.status === "OPEN")?.id
    ?? activeQuizSessionId
    ?? "";
  const rewardValue = Number(rewardPerPerson);
  const unfinishedSessions = managedSessions.filter((session) => (
    ["OPEN", "SCORING", "REVIEWING"].includes(session.status)
  ));
  const hasOpenSession = unfinishedSessions.length > 0;
  // 이미 학생에게 열어 본 문항과 아직 쓰지 않은 문항을 나눠 보여 줍니다.
  // 사용 여부는 서버(already_used_in_session)가 기준이라 새로고침해도 그대로 유지됩니다.
  // 방금 연 퀴즈는 목록을 다시 읽기 전이라 openedVersionIds로 함께 봅니다.
  const isUsed = useCallback(
    (question: QuizQuestion) => question.already_used_in_session
      || openedVersionIds.includes(question.question_version_id),
    [openedVersionIds]
  );
  const reusableQuestions = useMemo(
    () => publishedQuestions.filter((question) => !isUsed(question)),
    [publishedQuestions, isUsed]
  );
  const usedQuestions = useMemo(
    () => publishedQuestions.filter((question) => isUsed(question)),
    [publishedQuestions, isUsed]
  );
  // 연 퀴즈 기록에 없는 사용 문항입니다. 다른 브라우저에서 열었거나 기록이 지워진 경우입니다.
  const recordedUsedVersionIds = useMemo(
    () => new Set(openedQuizzes.flatMap((quiz) => quiz.questions.map((item) => item.versionId))),
    [openedQuizzes]
  );
  const unrecordedUsedQuestions = useMemo(
    () => usedQuestions.filter(
      (question) => !recordedUsedVersionIds.has(question.question_version_id)
    ),
    [recordedUsedVersionIds, usedQuestions]
  );
  const invalidReward = sessionMode === "GAME"
    && (!Number.isInteger(rewardValue) || rewardValue < 0);
  const canStart = !starting
    && !hasOpenSession
    && selectedQuestions.length > 0
    && !mixedContentTypes
    && participantIds.length > 0
    && !invalidReward;

  function toggleQuestion(versionId: string) {
    if (!reusableQuestions.some((question) => question.question_version_id === versionId)) return;
    setSessionError("");
    setSessionMessage("");
    setSelectedVersionIds((current) => current.includes(versionId)
      ? current.filter((item) => item !== versionId)
      : [...current, versionId]);
  }

  async function startSession() {
    if (!canStart) return;
    setStarting(true);
    setSessionError("");
    setSessionMessage("");
    const sessionId = crypto.randomUUID();
    try {
      const response = await apiRequest<QuizSessionCommandResponse>(
        `/classrooms/${classroomId}/quiz-sessions/${sessionId}/commands`,
        {
          method: "POST",
          body: JSON.stringify({
            screen_id: "SCR-WEB-018",
            ui_action_id: "UIA-QZ-004",
            client_occurred_at: new Date().toISOString(),
            command: "START",
            question_version_ids: selectedVersionIds,
            participant_ids: participantIds,
            mode: sessionMode,
            content_type: selectedContentTypes[0],
            // 평가형 퀴즈에는 금전 보상과 순위 공개가 없습니다.
            reward_policy_public: sessionMode === "GAME"
              ? { reward_per_person: rewardValue }
              : {},
            leaderboard_public: sessionMode === "GAME" && leaderboardPublic
          })
        }
      );
      const openedCount = selectedQuestions.length;
      setManagedSessions((current) => [{
        id: response.quiz_resource_id,
        mode: sessionMode,
        contentType: selectedContentTypes[0],
        status: response.status,
        stateVersion: response.state_version
      }, ...current]);
      // 아래 '생성된 퀴즈'에 새 칸으로 바로 올립니다. 새로고침을 기다리지 않습니다.
      const openedQuiz: OpenedQuiz = {
        sessionId: response.quiz_resource_id,
        mode: sessionMode,
        contentType: selectedContentTypes[0],
        openedAt: new Date().toISOString(),
        questions: selectedQuestions.map((question) => ({
          versionId: question.question_version_id,
          prompt: question.prompt
        }))
      };
      setOpenedQuizzes((current) => {
        const next = [openedQuiz, ...current.filter((item) => item.sessionId !== openedQuiz.sessionId)];
        writeStored(openedQuizzesKey(classroomId), next);
        return next;
      });
      // 같은 문항으로 다시 열지 못하게 잠급니다.
      setOpenedVersionIds((current) => [...new Set([...current, ...selectedVersionIds])]);
      setSelectedVersionIds([]);
      setSessionMessage(
        `${openedCount}개 문항으로 ${sessionMode === "GAME" ? "게임형" : "평가형"} 퀴즈를 열었어요. 학생 화면에서 바로 풀 수 있습니다.`
      );
      // 학급 상태를 다시 읽어 진행 중인 퀴즈 세션 정보를 최신으로 맞춥니다.
      if (onReload) await onReload();
      await loadSessions();
    } catch (caught) {
      setSessionError(sessionErrorMessage(caught));
      if (caught instanceof ApiError && caught.code === "QUESTION_NOT_PUBLISHED") {
        await loadQuestions(true);
      }
    } finally {
      setStarting(false);
    }
  }

  async function loadSessionResults(sessionId: string) {
    const results = await apiRequest<QuizResults>(`/classrooms/${classroomId}/quiz-sessions/${sessionId}/results`);
    setSessionResultsById((current) => ({ ...current, [sessionId]: results }));
    return results;
  }

  function updateManagedSession(sessionId: string, response: QuizSessionCommandResponse) {
    setManagedSessions((current) => current.map((session) => (
      session.id === sessionId
        ? { ...session, status: response.status, stateVersion: response.state_version }
        : session
    )));
  }

  async function closeSubmissions(session: ManagedQuizSession) {
    if (session.status !== "OPEN") return;
    setStarting(true);
    setSessionError("");
    try {
      const response = await apiRequest<QuizSessionCommandResponse>(
        `/classrooms/${classroomId}/quiz-sessions/${session.id}/commands`,
        {
          method: "POST",
          body: JSON.stringify({
            expected_state_version: session.stateVersion,
            command: "CLOSE_SUBMISSIONS",
            question_version_ids: [],
            participant_ids: [],
            mode: session.mode,
            content_type: session.contentType,
            reward_policy_public: {},
            leaderboard_public: false
          })
        }
      );
      updateManagedSession(session.id, response);
      setSessionMessage("답안 제출을 마감했어요. 이제 결과를 계산하고 확인해 주세요.");
      if (onReload) await onReload();
      await loadSessions();
    } catch (caught) {
      setSessionError(sessionErrorMessage(caught));
    } finally {
      setStarting(false);
    }
  }

  async function confirmResults(session: ManagedQuizSession) {
    if (session.status !== "SCORING") return;
    setStarting(true);
    setSessionError("");
    try {
      const response = await apiRequest<QuizSessionCommandResponse>(
        `/classrooms/${classroomId}/quiz-sessions/${session.id}/result-commands`,
        {
          method: "POST",
          body: JSON.stringify({
            expected_state_version: session.stateVersion,
            command: "CONFIRM",
            leaderboard_public: false
          })
        }
      );
      updateManagedSession(session.id, response);
      await loadSessionResults(session.id);
      setSessionMessage("채점 결과를 계산했어요. 내용을 확인한 뒤 학생에게 공개할 수 있어요.");
    } catch (caught) {
      setSessionError(sessionErrorMessage(caught));
    } finally {
      setStarting(false);
    }
  }

  async function publishResults(session: ManagedQuizSession) {
    const sessionResults = sessionResultsById[session.id];
    if (session.status !== "REVIEWING" || !sessionResults) return;
    setStarting(true);
    setSessionError("");
    try {
      const sendPublish = (rewardWarningAcknowledged: boolean) => apiRequest<QuizSessionCommandResponse>(
        `/classrooms/${classroomId}/quiz-sessions/${session.id}/result-commands`,
        {
          method: "POST",
          body: JSON.stringify({
            expected_state_version: session.stateVersion,
            expected_result_version: sessionResults.result_version,
            command: "PUBLISH",
            leaderboard_public: session.mode === "GAME" && leaderboardPublic,
            reward_warning_acknowledged: rewardWarningAcknowledged
          })
        }
      );
      let response: QuizSessionCommandResponse;
      try {
        response = await sendPublish(false);
      } catch (caught) {
        if (!(caught instanceof ApiError) || caught.code !== "REWARD_ISSUANCE_SHARE_EXCEEDED") throw caught;
        const confirmed = window.confirm(
          "퀴즈 보상을 지급하면 전체 발행량 중 보상 비중이 30%를 넘습니다.\n\n그래도 결과·정답을 공개하고 보상을 지급할까요?"
        );
        if (!confirmed) {
          setSessionMessage("결과 공개를 취소했어요. 보상 설정을 조정한 뒤 다시 공개할 수 있어요.");
          return;
        }
        response = await sendPublish(true);
      }
      updateManagedSession(session.id, response);
      await loadSessionResults(session.id);
      await loadSessions();
      setSessionMessage("퀴즈 결과를 학생에게 공개했어요.");
    } catch (caught) {
      setSessionError(sessionErrorMessage(caught));
    } finally {
      setStarting(false);
    }
  }

  async function generateDrafts(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitBlocked || generating) return;
    if (!Number.isInteger(questionCount) || questionCount < 1 || questionCount > 30) {
      setError("문항 수는 1개 이상 30개 이하로 입력해 주세요.");
      return;
    }
    // 넌센스는 사람이 검토한 문제은행에서 가져오므로 AI를 부르지 않는다.
    if (isNonsense) {
      await importNonsense();
      return;
    }
    if (!topic.trim() || !learningObjective.trim()) {
      setError("주제와 학습목표를 모두 입력해 주세요.");
      return;
    }

    setGenerating(true);
    setError("");
    setMessage("");
    try {
      const response = await apiRequest<QuizDraftOperationResponse>(
        `/classrooms/${classroomId}/ai/quiz-draft-operations`,
        {
          method: "POST",
          body: JSON.stringify({
            grade,
            topic: topic.trim(),
            learning_objective: learningObjective.trim(),
            content_type: contentType,
            question_type: questionType,
            question_count: questionCount,
            screen_id: "SCR-WEB-018",
            ui_action_id: "UIA-QZ-002",
            client_occurred_at: new Date().toISOString()
          })
        }
      );
      rememberBatch(response.question_ids);
      setBankFilter("recent");
      await loadQuestions();
      setMessage(`${response.question_ids.length}개 문항을 만들었어요. 정답과 해설을 확인한 뒤 승인해 주세요.`);
    } catch (caught) {
      setError(quizErrorMessage(caught));
    } finally {
      setGenerating(false);
    }
  }

  async function importNonsense() {
    setGenerating(true);
    setError("");
    setMessage("");
    try {
      const response = await apiRequest<NonsenseImportResponse>(
        `/classrooms/${classroomId}/nonsense-question-imports`,
        {
          method: "POST",
          body: JSON.stringify({
            grade,
            question_count: questionCount,
            screen_id: "SCR-WEB-018",
            ui_action_id: "UIA-QZ-004",
            client_occurred_at: new Date().toISOString()
          })
        }
      );
      rememberBatch(response.question_ids);
      setBankFilter("recent");
      await loadQuestions();
      const remaining = response.remaining_count > 0
        ? `이 학년에서 더 가져올 수 있는 문제는 ${response.remaining_count}개예요.`
        : "이 학년의 넌센스 문제를 모두 가져왔어요.";
      setMessage(`넌센스 ${response.question_ids.length}개를 가져왔어요. ${remaining}`);
    } catch (caught) {
      setError(quizErrorMessage(caught));
    } finally {
      setGenerating(false);
    }
  }

  async function changeStatus(question: QuizQuestion, command: "APPROVE" | "PUBLISH") {
    setChangingId(question.resource_id);
    setError("");
    setMessage("");
    try {
      const response = await apiRequest<QuestionStatusResponse>(
        `/classrooms/${classroomId}/quiz-questions/${question.resource_id}/commands`,
        {
          method: "POST",
          body: JSON.stringify({
            // expected_state_version은 일부러 보내지 않습니다.
            // 상태 전이 규칙(검토 필요 -> 승인 -> 게시)이 이미 잘못된 전이를 막고,
            // 값을 보내면 화면이 조금 낡았다는 이유로 승인이 거절돼 교사가 원인을
            // 알 수 없는 충돌 안내만 보게 됩니다.
            command,
            reason: command === "APPROVE"
              ? "AI 초안의 문항·정답·해설을 교사가 검토함"
              : "승인된 문항을 문제은행에 게시함",
            screen_id: "SCR-WEB-018",
            ui_action_id: "UIA-QZ-003",
            client_occurred_at: new Date().toISOString()
          })
        }
      );
      setQuestions((current) => current.map((item) => item.resource_id === question.resource_id
        ? { ...item, status: response.status, state_version: response.state_version }
        : item));
      if (command === "APPROVE") {
        // 승인한 문항은 '승인된 문제'로 옮겨 가므로 '최근 생성'에서 바로 빼 줍니다.
        setGeneratedIds((current) => {
          const next = new Set(current);
          next.delete(question.resource_id);
          writeStored(lastBatchKey(classroomId), [...next]);
          return next;
        });
        setMessage("문항을 승인했어요. '승인된 문제' 탭에서 문제은행에 게시할 수 있어요.");
      } else {
        // 게시한 문항을 아래 '퀴즈 시작'에 바로 담아 둡니다.
        // 게시만으로는 학생에게 보이지 않고, 퀴즈를 시작해야 학생 화면에 열립니다.
        setSelectedVersionIds((current) => current.includes(question.question_version_id)
          ? current
          : [...current, question.question_version_id]);
        setMessage("문항을 게시했어요. 아래 '퀴즈 시작'에 담아 두었으니 학생 화면에 열어 주세요.");
      }
    } catch (caught) {
      setError(quizErrorMessage(caught));
      await loadQuestions(true);
    } finally {
      setChangingId("");
    }
  }

  return (
    <div className="page-flow quiz-draft-workspace">
      <section className="plain-section">
        <div className="section-title">
          <span className="section-kicker">{isNonsense ? "넌센스 문제은행" : "교사용 AI 퀴즈"}</span>
          <h2>검토용 문항 생성</h2>
          <p>
            {isNonsense
              ? "넌센스는 미리 검토한 문제은행에서 가져옵니다. 학생에게 바로 공개되지 않으니 확인한 뒤 승인해 주세요."
              : "AI 문항은 학생에게 바로 공개되지 않으며 교사가 정답과 해설을 검토해야 합니다."}
          </p>
        </div>

        <form className="quiz-draft-form" onSubmit={(event) => void generateDrafts(event)}>
          {submitBlocked ? (
            <div className="chat-disabled"><AlertTriangle size={18} /> 학급 AI가 중지되어 초안을 생성할 수 없어요.</div>
          ) : null}
          <div className="quiz-draft-grid">
            <label>학년
              <select value={grade} disabled={submitBlocked || generating} onChange={(event) => setGrade(Number(event.target.value))}>
                <option value={4}>초등 4학년</option>
                <option value={5}>초등 5학년</option>
                <option value={6}>초등 6학년</option>
              </select>
            </label>
            <label>분류
              <select
                value={contentType}
                disabled={generating}
                onChange={(event) => {
                  const next = event.target.value as typeof contentType;
                  setContentType(next);
                  // 넌센스는 객관식만 만든다. OX로는 말장난이 성립하지 않는다.
                  if (next === "NONSENSE") setQuestionType("MULTIPLE_CHOICE");
                }}
              >
                <option value="ECONOMY">경제</option>
                <option value="NONSENSE">넌센스</option>
              </select>
            </label>
            <label>문항 유형
              <select value={questionType} disabled={isNonsense || submitBlocked || generating} onChange={(event) => setQuestionType(event.target.value as typeof questionType)}>
                <option value="MULTIPLE_CHOICE">객관식</option>
                {isNonsense ? null : <option value="OX">OX</option>}
              </select>
            </label>
            <label>문항 수
              <input type="number" min="1" max="30" value={questionCount} disabled={submitBlocked || generating} onChange={(event) => setQuestionCount(Number(event.target.value))} />
            </label>
            {isNonsense ? null : (
              <>
                <label className="quiz-wide-field">주제
                  <input value={topic} maxLength={200} disabled={submitBlocked || generating} onChange={(event) => setTopic(event.target.value)} />
                </label>
                <label className="quiz-wide-field">학습목표
                  <textarea rows={3} value={learningObjective} maxLength={500} disabled={submitBlocked || generating} onChange={(event) => setLearningObjective(event.target.value)} />
                </label>
              </>
            )}
          </div>
          <div className="quiz-draft-submit">
            <small>
              {isNonsense
                ? "검토를 마친 넌센스 문제를 무작위로 가져옵니다. 이미 가져간 문제는 다시 나오지 않아요."
                : "난이도는 학년에 맞춰 자동 조정합니다. 하루 최대 30개까지 생성할 수 있어요."}
            </small>
            <button type="submit" className="primary-save" disabled={submitBlocked || generating}>
              {generating ? <LoaderCircle className="spin" size={17} /> : <Sparkles size={17} />}
              {generating
                ? (isNonsense ? "문제를 가져오는 중" : "문항을 만드는 중")
                : (isNonsense ? "넌센스 문제 가져오기" : "AI 초안 생성")}
            </button>
          </div>
        </form>
      </section>

      <section className="plain-section">
        <div className="section-title">
          <span className="section-kicker">문제은행</span>
          <h2>문항 검토</h2>
          <p>정답 표시와 해설을 확인한 뒤 승인하고, 수업에 사용할 문항만 게시하세요.</p>
        </div>
        <div className="quiz-bank">
          <div className="quiz-bank-toolbar">
            <div className="recommendation-tabs">
              <button type="button" className={bankFilter === "recent" ? "active" : ""} onClick={() => setBankFilter("recent")}>최근 생성</button>
              <button type="button" className={bankFilter === "approved" ? "active" : ""} onClick={() => setBankFilter("approved")}>
                승인된 문제{approvedQuestions.length > 0 ? ` ${approvedQuestions.length}` : ""}
              </button>
            </div>
            <button type="button" className="ghost-action" disabled={loading} onClick={() => void loadQuestions()}>
              <RefreshCw size={15} /> 새로고침
            </button>
          </div>
          {message ? <div className="inline-notice" role="status"><Check size={17} /> {message}</div> : null}
          {error ? <div className="inline-error" role="alert">{error}</div> : null}
          {loading ? <div className="chat-loading"><LoaderCircle className="spin" size={18} /> 문제은행을 불러오는 중이에요.</div> : null}
          {!loading && visibleQuestions.length === 0 ? <p className="empty-copy">{emptyBankCopy}</p> : null}
          <div className="quiz-card-list">
            {visibleQuestions.map((question, index) => (
              <article className={`quiz-review-card ${generatedIds.has(question.resource_id) ? "new" : ""}`} key={question.resource_id}>
                <header>
                  <div>
                    <span>문항 {index + 1}</span>
                    {question.source === "AI" ? <b><Bot size={14} /> AI 초안</b> : <b>교사 작성</b>}
                  </div>
                  <span className={`quiz-status ${question.status.toLowerCase()}`}>{STATUS_LABEL[question.status]}</span>
                </header>
                <h3>{question.prompt}</h3>
                <ol className="quiz-choice-list">
                  {question.options.map((option) => (
                    <li className={option.correct ? "correct" : ""} key={option.option_id}>
                      <span className="quiz-choice-key">
                        {option.option_key}
                        {option.correct ? <CheckCircle2 size={18} aria-label="정답" /> : null}
                      </span>
                      <p>{option.option_text}</p>
                    </li>
                  ))}
                </ol>
                <div className="quiz-explanation">
                  <strong>해설</strong>
                  <p>{question.explanation || "등록된 해설이 없습니다."}</p>
                </div>
                <footer>
                  <small>
                    {question.content_type === "ECONOMY" ? "경제" : "넌센스"} · {question.question_type === "OX" ? "OX" : "객관식"} · 버전 {question.version}
                  </small>
                  {question.status === "DRAFT" || question.status === "REVIEW_REQUIRED" ? (
                    <button type="button" disabled={changingId === question.resource_id} onClick={() => void changeStatus(question, "APPROVE")}>검토 후 승인</button>
                  ) : null}
                  {question.status === "APPROVED" ? (
                    <button type="button" disabled={changingId === question.resource_id} onClick={() => void changeStatus(question, "PUBLISH")}>문제은행에 게시</button>
                  ) : null}
                </footer>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="plain-section">
        <div className="section-title">
          <span className="section-kicker">수업 진행</span>
          <h2>퀴즈 시작</h2>
          <p>게시된 문항 중 이번 수업에 쓸 문항을 고르면 학생 화면에 문제가 열립니다.</p>
        </div>

        {/* 진행 상태와 '답안 제출 마감'은 아래 '생성된 퀴즈'의 해당 퀴즈 칸에서 다룹니다. */}

        {publishedQuestions.length === 0 ? (
          <p className="empty-copy">
            게시된 문항이 없어요. 위에서 문항을 승인하고 &quot;문제은행에 게시&quot;까지 하면 여기에 나타납니다.
          </p>
        ) : (
          <div className="quiz-start">
            <div className="quiz-start-picker">
              <div className="quiz-start-picker-head">
                <strong>이번에 쓸 문항</strong>
                <span>{selectedQuestions.length} / {reusableQuestions.length}개 선택</span>
              </div>
              {reusableQuestions.length === 0 ? (
                <p className="empty-copy">
                  아직 쓰지 않은 문항이 없어요. 위에서 새 문항을 만들어 게시하면 여기에 나타납니다.
                </p>
              ) : (
                <ul>
                  {reusableQuestions.map((question) => (
                    <li key={question.question_version_id}>
                      <label>
                        <input
                          type="checkbox"
                          checked={selectedVersionIds.includes(question.question_version_id)}
                          disabled={starting}
                          onChange={() => toggleQuestion(question.question_version_id)}
                        />
                        <span className="quiz-start-prompt">{question.prompt}</span>
                        <small>
                          {question.content_type === "ECONOMY" ? "경제" : "넌센스"} · {question.question_type === "OX" ? "OX" : "객관식"}
                        </small>
                      </label>
                    </li>
                  ))}
                </ul>
              )}

            </div>

            <div className="quiz-start-form">
              <label>운영 방식
                <select value={sessionMode} disabled={starting} onChange={(event) => {
                  setSessionMode(event.target.value as "GAME" | "TEST");
                  setSessionError("");
                  setSessionMessage("");
                }}>
                  <option value="GAME">게임형 · 점수와 보상이 있어요</option>
                  <option value="TEST">평가형 · 결과를 공개하지 않아요</option>
                </select>
              </label>
              {sessionMode === "GAME" ? (
                <>
                  <label>문제당 보상 · 맞힌 문제 수만큼 지급해요
                    <input
                      type="number"
                      min="0"
                      step="1"
                      value={rewardPerPerson}
                      disabled={starting}
                      onChange={(event) => setRewardPerPerson(event.target.value)}
                    />
                    {invalidReward ? <em className="quiz-start-invalid">0 이상의 정수로 입력해 주세요.</em> : null}
                  </label>
                  <label className="quiz-start-toggle">
                    <input
                      type="checkbox"
                      checked={leaderboardPublic}
                      disabled={starting}
                      onChange={(event) => setLeaderboardPublic(event.target.checked)}
                    />
                    순위를 학생에게 공개
                  </label>
                </>
              ) : (
                <p className="section-description">평가형 퀴즈는 보상과 순위 공개가 없습니다.</p>
              )}
              <dl className="quiz-start-summary">
                <div><dt>참여 학생</dt><dd>{participantIds.length}명</dd></div>
                <div>
                  <dt>문항 분류</dt>
                  <dd>{selectedContentTypes.length === 0
                    ? "-"
                    : mixedContentTypes
                      ? "섞여 있음"
                      : selectedContentTypes[0] === "ECONOMY" ? "경제" : "넌센스"}</dd>
                </div>
              </dl>
            </div>

            {mixedContentTypes ? (
              <div className="inline-error" role="alert">
                한 퀴즈에는 같은 분류의 문항만 넣을 수 있어요. 경제와 넌센스 중 하나만 골라 주세요.
              </div>
            ) : null}
            {participantIds.length === 0 ? (
              <div className="inline-notice">
                <AlertTriangle size={17} /> 참여할 학생이 없어요. 학생·급여 화면에서 명단을 저장한 뒤 다시 시도해 주세요.
              </div>
            ) : null}
            {hasOpenSession ? (
              <div className="inline-notice">
                <AlertTriangle size={17} /> 미완료 퀴즈가 있어요. 아래에서 마감·채점·결과 공개를 끝내야 새 퀴즈나 회차 수업을 시작할 수 있어요.
              </div>
            ) : null}
            <button type="button" className="wide-action" disabled={!canStart} onClick={() => void startSession()}>
              {starting ? <LoaderCircle className="spin" size={17} /> : <Play size={17} />}
              {starting ? "퀴즈를 여는 중" : hasOpenSession ? "이전 퀴즈를 먼저 완료해 주세요" : "학생 화면에 퀴즈 열기"}
            </button>
          </div>
        )}
      </section>

      <section className="plain-section">
        <div className="section-title">
          <span className="section-kicker">수업 진행</span>
          <h2>생성된 퀴즈</h2>
          <p>학생 화면에 연 퀴즈를 퀴즈별로 나눠서 봅니다. 여기에 쓴 문항은 다시 열 수 없어요.</p>
        </div>

        {/* 퀴즈를 열거나 마감·공개한 결과 알림은 진행 상태를 다루는 이 구역에서 보여 줍니다. */}
        {sessionMessage || sessionError ? (
          <div className="quiz-start-feedback" aria-live="polite">
            {sessionMessage ? <div className="quiz-start-success"><CheckCircle2 size={16} /> {sessionMessage}</div> : null}
            {sessionError ? <div className="quiz-start-failure" role="alert">{sessionError}</div> : null}
          </div>
        ) : null}

        {openedQuizzes.length === 0 && unrecordedUsedQuestions.length === 0 ? (
          <p className="empty-copy">
            아직 연 퀴즈가 없어요. 위에서 문항을 고르고 &quot;학생 화면에 퀴즈 열기&quot;를 누르면 여기에 쌓입니다.
          </p>
        ) : (
          <div className="opened-quiz-list">
            {openedQuizzes.map((quiz, index) => {
              const managedSession = managedSessions.find((session) => session.id === quiz.sessionId);
              const sessionResults = sessionResultsById[quiz.sessionId];
              return (
                <article
                className={`opened-quiz-card${quiz.sessionId === openSessionId ? " live" : ""}`}
                key={quiz.sessionId}
                >
                <header>
                  <div>
                    <strong>퀴즈 {openedQuizzes.length - index}</strong>
                    <small>
                      {quiz.contentType === "ECONOMY" ? "경제" : "넌센스"} · {quiz.mode === "GAME" ? "게임형" : "평가형"} · 문항 {quiz.questions.length}개
                    </small>
                  </div>
                  {quiz.sessionId === openSessionId ? <span className="opened-quiz-live">진행 중</span> : null}
                </header>
                <ol>
                  {quiz.questions.map((question) => (
                    <li key={question.versionId}>{question.prompt}</li>
                  ))}
                </ol>
                {/* 진행 상태와 다음 단계 버튼은 그 퀴즈 칸 안에 둡니다. 위 '퀴즈 시작'에 두면
                    어느 퀴즈를 마감하는 것인지 알 수 없습니다. */}
                {managedSession ? (
                  <div className="quiz-start-feedback" aria-live="polite">
                    <div className="inline-notice">
                      <strong>현재 세션 · {managedSession.status === "OPEN" ? "학생 답안 제출 중" : managedSession.status === "SCORING" ? "제출 마감·채점 대기" : managedSession.status === "REVIEWING" ? "결과 검토 중" : managedSession.status === "RESULT_PUBLISHED" ? "결과 공개됨" : managedSession.status === "CLOSED" ? "종료됨" : managedSession.status}</strong>
                      {managedSession.status === "OPEN" ? <button type="button" disabled={starting} onClick={() => void closeSubmissions(managedSession)}>답안 제출 마감</button> : null}
                      {managedSession.status === "SCORING" ? <button type="button" disabled={starting} onClick={() => void confirmResults(managedSession)}>채점 결과 계산</button> : null}
                      {managedSession.status === "REVIEWING" ? <button type="button" disabled={starting || !sessionResults} onClick={() => void publishResults(managedSession)}>결과·정답 학생 공개</button> : null}
                    </div>
                    {sessionResults ? (
                      <div className="quiz-start-success">
                        <CheckCircle2 size={16} /> 결과 버전 {sessionResults.result_version} · 참여 결과 {String(sessionResults.summary.participant_count ?? sessionResults.leaderboard.length)}명
                        {sessionResults.leaderboard.length ? <span> · {sessionResults.leaderboard.slice(0, 3).map((entry) => `${entry.rank}위 ${entry.nickname}`).join(", ")}</span> : null}
                      </div>
                    ) : null}
                  </div>
                ) : null}
                </article>
              );
            })}

            {unrecordedUsedQuestions.length > 0 ? (
              <article className="opened-quiz-card">
                <header>
                  <div>
                    <strong>이전에 진행한 문항</strong>
                    <small>문항 {unrecordedUsedQuestions.length}개</small>
                  </div>
                </header>
                <p className="section-description">
                  다른 기기에서 열었거나 기록이 남지 않은 문항이에요. 어느 퀴즈였는지는 알 수 없지만 다시 열 수는 없습니다.
                </p>
                <ol>
                  {unrecordedUsedQuestions.map((question) => (
                    <li key={question.question_version_id}>{question.prompt}</li>
                  ))}
                </ol>
              </article>
            ) : null}
          </div>
        )}
      </section>
    </div>
  );
}
