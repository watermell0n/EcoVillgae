"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, Bell, Check, LoaderCircle, RefreshCw, SlidersHorizontal } from "lucide-react";
import {
  ApiError,
  apiRequest,
  valueForDisplay,
  type AiProposalCommandResponse,
  type AiRunHistoryItem,
  type BalancingMetricCode,
  type BalancingProposal,
  type OperationalAnalysisResponse
} from "../api";

const METRICS: Array<{ code: BalancingMetricCode; label: string }> = [
  { code: "LIQUID_MONEY_CHANGE_RATE_BPS", label: "유동 화폐 변화율" },
  { code: "ISSUANCE_RECOVERY_RATE_BPS", label: "발행·회수 비율" },
  { code: "ASSET_CONCENTRATION_TOP20_BPS", label: "상위 20% 자산 집중도" },
  { code: "CONSUMPTION_RATE_BPS", label: "소비율" },
  { code: "PRODUCT_SELL_THROUGH_RATE_BPS", label: "상품 판매율" },
  { code: "PRODUCT_STOCKOUT_SHARE_BPS", label: "상품 품절 비율" },
  { code: "FINANCIAL_PARTICIPATION_RATE_BPS", label: "금융상품 참여율" },
  { code: "PAYMENT_MISS_RATE_BPS", label: "납부 미이행률" },
  { code: "INVESTMENT_PARTICIPATION_RATE_BPS", label: "투자 참여율" },
  { code: "INVESTMENT_MEDIAN_RETURN_BPS", label: "투자 수익률 중앙값" },
  { code: "INVESTMENT_LOSS_PARTICIPANT_RATE_BPS", label: "투자 손실 참여자 비율" },
  { code: "REWARD_ISSUANCE_SHARE_BPS", label: "보상 발행 비중" }
];

const DEFAULT_METRICS = new Set<BalancingMetricCode>(METRICS.slice(0, 8).map((item) => item.code));

const SEVERITY_LABEL = {
  NORMAL: "정상",
  OBSERVE: "관찰",
  WARN: "경고"
} as const;

function dateInput(daysAgo: number) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() - daysAgo);
  return date.toISOString().slice(0, 10);
}

function periodInstant(date: string, end: boolean) {
  const selected = new Date(`${date}T${end ? "23:59:59.999" : "00:00:00.000"}Z`);
  if (end && selected.getTime() > Date.now()) return new Date().toISOString();
  return selected.toISOString();
}

function balancingError(error: unknown) {
  if (!(error instanceof ApiError)) return "경제 분석을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.";
  if (error.code === "INSUFFICIENT_ANALYSIS_DATA") return "분석할 학생 수나 거래가 부족해요.";
  if (error.code === "ANALYSIS_ALREADY_RUNNING") return "같은 구간의 분석이 이미 진행 중이에요.";
  if (error.code === "STALE_PROPOSAL") return "제안 생성 이후 대상 값이 바뀌었어요. 새 분석을 실행해 주세요.";
  if (error.code === "PROPOSAL_EXPIRED") return "제안 유효기간이 지났어요. 새 분석을 실행해 주세요.";
  if (error.code === "AI_DISABLED") return "학급 AI가 중지되어 있어요.";
  if (error.status === 403) return "이 작업을 수행할 권한이 없어요.";
  return error.message;
}

function rangeFor(proposal: BalancingProposal, actionId: string) {
  return proposal.allowed_ranges.find((range) => range.action_id === actionId);
}

export function MonitoringBalancingPanel({
  classroomId,
  enabled,
  canManage,
  onReload
}: Readonly<{
  classroomId: string;
  enabled: boolean;
  canManage: boolean;
  onReload: () => Promise<void>;
}>) {
  const [fromDate, setFromDate] = useState(() => dateInput(7));
  const [toDate, setToDate] = useState(() => dateInput(0));
  const [selectedMetrics, setSelectedMetrics] = useState<Set<BalancingMetricCode>>(DEFAULT_METRICS);
  const [analysis, setAnalysis] = useState<OperationalAnalysisResponse | null>(null);
  const [history, setHistory] = useState<AiRunHistoryItem[]>([]);
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [decidingId, setDecidingId] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const loadHistory = useCallback(async () => {
    setLoading(true);
    try {
      const response = await apiRequest<{ items: AiRunHistoryItem[] }>(
        `/classrooms/${classroomId}/ai/runs-and-proposals?limit=50`
      );
      setHistory(response.items.filter((item) => item.feature_type === "BALANCING"));
      setError("");
    } catch (caught) {
      setError(balancingError(caught));
    } finally {
      setLoading(false);
    }
  }, [classroomId]);

  useEffect(() => {
    setAnalysis(null);
    setEdits({});
    setMessage("");
    setError("");
    void loadHistory();
  }, [classroomId, loadHistory]);

  const proposals = useMemo(() => history
    .flatMap((run) => run.proposals.map((proposal) => ({ ...proposal, run })))
    .filter((proposal) => proposal.proposal_type === "BALANCING_RECOMMENDATION"), [history]);

  function toggleMetric(code: BalancingMetricCode) {
    setSelectedMetrics((current) => {
      const next = new Set(current);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  }

  async function requestAnalysis() {
    if (!enabled || analyzing) return;
    if (!fromDate || !toDate || fromDate > toDate) {
      setError("올바른 분석 시작일과 종료일을 선택해 주세요.");
      return;
    }
    if (selectedMetrics.size === 0) {
      setError("분석할 지표를 하나 이상 선택해 주세요.");
      return;
    }
    setAnalyzing(true);
    setMessage("");
    setError("");
    try {
      const response = await apiRequest<OperationalAnalysisResponse>(
        `/classrooms/${classroomId}/ai/balancing-analysis-operations`,
        {
          method: "POST",
          body: JSON.stringify({
            analysis_mode: "ON_DEMAND",
            period: {
              from: periodInstant(fromDate, false),
              to: periodInstant(toDate, true)
            },
            metric_scopes: Array.from(selectedMetrics)
          })
        }
      );
      setAnalysis(response);
      await loadHistory();
      setMessage(response.proposal_id
        ? "분석이 끝났어요. 아래 조정 제안을 검토해 주세요."
        : "분석이 끝났어요. 현재 적용할 조정 제안은 없습니다.");
    } catch (caught) {
      setError(balancingError(caught));
    } finally {
      setAnalyzing(false);
    }
  }

  async function decide(proposal: BalancingProposal, command: "APPLY" | "REJECT" | "ACKNOWLEDGE") {
    setDecidingId(proposal.proposal_id);
    setMessage("");
    setError("");
    try {
      await apiRequest<AiProposalCommandResponse>(
        `/classrooms/${classroomId}/ai/balancing-proposals/${proposal.proposal_id}/commands`,
        {
          method: "POST",
          body: JSON.stringify({
            command,
            edited_values: command === "APPLY" ? proposal.proposed_values.map((value) => ({
              action_id: value.action_id,
              proposed_value: Number(edits[`${proposal.proposal_id}:${value.action_id}`] ?? value.proposed_value)
            })) : [],
            reason: command === "APPLY"
              ? "운영 지표, 영향과 허용 범위를 확인하고 밸런싱 제안을 적용함"
              : command === "REJECT"
                ? "운영 상황을 확인한 결과 이번 밸런싱 제안을 적용하지 않음"
                : "자동 모니터링 알림과 근거를 확인함"
          })
        }
      );
      if (command === "APPLY") await onReload();
      await loadHistory();
      setMessage(command === "APPLY" ? "조정안을 적용했어요." : command === "REJECT" ? "제안을 거절했어요." : "알림을 확인 처리했어요.");
    } catch (caught) {
      setError(balancingError(caught));
      if (caught instanceof ApiError && ["STALE_PROPOSAL", "PROPOSAL_EXPIRED"].includes(caught.code)) {
        await loadHistory();
      }
    } finally {
      setDecidingId("");
    }
  }

  return (
    <section className="plain-section monitoring-panel">
      <div className="section-title-line">
        <div>
          <span className="section-kicker">AI 경제 운영</span>
          <h2>자동 모니터링·밸런싱</h2>
          <p>7일마다 운영 지표를 자동 점검하고, 설정은 교사가 승인한 경우에만 바뀝니다.</p>
        </div>
        <span className={`monitor-state ${enabled ? "on" : "off"}`}>
          <Bell size={15} /> {enabled ? "자동 점검 사용 중" : "자동 점검 중지"}
        </span>
      </div>

      {!enabled ? <div className="chat-disabled"><AlertTriangle size={18} /> 학급 AI가 중지되어 수동 분석과 자동 모니터링을 실행하지 않아요.</div> : null}

      <div className="monitor-analysis-form">
        <div className="monitor-period">
          <label>분석 시작일<input type="date" value={fromDate} disabled={!enabled || analyzing} onChange={(event) => setFromDate(event.target.value)} /></label>
          <label>분석 종료일<input type="date" value={toDate} disabled={!enabled || analyzing} onChange={(event) => setToDate(event.target.value)} /></label>
        </div>
        <fieldset disabled={!enabled || analyzing}>
          <legend>분석 지표</legend>
          <div className="monitor-metric-picker">
            {METRICS.map((metric) => (
              <label key={metric.code}>
                <input type="checkbox" checked={selectedMetrics.has(metric.code)} onChange={() => toggleMetric(metric.code)} />
                {metric.label}
              </label>
            ))}
          </div>
        </fieldset>
        <button type="button" className="wide-action" disabled={!enabled || analyzing || selectedMetrics.size === 0} onClick={() => void requestAnalysis()}>
          {analyzing ? <LoaderCircle className="spin" size={17} /> : <SlidersHorizontal size={17} />}
          {analyzing ? "운영 지표 분석 중" : "지금 분석하기"}
        </button>
      </div>

      {analysis ? (
        <div className="monitor-result">
          <header>
            <span className={`monitor-severity ${analysis.analysis_severity.toLowerCase()}`}>{SEVERITY_LABEL[analysis.analysis_severity]}</span>
            <strong>학생 {analysis.sample.active_students}명 · 관련 거래 {analysis.sample.related_transactions}건</strong>
          </header>
          <p>{analysis.summary}</p>
          {analysis.sample.status === "INSUFFICIENT" ? <div className="inline-notice">학생 5명과 관련 거래 10건이 모인 뒤 조정안을 생성합니다.</div> : null}
          <div className="monitor-metric-results">
            {analysis.metrics.map((metric) => (
              <div className={metric.state.toLowerCase()} key={metric.code}>
                <span>{METRICS.find((item) => item.code === metric.code)?.label ?? metric.subject_label}</span>
                <strong>{metric.value == null ? "측정 불가" : valueForDisplay(metric.value, metric.unit)}</strong>
                <small>{metric.state} · {metric.direction}</small>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      <div className="monitor-proposal-heading">
        <div><h3>모니터링 알림·조정 제안</h3><p>자동 알림은 확인만으로 정책을 바꾸지 않습니다.</p></div>
        <button type="button" className="ghost-action" disabled={loading} onClick={() => void loadHistory()}><RefreshCw size={15} /> 새로고침</button>
      </div>
      {message ? <div className="inline-notice" role="status"><Check size={17} /> {message}</div> : null}
      {error ? <div className="inline-error" role="alert">{error}</div> : null}
      {loading ? <div className="chat-loading"><LoaderCircle className="spin" size={18} /> 모니터링 이력을 불러오는 중이에요.</div> : null}
      {!loading && proposals.length === 0 ? <p className="empty-copy">아직 검토할 모니터링 알림이나 조정 제안이 없어요.</p> : null}

      <div className="monitor-proposal-list">
        {proposals.slice(0, 12).map((proposal) => {
          const actionable = ["AWAITING_TEACHER", "ACKNOWLEDGED"].includes(proposal.status);
          const invalidEdit = proposal.proposed_values.some((value) => {
            const range = rangeFor(proposal, value.action_id);
            const edited = Number(edits[`${proposal.proposal_id}:${value.action_id}`] ?? value.proposed_value);
            return !Number.isInteger(edited) || !range || edited < range.allowed_min_value || edited > range.allowed_max_value;
          });
          return (
            <article className={`monitor-proposal-card ${proposal.source_type.toLowerCase()}`} key={proposal.proposal_id}>
              <header>
                <div>
                  <span className={`monitor-severity ${(proposal.severity ?? "NORMAL").toLowerCase()}`}>{SEVERITY_LABEL[proposal.severity ?? "NORMAL"]}</span>
                  <b>{proposal.source_type === "AUTO_MONITOR" ? "자동 모니터링" : "교사 요청 분석"}</b>
                </div>
                <span>{proposal.decision_status ?? proposal.status}</span>
              </header>
              <p className="monitor-summary">{proposal.summary}</p>
              <div className="monitor-proposed-values">
                {proposal.proposed_values.map((value) => {
                  const range = rangeFor(proposal, value.action_id);
                  const editKey = `${proposal.proposal_id}:${value.action_id}`;
                  return (
                    <div key={value.action_id}>
                      <label>
                        <span>{value.label}</span>
                        <input
                          type="number"
                          value={edits[editKey] ?? value.proposed_value}
                          disabled={!canManage || !actionable}
                          onChange={(event) => setEdits((current) => ({ ...current, [editKey]: event.target.value }))}
                        />
                      </label>
                      <small>
                        현재 {valueForDisplay(value.current_value ?? 0, value.unit)} · 허용 {range ? `${valueForDisplay(range.allowed_min_value, value.unit)} ~ ${valueForDisplay(range.allowed_max_value, value.unit)}` : "확인 불가"}
                      </small>
                      {value.rationale ? <p><strong>근거</strong> {value.rationale}</p> : null}
                      {value.expected_effect ? <p><strong>예상 효과</strong> {value.expected_effect}</p> : null}
                      {value.side_effects.length ? <p><strong>주의</strong> {value.side_effects.join(" · ")}</p> : null}
                    </div>
                  );
                })}
              </div>
              <footer>
                <small>{proposal.run.analysis_period_from && proposal.run.data_as_of
                  ? `${new Date(proposal.run.analysis_period_from).toLocaleDateString("ko-KR")} ~ ${new Date(proposal.run.data_as_of).toLocaleDateString("ko-KR")}`
                  : new Date(proposal.created_at).toLocaleString("ko-KR")}</small>
                {proposal.source_type === "AUTO_MONITOR" && proposal.status === "AWAITING_TEACHER" ? (
                  <button type="button" disabled={decidingId === proposal.proposal_id} onClick={() => void decide(proposal, "ACKNOWLEDGE")}>알림 확인</button>
                ) : null}
                {canManage && actionable ? <button type="button" className="reject" disabled={decidingId === proposal.proposal_id} onClick={() => void decide(proposal, "REJECT")}>거절</button> : null}
                {canManage && actionable ? <button type="button" className="apply" disabled={decidingId === proposal.proposal_id || invalidEdit} onClick={() => void decide(proposal, "APPLY")}>검토 후 적용</button> : null}
              </footer>
            </article>
          );
        })}
      </div>
    </section>
  );
}
