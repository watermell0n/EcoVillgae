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
  kind?: "PHYSICAL" | "DIGITAL";
  vatRateBps?: number;
};

type RecommendationKind = "product" | "quiz";

type AiHistoryItem = {
  ai_run_id: string;
  requested_at: string;
  fallback_used: boolean;
  proposals: Array<{
    proposal_id: string;
    context_type: string;
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

export function DraftRecommendationPanel({
  classroomId,
  products,
  policyVersions,
  quizSessionId,
  activeStudentCount,
  effectiveTaxBps,
  onReload,
  onStateVersionChange,
  onProductPriceApplied
}: Readonly<{
  classroomId: string;
  products: ProductDraft[];
  policyVersions: readonly AiPolicyVersionRow[];
  quizSessionId: string;
  activeStudentCount: number;
  effectiveTaxBps: number;
  onReload: () => Promise<void>;
  onStateVersionChange: (version: number) => void;
  onProductPriceApplied: (productId: string, nextPrice: number) => void;
}>) {
  const serverProducts = useMemo(() => products.filter((product) => product.resourceId), [products]);
  const [kind, setKind] = useState<RecommendationKind>("product");
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
    if (!productId && serverProducts[0]?.resourceId) setProductId(serverProducts[0].resourceId);
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

  function resetProposal(nextKind?: RecommendationKind) {
    if (nextKind) setKind(nextKind);
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
      setMessage(response.proposal_id ? "추천값을 확인한 뒤 적용해 주세요." : "적용 가능한 제안이 생성되지 않았어요.");
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
        if (priceValue) onProductPriceApplied(selectedProduct.resourceId, priceValue.proposed_value);
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
    <section className="plain-section">
      <div className="section-title-line">
        <div><span className="section-kicker">상품·퀴즈</span><h2>AI 추천 검토</h2></div>
      </div>
      <div className="recommendation-tabs" role="tablist" aria-label="추천 종류">
        <button type="button" className={kind === "product" ? "active" : ""} onClick={() => resetProposal("product")}>상품 가격</button>
        <button type="button" className={kind === "quiz" ? "active" : ""} onClick={() => resetProposal("quiz")}>GAME 보상</button>
      </div>

      {kind === "product" ? (
        <div className="recommendation-form">
          {serverProducts.length ? <>
            <label>등록 상품<select value={productId} onChange={(event) => { setProductId(event.target.value); resetProposal(); }}>
              {serverProducts.map((product) => <option value={product.resourceId} key={product.resourceId}>{product.name} · 현재 {product.price}비빔</option>)}
            </select></label>
            <label>예상 구매 학생 수<input type="number" min="1" value={expectedDemand} onChange={(event) => setExpectedDemand(event.target.value)} /></label>
            <label>학생 1인당 예상 구매 횟수<input type="number" min="0.1" step="0.1" value={purchaseFrequency} onChange={(event) => setPurchaseFrequency(event.target.value)} /></label>
          </> : <p className="empty-copy">상점에서 상품을 먼저 등록하면 가격 추천을 받을 수 있어요.</p>}
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
        <SetupPrecheckSummary result={result} />
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

      <div className="recommendation-history">
        <h3>최근 추천·적용 이력</h3>
        {historyError ? <p className="empty-copy">{historyError}</p> : null}
        {!historyError && history.flatMap((item) => item.proposals).length === 0 ? <p className="empty-copy">아직 추천 이력이 없어요.</p> : null}
        {history.flatMap((item) => item.proposals.map((proposal) => ({ ...proposal, requestedAt: item.requested_at, fallbackUsed: item.fallback_used }))).slice(0, 6).map((proposal) => (
          <div className="recommendation-history-row" key={proposal.proposal_id}>
            <div>
              <strong>{proposal.context_type === "PRODUCT_DRAFT" ? "상품 가격" : "GAME 보상"}</strong>
              <span>{proposal.summary || "추천 검토"}</span>
            </div>
            <div>
              {proposal.fallbackUsed ? <small>규칙 엔진 결과</small> : null}
              <b>{proposal.decision_status || proposal.status}</b>
              <time>{new Date(proposal.requestedAt).toLocaleString("ko-KR")}</time>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
