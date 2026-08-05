"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { AlertTriangle, Bot, Check, CheckCircle2, LoaderCircle, Play, RefreshCw, Sparkles } from "lucide-react";
import { ApiError, apiRequest } from "../api";
import type { QuizDraftOperationResponse, QuizQuestion, QuizSessionCommandResponse } from "../api";

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

// 문제은행 목록에서 무엇을 보여 줄지 고르는 탭입니다.
type BankFilter = "recent" | "approved" | "all";

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
  onReload
}: Readonly<{
  classroomId: string;
  initialGrade: number;
  aiEnabled: boolean;
  students: readonly QuizStudent[];
  // 진행 중인 퀴즈 세션 id입니다. 활성 퀴즈 모듈 config에서 옵니다.
  activeQuizSessionId?: string;
  onReload?: () => Promise<void>;
}>) {
  const [grade, setGrade] = useState(initialGrade);
  const [topic, setTopic] = useState("합리적인 소비");
  const [learningObjective, setLearningObjective] = useState("필요와 욕구를 구분하고 계획적인 소비 방법을 설명한다.");
  const [contentType, setContentType] = useState<"ECONOMY" | "NONSENSE">("ECONOMY");
  const [questionType, setQuestionType] = useState<"MULTIPLE_CHOICE" | "OX">("MULTIPLE_CHOICE");
  const [questionCount, setQuestionCount] = useState(3);
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
  const [startedSession, setStartedSession] = useState<{ id: string; mode: string } | null>(null);
  // 이미 학생 화면에 열어 준 문항입니다. 같은 문항으로 두 번 열지 않도록 잠급니다.
  const [openedVersionIds, setOpenedVersionIds] = useState<string[]>([]);
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
    } catch (caught) {
      setError(quizErrorMessage(caught));
    } finally {
      setLoading(false);
    }
  }, [classroomId]);

  useEffect(() => {
    setGeneratedIds(new Set());
    setBankFilter("recent");
    setMessage("");
    setError("");
    setSelectedVersionIds([]);
    setOpenedVersionIds([]);
    setStartedSession(null);
    setSessionError("");
    setSessionMessage("");
    void loadQuestions();
  }, [classroomId, loadQuestions]);

  // 이미 진행 중인 세션도 다시 읽어, 페이지를 나갔다 와도 같은 문항을
  // 또 선택해 새 퀴즈를 열지 않도록 합니다.
  useEffect(() => {
    if (!activeQuizSessionId) return;
    void apiRequest<{
      questions: Array<{ quiz_question_version_id?: string }>;
    }>(`/classrooms/${classroomId}/quiz-sessions/${activeQuizSessionId}`)
      .then((session) => {
        setOpenedVersionIds(session.questions.flatMap((question) => (
          question.quiz_question_version_id ? [question.quiz_question_version_id] : []
        )));
      })
      .catch(() => {
        // 세션 정보를 바로 읽지 못해도 새 시작 요청의 서버 검증은 유지합니다.
      });
  }, [activeQuizSessionId, classroomId]);

  const approvedQuestions = useMemo(
    () => questions.filter((question) => question.status === "APPROVED"),
    [questions]
  );

  const visibleQuestions = useMemo(() => {
    if (bankFilter === "approved") return approvedQuestions;
    if (bankFilter === "all" || generatedIds.size === 0) return questions;
    return questions.filter((question) => generatedIds.has(question.resource_id));
  }, [approvedQuestions, bankFilter, generatedIds, questions]);

  const emptyBankCopy = bankFilter === "approved"
    ? "승인한 문항이 없어요. 문항을 검토한 뒤 '검토 후 승인'을 누르면 여기로 옵니다."
    : "표시할 문항이 없어요. AI 초안을 먼저 생성해 보세요.";

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
  // 서버가 알려 주는 진행 중 세션을 우선 쓰고, 아직 반영 전이면 방금 시작한 세션을 씁니다.
  const openSessionId = activeQuizSessionId || startedSession?.id || "";
  const rewardValue = Number(rewardPerPerson);
  const hasOpenSession = Boolean(openSessionId);
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
  const invalidReward = sessionMode === "GAME"
    && (!Number.isInteger(rewardValue) || rewardValue < 0);
  const canStart = !starting
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
    // 진행 중인 퀴즈가 있으면 새 퀴즈가 그 자리를 대신합니다. 학생 화면은 활성 모듈에
    // 기록된 세션 하나만 읽으므로, 교사가 모르고 바꾸지 않도록 먼저 확인받습니다.
    if (hasOpenSession && !window.confirm("진행 중인 퀴즈를 새 퀴즈로 바꿀까요? 학생 화면에는 새 문항이 열립니다.")) {
      return;
    }
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
      setStartedSession({ id: response.quiz_resource_id, mode: sessionMode });
      // 같은 문항으로 다시 열지 못하게 잠급니다.
      setOpenedVersionIds((current) => [...new Set([...current, ...selectedVersionIds])]);
      setSelectedVersionIds([]);
      setSessionMessage(
        `${openedCount}개 문항으로 ${sessionMode === "GAME" ? "게임형" : "평가형"} 퀴즈를 열었어요. 학생 화면에서 바로 풀 수 있습니다.`
      );
      // 학급 상태를 다시 읽어 진행 중인 퀴즈 세션 정보를 최신으로 맞춥니다.
      if (onReload) await onReload();
    } catch (caught) {
      setSessionError(sessionErrorMessage(caught));
      if (caught instanceof ApiError && caught.code === "QUESTION_NOT_PUBLISHED") {
        await loadQuestions(true);
      }
    } finally {
      setStarting(false);
    }
  }

  async function generateDrafts(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!aiEnabled || generating) return;
    if (!topic.trim() || !learningObjective.trim()) {
      setError("주제와 학습목표를 모두 입력해 주세요.");
      return;
    }
    if (!Number.isInteger(questionCount) || questionCount < 1 || questionCount > 30) {
      setError("문항 수는 1개 이상 30개 이하로 입력해 주세요.");
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
      setGeneratedIds(new Set(response.question_ids));
      setBankFilter("recent");
      await loadQuestions();
      setMessage(`${response.question_ids.length}개 문항을 만들었어요. 정답과 해설을 확인한 뒤 승인해 주세요.`);
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
          <span className="section-kicker">교사용 AI 퀴즈</span>
          <h2>검토용 문항 생성</h2>
          <p>AI 문항은 학생에게 바로 공개되지 않으며 교사가 정답과 해설을 검토해야 합니다.</p>
        </div>

        <form className="quiz-draft-form" onSubmit={(event) => void generateDrafts(event)}>
          {!aiEnabled ? (
            <div className="chat-disabled"><AlertTriangle size={18} /> 학급 AI가 중지되어 초안을 생성할 수 없어요.</div>
          ) : null}
          <div className="quiz-draft-grid">
            <label>학년
              <select value={grade} disabled={!aiEnabled || generating} onChange={(event) => setGrade(Number(event.target.value))}>
                <option value={4}>초등 4학년</option>
                <option value={5}>초등 5학년</option>
                <option value={6}>초등 6학년</option>
              </select>
            </label>
            <label>분류
              <select value={contentType} disabled={!aiEnabled || generating} onChange={(event) => setContentType(event.target.value as typeof contentType)}>
                <option value="ECONOMY">경제</option>
                <option value="NONSENSE">넌센스</option>
              </select>
            </label>
            <label>문항 유형
              <select value={questionType} disabled={!aiEnabled || generating} onChange={(event) => setQuestionType(event.target.value as typeof questionType)}>
                <option value="MULTIPLE_CHOICE">객관식</option>
                <option value="OX">OX</option>
              </select>
            </label>
            <label>문항 수
              <input type="number" min="1" max="30" value={questionCount} disabled={!aiEnabled || generating} onChange={(event) => setQuestionCount(Number(event.target.value))} />
            </label>
            <label className="quiz-wide-field">주제
              <input value={topic} maxLength={200} disabled={!aiEnabled || generating} onChange={(event) => setTopic(event.target.value)} />
            </label>
            <label className="quiz-wide-field">학습목표
              <textarea rows={3} value={learningObjective} maxLength={500} disabled={!aiEnabled || generating} onChange={(event) => setLearningObjective(event.target.value)} />
            </label>
          </div>
          <div className="quiz-draft-submit">
            <small>난이도는 학년에 맞춰 자동 조정합니다. 하루 최대 30개까지 생성할 수 있어요.</small>
            <button type="submit" className="primary-save" disabled={!aiEnabled || generating}>
              {generating ? <LoaderCircle className="spin" size={17} /> : <Sparkles size={17} />}
              {generating ? "문항을 만드는 중" : "AI 초안 생성"}
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
              <button type="button" className={bankFilter === "all" ? "active" : ""} onClick={() => setBankFilter("all")}>전체 문제은행</button>
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
                      <span>{option.option_key}</span>
                      <p>{option.option_text}</p>
                      {option.correct ? <CheckCircle2 size={18} /> : null}
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

              {usedQuestions.length > 0 ? (
                <details className="quiz-used-questions">
                  <summary>이미 진행한 문항 {usedQuestions.length}개</summary>
                  <p className="section-description">
                    학생에게 한 번 열었던 문항입니다. 같은 문항으로 다시 열 수 없고, 문제은행에는 그대로 남습니다.
                  </p>
                  <ul>
                    {usedQuestions.map((question) => (
                      <li key={question.question_version_id}>
                        <span className="quiz-start-prompt">{question.prompt}</span>
                      </li>
                    ))}
                  </ul>
                </details>
              ) : null}
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
                  <label>1인 참여 보상
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
                <AlertTriangle size={17} /> 진행 중인 퀴즈가 있어요. 새 문항으로 열면 학생 화면이 새 퀴즈로 바뀝니다.
              </div>
            ) : null}
            <button type="button" className="wide-action" disabled={!canStart} onClick={() => void startSession()}>
              {starting ? <LoaderCircle className="spin" size={17} /> : <Play size={17} />}
              {starting ? "퀴즈를 여는 중" : hasOpenSession ? "새 퀴즈로 바꿔 열기" : "학생 화면에 퀴즈 열기"}
            </button>
            {sessionMessage || sessionError ? (
              <div className="quiz-start-feedback" aria-live="polite">
                {sessionMessage ? <div className="quiz-start-success"><CheckCircle2 size={16} /> {sessionMessage}</div> : null}
                {sessionError ? <div className="quiz-start-failure" role="alert">{sessionError}</div> : null}
              </div>
            ) : null}
          </div>
        )}
      </section>
    </div>
  );
}
