"use client";

// AI 시작 전 점검·추천 기반 모듈 활성화 UI입니다.
// 소유: AI 담당 — Spring/FastAPI 계약을 교사 화면에 붙이는 코드만 이 폴더에 둡니다.
// page.tsx(교사 프런트 소유)는 AiWorkspace만 배치하고 내부는 건드리지 않습니다.
// 성공 후에는 onReload로 서버 상태를 다시 읽습니다. state_version과 policy_version의
// 기준은 항상 서버이며 화면에서 추측해 갱신하지 않습니다.

import { useState } from "react";
import { AlertTriangle, Check } from "lucide-react";
import {
  ApiError,
  apiRequest,
  inputTextToValue,
  MODULE_PREREQUISITES,
  unitHint,
  unitSuffix,
  valueForDisplay,
  valueToInputText,
  type AiProposalCommandResponse,
  type ModuleActivationResponse,
  type SetupPrecheckResponse
} from "../api";
import {
  appliedPolicyValues,
  modulePolicyArea,
  policyVersionOf,
  type AiModuleRow,
  type AiPolicyVersionRow
} from "../types";
import { PlainSection } from "../ui";

// 서버에서 내려온 모듈 상태 표기입니다. page.tsx가 ACTIVE를 "열림"으로 매핑합니다.
const ACTIVE_STATUS = "열림";

const MODULE_LABELS: Record<string, string> = {
  CHARACTER: "캐릭터",
  CREDIT: "신용",
  DEPOSIT: "예금",
  INCOME_TAX: "세금",
  INVESTMENT: "투자",
  LOAN: "대출",
  PUBLIC_FUND: "공공 예산/투표",
  QUIZ_GAME: "퀴즈",
  QUIZ_TEST: "퀴즈",
  ROLE_SALARY: "월급",
  SAVINGS: "적금",
  STORE: "상점",
  TRADE: "개인 거래"
};

function moduleLabel(module: AiModuleRow) {
  return MODULE_LABELS[module.code ?? ""] ?? module.name;
}

export function SetupPrecheckSummary({ result }: Readonly<{ result: SetupPrecheckResponse }>) {
  return (
    <section className={`ai-precheck-summary ${result.overall_severity.toLowerCase()}`}>
      <div>
        <span>AI 시작 전 점검 · {result.overall_severity}</span>
        <strong>{result.summary}</strong>
      </div>
      {result.fallback_used ? <p>AI 설명 연결에 실패해 Spring 규칙 결과를 그대로 표시했습니다.</p> : null}
      {result.checks.map((check) => (
        <p key={check.check_id}><b>{check.severity}</b> {check.explanation}{check.correction_guidance ? ` · ${check.correction_guidance}` : ""}</p>
      ))}
    </section>
  );
}

export function ModuleActivationPanel({
  classroomId,
  modules,
  policyVersions,
  activeStudentCount,
  startDate,
  canManage,
  onReload,
  onStateVersionChange
}: Readonly<{
  classroomId: string;
  modules: readonly AiModuleRow[];
  policyVersions: readonly AiPolicyVersionRow[];
  activeStudentCount: number;
  startDate: string;
  canManage: boolean;
  onReload: () => Promise<void>;
  onStateVersionChange: (version: number) => void;
}>) {
  const [prechecks, setPrechecks] = useState<Record<string, SetupPrecheckResponse>>({});
  const [editedValues, setEditedValues] = useState<Record<string, Record<string, string>>>({});
  const [investmentModes, setInvestmentModes] = useState<Record<string, "SHORT_TERM" | "LONG_TERM">>({});
  const [busyCode, setBusyCode] = useState("");
  const [message, setMessage] = useState("");

  // 서버에 등록된 모듈만 활성화할 수 있습니다. 개설 전 화면에는 code가 없습니다.
  const serverModules = modules
    .filter((module) => module.code)
    .sort((left, right) => Number(missingPrerequisites(left).length > 0) - Number(missingPrerequisites(right).length > 0));

  function missingPrerequisites(module: AiModuleRow) {
    const code = module.code;
    if (!code) return [];
    const prerequisites = MODULE_PREREQUISITES[code] ?? [];
    return prerequisites.filter((requiredCode) => (
      !modules.some((candidate) => candidate.code === requiredCode && candidate.status === ACTIVE_STATUS)
    ));
  }

  // 입력칸의 글자는 금리·세율이면 %입니다. 허용 구간 검사는 서버와 같은 bps로 환산해서 합니다.
  function candidateValue(
    candidate: SetupPrecheckResponse["candidates"][number],
    values: Record<string, string>
  ) {
    return inputTextToValue(values[candidate.action_id] ?? "", candidate.unit);
  }

  function invalidCandidates(result: SetupPrecheckResponse, values: Record<string, string>) {
    return result.candidates.some((candidate) => {
      const value = candidateValue(candidate, values);
      return value === null
        || value < candidate.allowed_min_value
        || value > candidate.allowed_max_value;
    });
  }

  async function afterApplied(code: string, stateVersion: number) {
    onStateVersionChange(stateVersion);
    setPrechecks((current) => {
      const next = { ...current };
      delete next[code];
      return next;
    });
    await onReload();
  }

  function handleFailure(error: unknown, fallback: string) {
    if (error instanceof ApiError && ["STATE_VERSION_CONFLICT", "POLICY_VERSION_CONFLICT"].includes(error.code)) {
      setPrechecks({});
      void onReload();
      setMessage("다른 화면에서 설정이 변경되어 최신 상태를 불러왔어요. 다시 점검해 주세요.");
      return;
    }
    if (error instanceof ApiError && error.status === 403) {
      setMessage("모듈 활성화는 담임 교사만 실행할 수 있어요.");
      return;
    }
    setMessage(error instanceof Error ? error.message : fallback);
  }

  async function requestPrecheck(module: AiModuleRow) {
    const code = module.code!;
    const prerequisites = missingPrerequisites(module);
    if (prerequisites.length > 0) {
      setMessage(`선행 모듈(${prerequisites.map((item) => MODULE_LABELS[item] ?? item).join(", ")})을 먼저 활성화해 주세요.`);
      return;
    }
    if (!module.resourceId) {
      setMessage("모듈 정보를 아직 불러오지 못했어요. 새로고침한 뒤 다시 시도해 주세요.");
      return;
    }
    setBusyCode(code);
    setMessage("");
    try {
      const salaryPolicy = code === "ROLE_SALARY" ? {
        roles: [{
          role_code: "DEFAULT_ROLE",
          name: "기본 역할",
          weight: 1,
          confirmed_salary: null,
          assigned_student_count: Math.max(activeStudentCount, 1),
          active: true
        }],
        pay_interval_days: 7,
        first_pay_on: startDate,
        default_role_salary: 1000
      } : undefined;
      const response = await apiRequest<SetupPrecheckResponse>(
        `/classrooms/${classroomId}/ai/balancing-analysis-operations`,
        {
          method: "POST",
          body: JSON.stringify({
            analysis_mode: "SETUP_PRECHECK",
            trigger_context: {
              context_type: "MODULE_ACTIVATION",
              active_student_count: Math.max(activeStudentCount, 1),
              module_activation: { module_code: code, target_id: module.resourceId },
              ...(salaryPolicy ? { salary_policy: salaryPolicy } : {})
            },
            metric_scopes: []
          })
        }
      );
      setPrechecks((current) => ({ ...current, [code]: response }));
      setEditedValues((current) => ({
        ...current,
        [code]: Object.fromEntries(response.candidates.map((candidate) => [
          candidate.action_id,
          valueToInputText(candidate.proposed_value, candidate.unit)
        ]))
      }));
      setMessage(response.proposal_id ? "추천값을 확인한 뒤 활성화해 주세요." : "적용 가능한 제안이 생성되지 않았어요.");
    } catch (error) {
      handleFailure(error, "AI 점검을 불러오지 못했어요.");
    } finally {
      setBusyCode("");
    }
  }

  async function activateWithoutPolicy(module: AiModuleRow) {
    const code = module.code!;
    const investmentMode = investmentModes[code]
      ?? (module.config?.investment_mode === "SHORT_TERM" ? "SHORT_TERM" : "LONG_TERM");
    const prerequisites = missingPrerequisites(module);
    if (prerequisites.length > 0) {
      setMessage(`선행 모듈(${prerequisites.map((item) => MODULE_LABELS[item] ?? item).join(", ")})을 먼저 활성화해 주세요.`);
      return;
    }
    setBusyCode(code);
    setMessage("");
    try {
      const response = await apiRequest<ModuleActivationResponse>(
        `/classrooms/${classroomId}/modules/${code}/activation-commands`,
        {
          method: "POST",
          body: JSON.stringify({
            expected_state_version: module.stateVersion ?? 0,
            screen_id: "SCR-WEB-003",
            ui_action_id: "UIA-MOD-001",
            client_occurred_at: new Date().toISOString(),
            confirm_prerequisites: true,
            initial_policy_values: code === "INVESTMENT"
              ? { investment_mode: investmentMode }
              : {},
            recommendation_action_ids: []
          })
        }
      );
      await afterApplied(code, response.state_version);
      setMessage(`${moduleLabel(module)} 모듈을 활성화했어요.`);
    } catch (error) {
      handleFailure(error, "모듈을 활성화하지 못했어요.");
    } finally {
      setBusyCode("");
    }
  }

  async function applyRecommendation(module: AiModuleRow, result: SetupPrecheckResponse) {
    const code = module.code!;
    const values = editedValues[code] ?? {};
    if (!result.proposal_id || result.overall_severity === "BLOCK") return;
    if (invalidCandidates(result, values)) {
      setMessage("허용 구간 밖의 값은 적용할 수 없습니다.");
      return;
    }
    setBusyCode(code);
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
              // 화면은 %, 서버는 정수 bps입니다. invalidCandidates가 null을 미리 걸러 냅니다.
              proposed_value: candidateValue(candidate, values)
            })),
            reason: "교사가 추천값과 허용 구간을 검토하고 초기 정책을 확정함",
            // 서버는 제안 생성 시점의 정책 영역 버전과 일치해야 적용합니다. null은 항상 거절됩니다.
            expected_policy_version: policyVersionOf(policyVersions, modulePolicyArea(code))
          })
        }
      );
      await afterApplied(code, response.state_version);
      // 어떤 값이 확정됐는지 서버 응답(applied_values)을 그대로 읽어 보여줍니다.
      const applied = response.applied_values
        .map((value) => {
          const candidate = result.candidates.find((item) => item.action_id === value.action_id);
          return `${candidate?.label ?? value.action_id} ${valueForDisplay(value.proposed_value, value.unit)}`;
        })
        .join(" · ");
      setMessage(applied
        ? `${module.name} 초기 정책을 확정하고 활성화했어요. (${applied})`
        : `${module.name} 초기 정책을 확정하고 활성화했어요.`);
    } catch (error) {
      handleFailure(error, "추천값을 적용하지 못했어요.");
    } finally {
      setBusyCode("");
    }
  }

  return (
    <PlainSection
      title="선택 모듈 활성화"
      description="개설 때 선택한 모듈만 표시합니다. 투자 모듈은 단기 또는 장기 운영 방식을 고른 뒤 활성화합니다."
    >
      {!canManage ? (
        <div className="inline-notice"><AlertTriangle size={17} /> 모듈 활성화는 학급 소유 교사만 실행할 수 있어요.</div>
      ) : null}
      <div className="module-activation-list">
        {serverModules.length === 0 ? (
          <p className="empty-copy">서버에 등록된 모듈이 없습니다. 학급을 먼저 개설하면 선택한 모듈이 표시됩니다.</p>
        ) : null}
        {serverModules.map((module) => {
          const code = module.code!;
          const result = prechecks[code];
          const values = editedValues[code] ?? {};
          const prerequisites = missingPrerequisites(module);
          const needsPolicy = modulePolicyArea(code) !== null;
          const isActive = module.status === ACTIVE_STATUS;
          const busy = busyCode === code;
          const blocked = !canManage || busy || prerequisites.length > 0;
          const investmentMode = investmentModes[code]
            ?? (module.config?.investment_mode === "SHORT_TERM" ? "SHORT_TERM" : "LONG_TERM");
          const appliedValues = appliedPolicyValues(policyVersions, code);
          return (
            <article className="module-activation-card" key={code}>
              <header>
                <div><strong>{moduleLabel(module)}</strong></div>
                <b className={`module-status ${isActive ? "active" : ""}`}>
                  {isActive ? <Check size={14} /> : null}{module.status}
                </b>
              </header>
              {appliedValues.length > 0 ? (
                <dl className="module-applied-values">
                  {appliedValues.map((applied) => (
                    <div key={applied.label}>
                      <dt>{applied.label}</dt>
                      <dd>{applied.text}</dd>
                    </div>
                  ))}
                </dl>
              ) : null}
              {prerequisites.length > 0 ? <p className="contract-warning">먼저 활성화: {prerequisites.map((item) => MODULE_LABELS[item] ?? item).join(", ")}</p> : null}
              {code === "INVESTMENT" && !isActive ? (
                <label className="investment-mode-selector">
                  투자 운영 방식
                  <select
                    value={investmentMode}
                    disabled={blocked}
                    onChange={(event) => setInvestmentModes((current) => ({
                      ...current,
                      [code]: event.target.value as "SHORT_TERM" | "LONG_TERM"
                    }))}
                  >
                    <option value="SHORT_TERM">단기 투자 · 오늘 투자하고 다음 날 의무 정산</option>
                    <option value="LONG_TERM">장기 투자 · 매일 조정하고 마지막 날 최종 정산</option>
                  </select>
                  <small>
                    {investmentMode === "SHORT_TERM"
                      ? "AI가 다음 날 결과에 맞는 가상 기업 단서 이야기를 만듭니다."
                      : "역사 지수 흐름을 우선 사용하고, 불가능하면 AI 생성 가격 흐름을 사용합니다."}
                  </small>
                </label>
              ) : null}
              {code === "INVESTMENT" && isActive ? (
                <p className="inline-notice">
                  운영 방식: {investmentMode === "SHORT_TERM" ? "단기 투자" : "장기 투자"}
                </p>
              ) : null}
              {!isActive && needsPolicy ? (
                <>
                  <button type="button" disabled={blocked} onClick={() => void requestPrecheck(module)}>
                    {busy ? "점검하는 중" : result ? "추천 다시 확인" : "AI 추천 확인"}
                  </button>
                  <button type="button" disabled={blocked} onClick={() => void activateWithoutPolicy(module)}>
                    {busy ? "활성화하는 중" : "기본값으로 바로 활성화"}
                  </button>
                </>
              ) : null}
              {!isActive && !needsPolicy ? (
                <button type="button" disabled={blocked} onClick={() => void activateWithoutPolicy(module)}>
                  {busy ? "활성화하는 중" : "활성화"}
                </button>
              ) : null}
              {result ? (
                <div className="module-recommendation">
                  <SetupPrecheckSummary result={result} />
                  {result.candidates.map((candidate) => {
                    const value = candidateValue(candidate, values);
                    const outsideRecommended = value !== null
                      && (value < candidate.recommended_min_value || value > candidate.recommended_max_value);
                    const outsideAllowed = value === null
                      || value < candidate.allowed_min_value
                      || value > candidate.allowed_max_value;
                    const suffix = unitSuffix(candidate.unit);
                    const hint = unitHint(value, candidate.unit);
                    return (
                      <label className={outsideAllowed ? "value-blocked" : outsideRecommended ? "value-warning" : ""} key={candidate.action_id}>
                        {candidate.label}{suffix ? ` (${suffix})` : ""}
                        <span className={suffix ? "value-input with-unit" : "value-input"}>
                          <input
                            type="number"
                            step={candidate.unit === "RATE_BPS" ? "0.5" : "1"}
                            value={values[candidate.action_id] ?? ""}
                            onChange={(event) => setEditedValues((current) => ({
                              ...current,
                              [code]: { ...(current[code] ?? {}), [candidate.action_id]: event.target.value }
                            }))}
                          />
                          {suffix ? <b aria-hidden="true">{suffix}</b> : null}
                        </span>
                        <small>
                          추천 {valueForDisplay(candidate.recommended_min_value, candidate.unit)} ~ {valueForDisplay(candidate.recommended_max_value, candidate.unit)} · 허용 {valueForDisplay(candidate.allowed_min_value, candidate.unit)} ~ {valueForDisplay(candidate.allowed_max_value, candidate.unit)}
                        </small>
                        {hint ? <small>{hint}</small> : null}
                        {outsideAllowed ? (
                          <em>{value === null
                            ? (candidate.unit === "RATE_BPS"
                              ? "0.01% 단위의 숫자로 입력해 주세요."
                              : "정수로 입력해 주세요.")
                            : "허용 구간 밖이라 적용할 수 없습니다."}</em>
                        ) : outsideRecommended ? <em>추천 구간 밖입니다. 검토 후 적용할 수 있습니다.</em> : null}
                      </label>
                    );
                  })}
                  <button
                    type="button"
                    disabled={blocked || !result.proposal_id || result.overall_severity === "BLOCK" || invalidCandidates(result, values)}
                    onClick={() => void applyRecommendation(module, result)}
                  >
                    {busy ? "적용하는 중" : "추천값 검토 완료 · 활성화"}
                  </button>
                </div>
              ) : null}
            </article>
          );
        })}
      </div>
      {message ? <div className="inline-notice" role="status">{message}</div> : null}
    </PlainSection>
  );
}
