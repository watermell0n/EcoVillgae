"use client";

// AI 시작 전 점검·추천 기반 모듈 활성화 UI입니다.
// 소유: AI 담당 — Spring/FastAPI 계약을 교사 화면에 붙이는 코드만 이 폴더에 둡니다.
// page.tsx(교사 프런트 소유)는 이 컴포넌트를 배치만 하고 내부는 건드리지 않습니다.

import { useState, type Dispatch, type SetStateAction } from "react";
import {
  MODULE_PREREQUISITES,
  STUDENT_ACCESS_MODULE_NAMES,
  valueForDisplay,
  type AiProposalCommandResponse,
  type ModuleActivationResponse,
  type ModuleCode,
  type SetupPrecheckResponse
} from "../api";
import type { ClassSettings, LogTone, ModuleRow, RunAction, StudentRow } from "../types";
import { PlainSection } from "../ui";

// 교사가 값을 확정해야 활성화되는 정책 모듈입니다.
const POLICY_MODULE_CODES = new Set<ModuleCode>(["ROLE_SALARY", "INCOME_TAX", "DEPOSIT", "LOAN"]);

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
  settings,
  modules,
  students,
  addLog,
  runAction,
  setModules
}: Readonly<{
  settings: ClassSettings;
  modules: ModuleRow[];
  students: StudentRow[];
  addLog: (message: string, tone?: LogTone) => void;
  runAction: RunAction;
  setModules: Dispatch<SetStateAction<ModuleRow[]>>;
}>) {
  const [prechecks, setPrechecks] = useState<Partial<Record<ModuleCode, SetupPrecheckResponse>>>({});
  const [editedValues, setEditedValues] = useState<Partial<Record<ModuleCode, Record<string, string>>>>({});
  const selectedModules = modules.filter((module) => module.status !== "미선택");

  function updateModuleState(code: ModuleCode, response: ModuleActivationResponse | AiProposalCommandResponse) {
    setModules((items) => items.map((item) => item.code === code ? {
      ...item,
      status: "사용 중",
      studentState: STUDENT_ACCESS_MODULE_NAMES.includes(item.name) ? "학생 노출" : "교사 설정",
      teacherTodo: "활성화 완료",
      stateVersion: "module_code" in response ? response.state_version : item.stateVersion,
      policyVersion: response.policy_version
    } : item));
  }

  function missingPrerequisites(module: ModuleRow) {
    return (MODULE_PREREQUISITES[module.code] ?? []).filter((requiredCode) => (
      !modules.some((candidate) => candidate.code === requiredCode && candidate.status === "사용 중")
    ));
  }

  async function requestPrecheck(module: ModuleRow) {
    if (!settings.classroomId) {
      addLog("먼저 백엔드에서 학급을 생성해야 추천을 요청할 수 있습니다.", "warning");
      return;
    }
    const prerequisites = missingPrerequisites(module);
    if (prerequisites.length > 0) {
      addLog(`선행 모듈(${prerequisites.join(", ")})을 먼저 활성화해 주세요.`, "warning");
      return;
    }
    const salaryPolicy = module.code === "ROLE_SALARY" ? {
      roles: [{
        role_code: "DEFAULT_ROLE",
        name: "기본 역할",
        weight: 1,
        confirmed_salary: null,
        assigned_student_count: Math.max(students.length, Number(settings.studentCount) || 0),
        active: true
      }],
      pay_interval_days: 7,
      first_pay_on: settings.startDate,
      default_role_salary: 1000
    } : undefined;
    await runAction<SetupPrecheckResponse>(
      `${module.name} AI 시작 전 점검`,
      `/classrooms/${settings.classroomId}/ai/balancing-analysis-operations`,
      {
        method: "POST",
        body: JSON.stringify({
          analysis_mode: "SETUP_PRECHECK",
          trigger_context: {
            context_type: "MODULE_ACTIVATION",
            active_student_count: Math.max(students.length, Number(settings.studentCount) || 0),
            module_activation: { module_code: module.code },
            ...(salaryPolicy ? { salary_policy: salaryPolicy } : {})
          }
        })
      },
      (result) => {
        setPrechecks((current) => ({ ...current, [module.code]: result }));
        setEditedValues((current) => ({
          ...current,
          [module.code]: Object.fromEntries(result.candidates.map((candidate) => [candidate.action_id, String(candidate.proposed_value)]))
        }));
      }
    );
  }

  async function activateWithoutPolicy(module: ModuleRow) {
    const prerequisites = missingPrerequisites(module);
    if (prerequisites.length > 0) {
      addLog(`선행 모듈(${prerequisites.join(", ")})을 먼저 활성화해 주세요.`, "warning");
      return;
    }
    await runAction<ModuleActivationResponse>(
      `${module.name} 활성화`,
      `/classrooms/${settings.classroomId}/modules/${module.code}/activation-commands`,
      {
        method: "POST",
        body: JSON.stringify({
          expected_state_version: module.stateVersion,
          screen_id: "SCR-WEB-003",
          ui_action_id: "UIA-MOD-001",
          client_occurred_at: new Date().toISOString(),
          confirm_prerequisites: true,
          initial_policy_values: {},
          recommendation_action_ids: []
        })
      },
      (result) => updateModuleState(module.code, result)
    );
  }

  async function applyRecommendation(module: ModuleRow, result: SetupPrecheckResponse) {
    if (!result.proposal_id || result.overall_severity === "BLOCK") return;
    const values = editedValues[module.code] ?? {};
    const invalid = result.candidates.some((candidate) => {
      const value = Number(values[candidate.action_id]);
      return !Number.isInteger(value) || value < candidate.allowed_min_value || value > candidate.allowed_max_value;
    });
    if (invalid) {
      addLog("허용 구간 밖의 값은 적용할 수 없습니다.", "warning");
      return;
    }
    await runAction<AiProposalCommandResponse>(
      `${module.name} 추천안 적용`,
      `/classrooms/${settings.classroomId}/ai/balancing-proposals/${result.proposal_id}/commands`,
      {
        method: "POST",
        body: JSON.stringify({
          command: "APPLY",
          edited_values: result.candidates.map((candidate) => ({
            action_id: candidate.action_id,
            proposed_value: Number(values[candidate.action_id])
          })),
          reason: "교사가 추천값과 허용 구간을 검토하고 초기 정책을 확정함",
          expected_policy_version: 0
        })
      },
      (response) => updateModuleState(module.code, response)
    );
  }

  return (
    <PlainSection title="선택 모듈 활성화" description="방 생성 때 선택한 모듈만 표시합니다. 정책 모듈은 AI 점검 후 교사가 값을 확정해야 활성화됩니다.">
      <div className="module-activation-list">
        {selectedModules.length === 0 ? <p className="empty-copy">선택한 모듈이 없습니다. 학생은 직업 없이 기본 학급 흐름만 이용합니다.</p> : null}
        {selectedModules.map((module) => {
          const result = prechecks[module.code];
          const values = editedValues[module.code] ?? {};
          const prerequisites = missingPrerequisites(module);
          const needsPolicy = POLICY_MODULE_CODES.has(module.code);
          const invalidValue = result?.candidates.some((candidate) => {
            const value = Number(values[candidate.action_id]);
            return !Number.isInteger(value) || value < candidate.allowed_min_value || value > candidate.allowed_max_value;
          }) ?? false;
          return (
            <article className="module-activation-card" key={module.code}>
              <header>
                <div><strong>{module.name}</strong><small>{module.code}</small></div>
                <b className={`module-status ${module.status === "사용 중" ? "active" : ""}`}>{module.status}</b>
              </header>
              {prerequisites.length > 0 ? <p className="contract-warning">먼저 활성화: {prerequisites.join(", ")}</p> : null}
              {module.status !== "사용 중" && needsPolicy ? (
                <button type="button" disabled={!settings.classroomId || prerequisites.length > 0} onClick={() => requestPrecheck(module)}>
                  {result ? "추천 다시 확인" : "AI 추천 확인"}
                </button>
              ) : null}
              {module.status !== "사용 중" && !needsPolicy ? (
                <button type="button" disabled={!settings.classroomId || prerequisites.length > 0} onClick={() => activateWithoutPolicy(module)}>
                  활성화
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
                            [module.code]: { ...(current[module.code] ?? {}), [candidate.action_id]: event.target.value }
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
                    disabled={!result.proposal_id || result.overall_severity === "BLOCK" || invalidValue}
                    onClick={() => applyRecommendation(module, result)}
                  >
                    추천값 검토 완료 · 활성화
                  </button>
                </div>
              ) : null}
            </article>
          );
        })}
      </div>
    </PlainSection>
  );
}
