"use client";

import { useMemo, useState } from "react";
import { CheckCircle2, LoaderCircle, ListChecks } from "lucide-react";

import { ApiError, apiRequest } from "../api";
import {
  economyQuizBankVersion,
  economyQuizDifficulties,
  economyQuizQuestionsOf,
  type EconomyQuizDifficulty,
  type EconomyQuizQuestion
} from "./economyQuizBank";

type QuizResourceResponse = {
  quiz_resource_id: string;
  status: string;
  state_version: number;
};

const SCREEN_ID = "SCR-WEB-018";

function errorMessage(caught: unknown) {
  if (caught instanceof ApiError) return caught.message;
  return "문항을 등록하지 못했어요. 잠시 뒤 다시 시도해 주세요.";
}

/**
 * 수업자료로 만든 고정 문항을 난이도별로 골라 학급 문제은행에 등록한다.
 *
 * <p>난이도는 문항 저장 스키마에 없으므로 등록 전 고르는 기준으로만 쓴다. 등록된 뒤에는
 * 평범한 경제 문항이라 기존 문제은행·퀴즈 시작 화면이 그대로 이어받는다.
 * 게시까지 해 두더라도 학생 화면에는 아직 보이지 않고, 아래 '퀴즈 시작'에서 열어야 한다.
 */
export function EconomyQuizDifficultyPanel({
  classroomId,
  canManage,
  onRegistered
}: Readonly<{
  classroomId: string;
  canManage: boolean;
  onRegistered?: (questionIds: string[]) => void;
}>) {
  const [difficulty, setDifficulty] = useState<EconomyQuizDifficulty>("EASY");
  const [expanded, setExpanded] = useState(false);
  const [progress, setProgress] = useState(0);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [registered, setRegistered] = useState<Record<EconomyQuizDifficulty, number>>({
    EASY: 0,
    NORMAL: 0,
    HARD: 0
  });

  const questions = useMemo(() => economyQuizQuestionsOf(difficulty), [difficulty]);
  const selected = economyQuizDifficulties.find((item) => item.value === difficulty)!;

  /**
   * 문항 하나를 만들고 바로 승인·게시까지 올린다.
   * 사람이 미리 검토한 고정 문항이라 AI 초안처럼 검토 단계를 따로 두지 않는다.
   */
  async function registerOne(question: EconomyQuizQuestion) {
    const created = await apiRequest<QuizResourceResponse>(
      `/classrooms/${classroomId}/quiz-question-commands`,
      {
        method: "POST",
        // 서버는 snake_case로 받습니다.
        body: JSON.stringify({
          command: "CREATE",
          content_type: "ECONOMY",
          question_type: "MULTIPLE_CHOICE",
          prompt: question.question,
          options: question.choices.map((choice, index) => ({
            option_key: choice.key,
            option_text: choice.text,
            is_correct: choice.key === question.correctChoiceKey,
            sort_order: index + 1
          })),
          explanation: question.explanation,
          screen_id: SCREEN_ID,
          ui_action_id: "UIA-QZ-005",
          client_occurred_at: new Date().toISOString()
        })
      }
    );

    // expected_state_version은 보내지 않습니다. 상태 전이 규칙이 잘못된 전이를 이미 막고,
    // 값을 보내면 화면이 조금 낡았다는 이유로 등록이 중간에 끊길 수 있습니다.
    for (const command of ["APPROVE", "PUBLISH"] as const) {
      await apiRequest<QuizResourceResponse>(
        `/classrooms/${classroomId}/quiz-questions/${created.quiz_resource_id}/commands`,
        {
          method: "POST",
          body: JSON.stringify({
            command,
            reason: command === "APPROVE"
              ? `수업자료 기반 고정 문항(${economyQuizBankVersion})을 교사가 확인함`
              : "확인한 문항을 문제은행에 게시함",
            screen_id: SCREEN_ID,
            ui_action_id: "UIA-QZ-006",
            client_occurred_at: new Date().toISOString()
          })
        }
      );
    }
    return created.quiz_resource_id;
  }

  /**
   * 문항을 하나씩 순서대로 등록한다.
   * 한 번에 병렬로 보내면 어디까지 됐는지 알 수 없고, 중간에 실패하면 교사가
   * 무엇을 다시 해야 하는지 판단할 수 없다.
   */
  async function registerSelected() {
    if (busy) return;
    setBusy(true);
    setError("");
    setMessage("");
    setProgress(0);
    const done: string[] = [];
    try {
      for (const question of questions) {
        done.push(await registerOne(question));
        setProgress(done.length);
      }
      setRegistered((current) => ({ ...current, [difficulty]: current[difficulty] + done.length }));
      setMessage(
        `${selected.label} ${done.length}문항을 문제은행에 게시했어요. 학생에게 보여 주려면 아래 '퀴즈 시작'에서 열어 주세요.`
      );
      onRegistered?.(done);
    } catch (caught) {
      setError(
        done.length > 0
          ? `${done.length}문항까지 등록한 뒤 멈췄어요. ${errorMessage(caught)} 다시 누르면 남은 문항이 아니라 10문항을 처음부터 등록하므로, 문제은행에서 중복을 확인해 주세요.`
          : errorMessage(caught)
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="economy-quiz-panel">
      <div className="economy-quiz-heading">
        <h2>수업자료 퀴즈 고르기</h2>
        <p>
          경제학습 수업자료로 미리 만들어 둔 4지선다 문항입니다. 난이도를 고르면 그 10문항이 학급
          문제은행에 게시됩니다. AI 초안과 달리 매번 새로 만들지 않습니다.
        </p>
      </div>
      <div className="economy-quiz-levels" role="list">
        {economyQuizDifficulties.map((item) => {
          const count = economyQuizQuestionsOf(item.value).length;
          return (
            <button
              type="button"
              role="listitem"
              key={item.value}
              className={item.value === difficulty ? "economy-quiz-level active" : "economy-quiz-level"}
              aria-pressed={item.value === difficulty}
              disabled={busy}
              onClick={() => {
                setDifficulty(item.value);
                setMessage("");
                setError("");
                setProgress(0);
              }}
            >
              <span className="economy-quiz-level-top">
                <strong>{item.label}</strong>
                <span className="economy-quiz-level-count">{count}문항</span>
              </span>
              <span className="economy-quiz-level-desc">{item.description}</span>
              {registered[item.value] > 0 ? (
                <em className="economy-quiz-done">
                  <CheckCircle2 size={13} aria-hidden /> 등록 {registered[item.value]}문항
                </em>
              ) : null}
            </button>
          );
        })}
      </div>

      <div className="economy-quiz-actions">
        <button type="button" disabled={!canManage || busy} onClick={() => void registerSelected()}>
          {busy ? (
            <>
              <LoaderCircle size={15} className="spin" aria-hidden /> 등록 중 {progress}/{questions.length}
            </>
          ) : (
            <>
              <ListChecks size={15} aria-hidden /> {selected.label} {questions.length}문항 등록
            </>
          )}
        </button>
        <button type="button" className="ghost" disabled={busy} onClick={() => setExpanded((value) => !value)}>
          {expanded ? "문항 접기" : "문항 미리보기"}
        </button>
      </div>

      {canManage ? null : <p className="helper-text">문항을 등록할 권한이 없습니다.</p>}
      {message ? <p className="economy-quiz-message">{message}</p> : null}
      {error ? <p className="economy-quiz-error">{error}</p> : null}

      {expanded ? (
        <ol className="economy-quiz-preview">
          {questions.map((question) => (
            <li key={question.id}>
              <div className="economy-quiz-preview-head">
                <strong>{question.question}</strong>
                <span>
                  {question.lessonNo}. {question.lessonTitle}
                </span>
              </div>
              <ul>
                {question.choices.map((choice) => (
                  <li
                    key={choice.key}
                    className={choice.key === question.correctChoiceKey ? "correct" : undefined}
                  >
                    {choice.key}. {choice.text}
                    {choice.key === question.correctChoiceKey ? " (정답)" : ""}
                  </li>
                ))}
              </ul>
              <p className="helper-text">{question.explanation}</p>
            </li>
          ))}
        </ol>
      ) : null}
    </div>
  );
}
