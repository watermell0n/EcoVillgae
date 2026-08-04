"use client";

import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { AlertTriangle, Bot, CalendarDays, Database, LoaderCircle, Send, Sparkles } from "lucide-react";
import { ApiError, apiRequest } from "../api";
import type { TeacherChatEvidence, TeacherChatResponse } from "../api";

const METRIC_OPTIONS = [
  { code: "LIQUID_MONEY_CHANGE_RATE_BPS", label: "통화량 변화" },
  { code: "ISSUANCE_AMOUNT", label: "발행액" },
  { code: "RECOVERY_AMOUNT", label: "회수액" },
  { code: "CONSUMPTION_RATE_BPS", label: "소비율" },
  { code: "ASSET_CONCENTRATION_TOP20_BPS", label: "자산 집중도" },
  { code: "PRODUCT_SELL_THROUGH_RATE_BPS", label: "상품 판매율" },
  { code: "FINANCIAL_PARTICIPATION_RATE_BPS", label: "금융 참여율" },
  { code: "INVESTMENT_PARTICIPATION_RATE_BPS", label: "투자 참여율" },
  { code: "INVESTMENT_MEDIAN_RETURN_BPS", label: "투자 수익률 중앙값" },
  { code: "REWARD_ISSUANCE_SHARE_BPS", label: "보상 발행 비중" }
] as const;

const DEFAULT_METRICS = new Set([
  "LIQUID_MONEY_CHANGE_RATE_BPS",
  "ISSUANCE_AMOUNT",
  "RECOVERY_AMOUNT",
  "CONSUMPTION_RATE_BPS",
  "ASSET_CONCENTRATION_TOP20_BPS"
]);

const QUICK_QUESTIONS = [
  "최근 학급 소비 흐름에서 눈여겨볼 점을 알려줘.",
  "학급 화폐 발행과 회수 상태를 설명해줘.",
  "현재 경제 운영에서 교사가 확인할 위험 신호가 있을까?"
];

type ChatEntry = {
  id: string;
  question: string;
  response: TeacherChatResponse;
};

function chatErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) {
    if (error instanceof Error && error.name === "AbortError") {
      return "답변 시간이 초과됐어요. 잠시 후 다시 질문해 주세요.";
    }
    return "AI 답변을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";
  }
  if (error.code === "AI_CHAT_DAILY_LIMIT_EXCEEDED" || error.status === 429) {
    return "오늘 사용할 수 있는 AI 질문 30회를 모두 사용했어요.";
  }
  if (error.code === "AI_DISABLED") return "현재 이 학급의 AI 기능이 중지되어 있어요.";
  if (error.code === "INDIVIDUAL_ANALYSIS_FORBIDDEN") {
    return "학생 개인 분석은 지원하지 않아요. 학급 전체 흐름을 질문해 주세요.";
  }
  if (error.status === 403) return "담당 학급의 교사만 AI 챗봇을 사용할 수 있어요.";
  return error.message;
}

function rangeFor(days: number, classroomStartDate: string) {
  const to = new Date();
  const rollingFrom = new Date(to.getTime() - days * 24 * 60 * 60 * 1000);
  const classroomStart = new Date(`${classroomStartDate}T00:00:00`);
  const from = Number.isNaN(classroomStart.getTime()) || classroomStart > to
    ? rollingFrom
    : new Date(Math.max(rollingFrom.getTime(), classroomStart.getTime()));
  return { from: from.toISOString(), to: to.toISOString() };
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatEvidence(item: TeacherChatEvidence) {
  if (typeof item.value === "number") {
    if (item.unit === "RATE_BPS") return `${(item.value / 100).toFixed(2)}%`;
    return item.value.toLocaleString("ko-KR");
  }
  return item.value;
}

export function TeacherChatPanel({
  classroomId,
  classroomStartDate,
  enabled
}: Readonly<{
  classroomId: string;
  classroomStartDate: string;
  enabled: boolean;
}>) {
  const [question, setQuestion] = useState("");
  const [periodDays, setPeriodDays] = useState(7);
  const [metricScopes, setMetricScopes] = useState<Set<string>>(() => new Set(DEFAULT_METRICS));
  const [includePolicyHistory, setIncludePolicyHistory] = useState(true);
  const [entries, setEntries] = useState<ChatEntry[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setQuestion("");
    setEntries([]);
    setError("");
  }, [classroomId]);

  const latestEntry = entries.at(-1);
  const canSubmit = enabled && !submitting && question.trim().length > 0 && metricScopes.size > 0;
  const selectedMetricLabels = useMemo(
    () => METRIC_OPTIONS.filter((item) => metricScopes.has(item.code)).map((item) => item.label),
    [metricScopes]
  );

  function toggleMetric(code: string) {
    setMetricScopes((current) => {
      const next = new Set(current);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  }

  async function ask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = question.trim();
    if (!trimmed || submitting) return;
    if (metricScopes.size === 0) {
      setError("답변에 사용할 학급 지표를 하나 이상 선택해 주세요.");
      return;
    }

    setSubmitting(true);
    setError("");
    try {
      const period = rangeFor(periodDays, classroomStartDate);
      const response = await apiRequest<TeacherChatResponse>(`/classrooms/${classroomId}/ai/chat-requests`, {
        method: "POST",
        body: JSON.stringify({
          question: trimmed,
          period,
          metric_scopes: [...metricScopes],
          include_policy_history: includePolicyHistory,
          screen_id: "SCR-WEB-022",
          ui_action_id: "UIA-AI-001",
          client_occurred_at: new Date().toISOString()
        })
      });
      setEntries((current) => [...current, { id: response.ai_run_id, question: trimmed, response }]);
      setQuestion("");
    } catch (caught) {
      setError(chatErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="plain-section teacher-chat-panel">
      <div className="section-title">
        <span className="section-kicker">교사용 AI 챗봇</span>
        <h2>학급 경제 질문</h2>
        <p>학생 개인이 아닌 학급 집계만 근거로 원인과 대응안을 설명합니다.</p>
      </div>

      <div className="teacher-chat-content">
        {!enabled ? (
          <div className="chat-disabled"><AlertTriangle size={18} /> 학급 AI가 중지되어 챗봇을 사용할 수 없어요.</div>
        ) : null}

        <div className="chat-quick-questions" aria-label="추천 질문">
          {QUICK_QUESTIONS.map((item) => (
            <button key={item} type="button" disabled={!enabled || submitting} onClick={() => setQuestion(item)}>
              <Sparkles size={14} /> {item}
            </button>
          ))}
        </div>

        <form className="teacher-chat-form" onSubmit={(event) => void ask(event)}>
          <label className="chat-question">
            질문
            <textarea
              value={question}
              maxLength={1000}
              rows={4}
              disabled={!enabled || submitting}
              placeholder="예: 최근 소비율이 높아진 원인을 학급 지표로 설명해줘."
              onChange={(event) => setQuestion(event.target.value)}
            />
            <small>{question.length}/1000</small>
          </label>

          <div className="chat-options">
            <label>
              조회 기간
              <select value={periodDays} disabled={!enabled || submitting} onChange={(event) => setPeriodDays(Number(event.target.value))}>
                <option value={7}>최근 7일</option>
                <option value={14}>최근 14일</option>
                <option value={30}>최근 30일</option>
              </select>
            </label>
            <label className="chat-policy-option">
              <input
                type="checkbox"
                checked={includePolicyHistory}
                disabled={!enabled || submitting}
                onChange={(event) => setIncludePolicyHistory(event.target.checked)}
              />
              기간 내 정책 변경 이력 포함
            </label>
          </div>

          <fieldset className="chat-metric-options" disabled={!enabled || submitting}>
            <legend>답변 근거 범위</legend>
            {METRIC_OPTIONS.map((item) => (
              <label key={item.code}>
                <input type="checkbox" checked={metricScopes.has(item.code)} onChange={() => toggleMetric(item.code)} />
                {item.label}
              </label>
            ))}
          </fieldset>
          <div className="chat-form-footer">
            <small>선택 지표: {selectedMetricLabels.length ? selectedMetricLabels.join(", ") : "없음"}</small>
            <button type="submit" className="primary-save" disabled={!canSubmit}>
              {submitting ? <LoaderCircle className="spin" size={17} /> : <Send size={17} />}
              {submitting ? "근거를 분석하는 중" : "AI에게 질문"}
            </button>
          </div>
        </form>

        {error ? <div className="inline-error" role="alert">{error}</div> : null}
        {submitting ? (
          <div className="chat-loading" role="status">
            <LoaderCircle className="spin" size={20} /> 학급 집계와 정책 이력을 확인하고 있어요.
          </div>
        ) : null}

        {latestEntry ? (
          <article className="chat-answer" aria-live="polite">
            <div className="chat-question-bubble"><strong>질문</strong><p>{latestEntry.question}</p></div>
            <div className="chat-answer-heading"><Bot size={20} /><strong>AI 답변</strong></div>
            <p className="chat-answer-text">{latestEntry.response.answer}</p>

            <div className="chat-answer-meta">
              <span><CalendarDays size={15} /> {formatDateTime(latestEntry.response.period.from)} ~ {formatDateTime(latestEntry.response.period.to)}</span>
              <span><Database size={15} /> 데이터 기준 {formatDateTime(latestEntry.response.data_as_of)}</span>
            </div>

            <section className="chat-evidence">
              <h3>답변 근거</h3>
              <dl>
                {latestEntry.response.evidence.map((item) => (
                  <div key={item.fact_id}>
                    <dt>{item.label}</dt>
                    <dd>{formatEvidence(item)}</dd>
                  </div>
                ))}
              </dl>
            </section>

            <section className="chat-recommendations">
              <h3>대응안과 고려할 점</h3>
              {latestEntry.response.recommendations.length ? (
                <ol>
                  {latestEntry.response.recommendations.map((item, index) => (
                    <li key={`${latestEntry.id}-recommendation-${index}`}>
                      <strong>{item.text}</strong>
                      <ul>{item.side_effects.map((sideEffect) => <li key={sideEffect}>{sideEffect}</li>)}</ul>
                    </li>
                  ))}
                </ol>
              ) : (
                <p>이 답변은 안내 범위가 제한되어 별도 대응안을 제시하지 않았어요.</p>
              )}
            </section>
            <p className="chat-readonly-note">AI 답변은 설명용이며 학급 설정을 자동으로 변경하지 않습니다.</p>
          </article>
        ) : null}

        {entries.length > 1 ? (
          <details className="chat-history">
            <summary>이 화면에서 한 이전 질문 {entries.length - 1}개</summary>
            {entries.slice(0, -1).reverse().map((entry) => (
              <button key={entry.id} type="button" onClick={() => setEntries((current) => [...current.filter((item) => item.id !== entry.id), entry])}>
                {entry.question}
              </button>
            ))}
          </details>
        ) : null}
        <small className="chat-limit-note">교사별 하루 최대 30회 · 답변 제한 20초 · 실패 시 1회 자동 재시도</small>
      </div>
    </section>
  );
}
