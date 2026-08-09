"use client";

import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Check } from "lucide-react";
import {
  ApiError,
  apiRequest,
  inputTextToValue,
  unitHint,
  unitSuffix,
  valueForDisplay,
  valueToInputText,
  type AiProposalCommandResponse,
  type SetupPrecheckResponse
} from "../api";
import { policyVersionOf, type AiPolicyVersionRow } from "../types";
import { SetupPrecheckSummary } from "./ModuleActivationPanel";

type ProductDraft = {
  name: string;
  price: string;
  stock: string;
  resourceId?: string;
  // 서버가 준 종류를 그대로 되돌려 보내야 합니다. 캐릭터 상품을 PHYSICAL로 바꿔 보내면
  // 서버가 PRODUCT_TYPE_CONFLICT로 거절해 가격 추천이 기존 상품에 적용되지 않습니다.
  kind?: "PHYSICAL" | "DIGITAL" | "CHARACTER";
  vatRateBps?: number;
  status?: string;
};

type RecommendationKind = "product" | "quiz";

type AiHistoryItem = {
  ai_run_id: string;
  requested_at: string;
  fallback_used: boolean;
  proposals: Array<{
    proposal_id: string;
    // 운영 조정(BALANCING_RECOMMENDATION) 제안에는 규칙 문맥이 없어 null입니다.
    context_type: string | null;
    target_id: string;
    status: string;
    decision_status: string | null;
    summary: string;
    proposed_values: Array<{ action_id: string; proposed_value: number; label: string }>;
  }>;
};

function historyErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 403) return "추천 이력을 볼 권한이 없어요.";
  if (error instanceof ApiError) return error.message;
  return "추천 이력을 불러오지 못했어요. 서버 연결을 확인해 주세요.";
}

// 이력에는 모듈 활성화·시작 전 점검 제안도 함께 내려옵니다.
// 이전에는 상품이 아니면 전부 "GAME 보상"으로 표시돼 잘못 읽혔습니다.
const CONTEXT_LABELS: Record<string, string> = {
  MODULE_ACTIVATION: "모듈 초기 정책",
  PRODUCT_DRAFT: "상품 가격",
  QUIZ_SESSION_DRAFT: "GAME 보상",
  CLASSROOM_SETUP: "시작 전 점검"
};

const DECISION_LABELS: Record<string, string> = {
  AWAITING_TEACHER: "검토 대기",
  ACKNOWLEDGED: "확인함",
  APPROVED: "승인함",
  APPLIED: "적용됨",
  REJECTED: "거절함",
  APPLY_FAILED: "적용 실패"
};

function contextLabel(contextType: string | null) {
  if (!contextType) return "운영 조정";
  return CONTEXT_LABELS[contextType] ?? contextType;
}

function decisionLabel(value: string) {
  return DECISION_LABELS[value] ?? value;
}

// "2026. 8. 5. 오후 3:24:11"은 좁은 칸에서 줄바꿈이 심해 "08-05 15:24"로 줄입니다.
function shortDateTime(value: string) {
  const at = new Date(value);
  if (Number.isNaN(at.getTime())) return value;
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${pad(at.getMonth() + 1)}-${pad(at.getDate())} ${pad(at.getHours())}:${pad(at.getMinutes())}`;
}

// kind는 화면에서 바꾸지 않고 배치하는 쪽이 정합니다.
// 상품 가격 추천은 '상점 상품' 탭, GAME 보상 추천은 'AI 설정' 탭에 놓습니다.
export function DraftRecommendationPanel({
  kind,
  classroomId,
  products = [],
  policyVersions,
  quizSessionId = "",
  activeStudentCount,
  effectiveTaxBps,
  currencyName = "",
  onReload,
  onStateVersionChange,
  onProductPriceApplied
}: Readonly<{
  kind: RecommendationKind;
  classroomId: string;
  products?: readonly ProductDraft[];
  policyVersions: readonly AiPolicyVersionRow[];
  /** 학급이 정한 화폐 이름입니다. 금액 뒤에 붙여 보여 줍니다. */
  currencyName?: string;
  quizSessionId?: string;
  activeStudentCount: number;
  effectiveTaxBps: number;
  onReload: () => Promise<void>;
  onStateVersionChange: (version: number) => void;
  onProductPriceApplied?: (productId: string, nextPrice: number) => void;
}>) {
  const serverProducts = useMemo(
    () => products.filter((product) => product.resourceId && product.status !== "PUBLISHED"),
    [products]
  );
  const [productId, setProductId] = useState("");
  const [sessionId, setSessionId] = useState(quizSessionId);
  const [expectedDemand, setExpectedDemand] = useState(String(Math.max(activeStudentCount, 1)));
  const [purchaseFrequency, setPurchaseFrequency] = useState("1");
  const [questionCount, setQuestionCount] = useState("5");
  const [expectedRuns, setExpectedRuns] = useState("1");
  const [currentReward, setCurrentReward] = useState("0");
  const [result, setResult] = useState<SetupPrecheckResponse | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [warnConfirmed, setWarnConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [history, setHistory] = useState<AiHistoryItem[]>([]);
  const [historyError, setHistoryError] = useState("");

  useEffect(() => setSessionId(quizSessionId), [quizSessionId]);
  useEffect(() => {
    if (!serverProducts.some((product) => product.resourceId === productId)) {
      setProductId(serverProducts[0]?.resourceId ?? "");
      resetProposal();
    }
  }, [productId, serverProducts]);
  useEffect(() => {
    let cancelled = false;
    async function restoreHistory() {
      try {
        const response = await apiRequest<{ items: AiHistoryItem[] }>(
          `/classrooms/${classroomId}/ai/runs-and-proposals?limit=20`
        );
        if (!cancelled) {
          setHistory(response.items);
          setHistoryError("");
        }
      } catch (error) {
        if (!cancelled) setHistoryError(historyErrorMessage(error));
      }
    }
    void restoreHistory();
    return () => { cancelled = true; };
  }, [classroomId]);

  // 이력 API는 학급의 모든 제안을 내려줍니다. 두 패널이 같은 줄을 중복으로 보여 주지 않도록
  // 상품 패널은 상품 제안만, 그 밖의 제안(GAME 보상·모듈 초기 정책·운영 조정)은 AI 패널이 맡습니다.
  const historyRows = useMemo(() => history
    .flatMap((item) => item.proposals.map((proposal) => ({
      ...proposal,
      requestedAt: item.requested_at,
      fallbackUsed: item.fallback_used
    })))
    .filter((proposal) => (kind === "product"
      ? proposal.context_type === "PRODUCT_DRAFT"
      : proposal.context_type !== "PRODUCT_DRAFT"))
    .slice(0, 6), [history, kind]);

  const selectedProduct = serverProducts.find((product) => product.resourceId === productId);
  // 입력칸의 글자는 금리·세율이면 %입니다. 검사와 전송은 서버와 같은 정수 값으로 환산해서 합니다.
  const candidateValue = (candidate: SetupPrecheckResponse["candidates"][number]) => (
    inputTextToValue(values[candidate.action_id] ?? "", candidate.unit)
  );
  const hasInvalidValue = result?.candidates.some((candidate) => {
    const value = candidateValue(candidate);
    return value === null
      || value < candidate.allowed_min_value
      || value > candidate.allowed_max_value;
  }) ?? true;

  function resetProposal() {
    setResult(null);
    setValues({});
    setWarnConfirmed(false);
    setMessage("");
  }

  async function requestRecommendation() {
    if (kind === "product" && !selectedProduct?.resourceId) {
      setMessage("먼저 상점에서 상품을 서버에 등록해 주세요.");
      return;
    }
    if (kind === "quiz" && !sessionId.trim()) {
      setMessage("열려 있는 GAME 퀴즈 세션 ID가 필요해요.");
      return;
    }
    setBusy(true);
    setMessage("");
    try {
      const triggerContext = kind === "product" ? {
        context_type: "PRODUCT_DRAFT",
        active_student_count: Math.max(activeStudentCount, 1),
        system_amount_cap: 1_000_000,
        effective_tax_bps: effectiveTaxBps,
        product_draft: {
          target_id: selectedProduct!.resourceId,
          kind: "NORMAL",
          stock: Number(selectedProduct!.stock),
          expected_demand: Number(expectedDemand),
          expected_purchase_frequency: Number(purchaseFrequency),
          vat_bps: selectedProduct!.vatRateBps ?? 0,
          free: Number(selectedProduct!.price) === 0,
          confirmed_price: Number(selectedProduct!.price)
        }
      } : {
        context_type: "QUIZ_SESSION_DRAFT",
        active_student_count: Math.max(activeStudentCount, 1),
        non_reward_issuance: 1_000_000,
        existing_reward_issuance: 0,
        effective_tax_bps: effectiveTaxBps,
        quiz_draft: {
          target_id: sessionId.trim(),
          mode: "GAME",
          category: "ECONOMY",
          question_count: Number(questionCount),
          expected_participants: Math.max(activeStudentCount, 1),
          expected_runs: Number(expectedRuns),
          confirmed_reward_per_person: Number(currentReward)
        }
      };
      const response = await apiRequest<SetupPrecheckResponse>(
        `/classrooms/${classroomId}/ai/balancing-analysis-operations`,
        {
          method: "POST",
          body: JSON.stringify({
            analysis_mode: "SETUP_PRECHECK",
            trigger_context: triggerContext,
            metric_scopes: []
          })
        }
      );
      setResult(response);
      setValues(Object.fromEntries(response.candidates.map((candidate) => [
        candidate.action_id,
        valueToInputText(candidate.proposed_value, candidate.unit)
      ])));
      setWarnConfirmed(false);
      // 상품 가격은 추천 결과 카드가 바로 아래 뜨므로 같은 말을 반복하는 안내를 띄우지 않습니다.
      setMessage(response.proposal_id
        ? (kind === "product" ? "" : "추천값을 확인한 뒤 적용해 주세요.")
        : "적용 가능한 제안이 생성되지 않았어요.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "추천을 불러오지 못했어요.");
    } finally {
      setBusy(false);
    }
  }

  async function applyRecommendation() {
    if (!result?.proposal_id || result.overall_severity === "BLOCK" || hasInvalidValue) return;
    setBusy(true);
    setMessage("");
    try {
      const response = await apiRequest<AiProposalCommandResponse>(
        `/classrooms/${classroomId}/ai/balancing-proposals/${result.proposal_id}/commands`,
        {
          method: "POST",
          body: JSON.stringify({
            command: "APPLY",
            edited_values: result.candidates.map((candidate) => ({
              action_id: candidate.action_id,
              // 화면 표기 단위를 서버 단위로 되돌립니다. hasInvalidValue가 null을 미리 걸러 냅니다.
              proposed_value: candidateValue(candidate)
            })),
            reason: kind === "product" ? "교사가 상품 가격 추천을 검토하고 적용함" : "교사가 GAME 퀴즈 보상을 검토하고 적용함",
            // 서버는 제안 생성 시점의 정책 영역 버전과 일치해야 적용합니다. null은 항상 거절됩니다.
            expected_policy_version: policyVersionOf(policyVersions, kind === "product" ? "STORE" : "QUIZ_REWARD")
          })
        }
      );
      onStateVersionChange(response.state_version);
      if (kind === "product" && selectedProduct?.resourceId) {
        const priceValue = response.applied_values.find((value) => value.action_id.toLowerCase().includes("price"))
          ?? response.applied_values[0];
        if (priceValue) onProductPriceApplied?.(selectedProduct.resourceId, priceValue.proposed_value);
      }
      // 어떤 값이 확정됐는지 서버 응답(applied_values)을 그대로 읽어 보여줍니다.
      const applied = response.applied_values
        .map((value) => {
          const candidate = result.candidates.find((item) => item.action_id === value.action_id);
          return `${candidate?.label ?? value.action_id} ${valueForDisplay(value.proposed_value, value.unit)}`;
        })
        .join(" · ");
      setResult(null);
      setValues({});
      setWarnConfirmed(false);
      setMessage(applied ? `추천값을 적용했어요. (${applied})` : "추천값을 적용했어요.");
      try {
        const refreshed = await apiRequest<{ items: AiHistoryItem[] }>(
          `/classrooms/${classroomId}/ai/runs-and-proposals?limit=20`
        );
        setHistory(refreshed.items);
        setHistoryError("");
      } catch (historyLoadError) {
        setHistoryError(historyErrorMessage(historyLoadError));
      }
    } catch (error) {
      if (error instanceof ApiError && ["STATE_VERSION_CONFLICT", "POLICY_VERSION_CONFLICT"].includes(error.code)) {
        await onReload();
        setResult(null);
        setValues({});
        setWarnConfirmed(false);
        setMessage("다른 화면에서 값이 변경되어 최신 상태를 불러왔어요. 다시 점검해 주세요.");
      } else {
        setMessage(error instanceof Error ? error.message : "추천값을 적용하지 못했어요.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="plain-section recommendation-panel">
      <div className="section-title-line">
        <div>
          <span className="section-kicker">{kind === "product" ? "상점 상품" : "게임형 퀴즈"}</span>
          <h2>{kind === "product" ? "AI 가격 추천 검토" : "AI 보상 추천 검토"}</h2>
          <p>{kind === "product"
            ? "비공개 상품의 참조 급여와 재고·예상 수요를 기준으로 세전 가격을 추천합니다. 공개 중인 상품은 먼저 공개를 중지해야 합니다."
            : "GAME 퀴즈의 1인 참여 보상을 추천합니다. 교사가 검토한 값만 세션에 적용됩니다."}</p>
        </div>
      </div>

      {kind === "product" ? (
        <div className="recommendation-form">
          {serverProducts.length ? <>
            <label>등록 상품<select value={productId} onChange={(event) => { setProductId(event.target.value); resetProposal(); }}>
              {serverProducts.map((product) => <option value={product.resourceId} key={product.resourceId}>{product.name} · 현재 {product.price}{currencyName}</option>)}
            </select></label>
            <label>예상 구매 학생 수<input type="number" min="1" value={expectedDemand} onChange={(event) => setExpectedDemand(event.target.value)} /></label>
            <label>학생 1인당 예상 구매 횟수<input type="number" min="0.1" step="0.1" value={purchaseFrequency} onChange={(event) => setPurchaseFrequency(event.target.value)} /></label>
          </> : <p className="empty-copy">추천할 비공개 상품이 없어요. 상품을 등록하거나 공개 중인 상품을 먼저 내려 주세요.</p>}
        </div>
      ) : (
        <div className="recommendation-form">
          <p className="section-description">GAME 퀴즈만 보상을 설정합니다. TEST 퀴즈에는 보상이 없어요.</p>
          <label>GAME 세션 ID<input value={sessionId} onChange={(event) => setSessionId(event.target.value)} placeholder="열려 있는 퀴즈 세션 ID" /></label>
          <label>문항 수<input type="number" min="1" value={questionCount} onChange={(event) => setQuestionCount(event.target.value)} /></label>
          <label>예상 진행 횟수<input type="number" min="1" value={expectedRuns} onChange={(event) => setExpectedRuns(event.target.value)} /></label>
          <label>현재 1인 보상<input type="number" min="0" value={currentReward} onChange={(event) => setCurrentReward(event.target.value)} /></label>
        </div>
      )}

      <button type="button" className="wide-action" disabled={busy || (kind === "product" ? !selectedProduct : !sessionId.trim())} onClick={() => void requestRecommendation()}>
        {busy ? "확인하는 중" : result ? "추천 다시 확인" : "추천 점검"}
      </button>

      {result ? <div className="recommendation-result">
        <SetupPrecheckSummary result={result} compact={kind === "product"} />
        {result.candidates.map((candidate) => {
          const value = candidateValue(candidate);
          const outsideAllowed = value === null
            || value < candidate.allowed_min_value
            || value > candidate.allowed_max_value;
          const suffix = unitSuffix(candidate.unit);
          const hint = unitHint(value, candidate.unit);
          return <label className={outsideAllowed ? "value-blocked" : ""} key={candidate.action_id}>
            {candidate.label}{suffix ? ` (${suffix})` : ""}
            <span className={suffix ? "value-input with-unit" : "value-input"}>
              <input
                type="number"
                step={candidate.unit === "RATE_BPS" ? "0.5" : "1"}
                value={values[candidate.action_id] ?? ""}
                onChange={(event) => setValues((current) => ({ ...current, [candidate.action_id]: event.target.value }))}
              />
              {suffix ? <b aria-hidden="true">{suffix}</b> : null}
            </span>
            <small>추천 {valueForDisplay(candidate.recommended_min_value, candidate.unit)} ~ {valueForDisplay(candidate.recommended_max_value, candidate.unit)} · 허용 {valueForDisplay(candidate.allowed_min_value, candidate.unit)} ~ {valueForDisplay(candidate.allowed_max_value, candidate.unit)}</small>
            {hint ? <small>{hint}</small> : null}
            {outsideAllowed ? <em>허용 범위 밖의 값은 적용할 수 없어요.</em> : null}
          </label>;
        })}
        {result.overall_severity === "WARN" ? <label className="warn-confirm"><input type="checkbox" checked={warnConfirmed} onChange={(event) => setWarnConfirmed(event.target.checked)} /><AlertTriangle size={16} /> 경고 내용을 확인했고 이 값으로 적용합니다.</label> : null}
        <button type="button" className="wide-action" disabled={busy || !result.proposal_id || result.overall_severity === "BLOCK" || hasInvalidValue || (result.overall_severity === "WARN" && !warnConfirmed)} onClick={() => void applyRecommendation()}>
          <Check size={16} /> 검토 완료·적용
        </button>
      </div> : null}
      {message ? <div className="inline-notice" role="status">{message}</div> : null}

      {/* 상점 화면은 추천·적용 결과가 상품 목록에 바로 반영되므로 이력 표를 겹쳐 보여 주지 않습니다. */}
      {kind === "product" ? null : <div className="recommendation-history">
        <h3>최근 추천·적용 이력</h3>
        {historyError ? <p className="empty-copy">{historyError}</p> : null}
        {!historyError && historyRows.length === 0 ? (
          <p className="empty-copy">아직 추천 이력이 없어요.</p>
        ) : null}
        {historyRows.length > 0 ? (
          <div className="recommendation-history-scroll">
            <div className="recommendation-history-table">
              <div className="recommendation-history-head">
                <span>종류</span><span>요약</span><span>결과</span><span>시각</span>
              </div>
              {historyRows.map((proposal) => (
                <div className="recommendation-history-row" key={proposal.proposal_id}>
                  <strong>{contextLabel(proposal.context_type)}</strong>
                  <p>{proposal.summary || "추천 검토"}</p>
                  <span className="history-result">
                    <b>{decisionLabel(proposal.decision_status || proposal.status)}</b>
                    {proposal.fallbackUsed ? <small>규칙 엔진 결과</small> : null}
                  </span>
                  <time dateTime={proposal.requestedAt}>{shortDateTime(proposal.requestedAt)}</time>
                </div>
              ))}
            </div>
          </div>
        ) : null}
      </div>}
    </section>
  );
}
