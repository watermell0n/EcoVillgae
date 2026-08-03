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
  MODULE_PREREQUISITES,
  valueForDisplay,
  type AiProposalCommandResponse,
  type ModuleActivationResponse,
  type ModuleCode,
  type SetupPrecheckResponse
} from "../api";
import {
  modulePolicyArea,
  policyVersionOf,
  type AiModuleRow,
  type AiPolicyVersionRow
} from "../types";
import { PlainSection } from "../ui";

// 서버에서 내려온 모듈 상태 표기입니다. page.tsx가 ACTIVE를 "열림"으로 매핑합니다.
const ACTIVE_STATUS = "열림";

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
  const [busyCode, setBusyCode] = useState("");
  const [message, setMessage] = useState("");

  // 서버에 등록된 모듈만 활성화할 수 있습니다. 개설 전 화면에는 code가 없습니다.
  const serverModules = modules.filter((module) => module.code);

  function missingPrerequisites(module: AiModuleRow) {
    const prerequisites = MODULE_PREREQUISITES[module.code as ModuleCode] ?? [];
    return prerequisites.filter((requiredCode) => (
      !modules.some((candidate) => candidate.code === requiredCode && candidate.status === ACTIVE_STATUS)
    ));
  }

  function invalidCandidates(result: SetupPrecheckResponse, values: Record<string, string>) {
    return result.candidates.some((candidate) => {
      const value = Number(values[candidate.action_id]);
      return !Number.isInteger(value) || value < candidate.allowed_min_value || value > candidate.allowed_max_value;
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
      setMessage(`선행 모듈(${prerequisites.join(", ")})을 먼저 활성화해 주세요.`);
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
              module_activation: { module_code: code },
              ...(salaryPolicy ? { salary_policy: salaryPolicy } : {})
            },
            metric_scopes: []
          })
        }
      );
      setPrechecks((current) => ({ ...current, [code]: response }));
      setEditedValues((current) => ({
        ...current,
        [code]: Object.fromEntries(response.candidates.map((candidate) => [candidate.action_id, String(candidate.proposed_value)]))
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
    const prerequisites = missingPrerequisites(module);
    if (prerequisites.length > 0) {
      setMessage(`선행 모듈(${prerequisites.join(", ")})을 먼저 활성화해 주세요.`);
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
            initial_policy_values: {},
            recommendation_action_ids: []
          })
        }
      );
      await afterApplied(code, response.state_version);
      setMessage(`${module.name} 모듈을 활성화했어요.`);
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
              proposed_value: Number(values[candidate.action_id])
            })),
            reason: "교사가 추천값과 허용 구간을 검토하고 초기 정책을 확정함",
            // 서버는 제안 생성 시점의 정책 영역 버전과 일치해야 적용합니다. null은 항상 거절됩니다.
            expected_policy_version: policyVersionOf(policyVersions, modulePolicyArea(code))
          })
        }
      );
      await afterApplied(code, response.state_version);
      setMessage(`${module.name} 초기 정책을 확정하고 활성화했어요.`);
    } catch (error) {
      handleFailure(error, "추천값을 적용하지 못했어요.");
    } finally {
      setBusyCode("");
    }
  }

  return (
    <PlainSection
      title="선택 모듈 활성화"
      description="개설 때 선택한 모듈만 표시합니다. 급여·소득세·예금·대출은 AI 점검 후 교사가 값을 확정해야 활성화됩니다."
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
          return (
            <article className="module-activation-card" key={code}>
              <header>
                <div><strong>{module.name}</strong><small>{code}</small></div>
                <b className={`module-status ${isActive ? "active" : ""}`}>
                  {isActive ? <Check size={14} /> : null}{module.status}
                </b>
              </header>
              {prerequisites.length > 0 ? <p className="contract-warning">먼저 활성화: {prerequisites.join(", ")}</p> : null}
              {!isActive && needsPolicy ? (
                <button type="button" disabled={blocked} onClick={() => void requestPrecheck(module)}>
                  {busy ? "점검하는 중" : result ? "추천 다시 확인" : "AI 추천 확인"}
                </button>
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
                    const value = Number(values[candidate.action_id]);
                    const outsideRecommended = Number.isFinite(value) && (value < candidate.recommended_min_value || value > candidate.recommended_max_value);
                    const outsideAllowed = !Number.isInteger(value) || value < candidate.allowed_min_value || value > candidate.allowed_max_value;
                    return (
                      <label className={outsideAllowed ? "value-blocked" : outsideRecommended ? "value-warning" : ""} key={candidate.action_id}>
                        {candidate.label}
                        <input
                          type="number"
                          step="1"
                          value={values[candidate.action_id] ?? ""}
                          onChange={(event) => setEditedValues((current) => ({
                            ...current,
                            [code]: { ...(current[code] ?? {}), [candidate.action_id]: event.target.value }
                          }))}
                        />
                        <small>
                          추천 {valueForDisplay(candidate.recommended_min_value, candidate.unit)} ~ {valueForDisplay(candidate.recommended_max_value, candidate.unit)} · 허용 {valueForDisplay(candidate.allowed_min_value, candidate.unit)} ~ {valueForDisplay(candidate.allowed_max_value, candidate.unit)}
                        </small>
                        {outsideAllowed ? <em>허용 구간 밖이라 적용할 수 없습니다.</em> : outsideRecommended ? <em>추천 구간 밖입니다. 검토 후 적용할 수 있습니다.</em> : null}
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
