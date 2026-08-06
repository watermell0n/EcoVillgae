"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, Bot, Check, ShieldOff } from "lucide-react";
import { ApiError, apiRequest } from "../api";
import type { AiModuleRow, AiPolicyVersionRow } from "../types";
import { DraftRecommendationPanel } from "./DraftRecommendationPanel";
import { MonitoringBalancingPanel } from "./MonitoringBalancingPanel";
import { ModuleActivationPanel } from "./ModuleActivationPanel";
import { TeacherChatPanel } from "./TeacherChatPanel";

type AiSettingResponse = {
  ai_resource_id: string;
  status: string;
  state_version?: number;
};

function errorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return "AI 설정을 변경하지 못했어요. 잠시 후 다시 시도해 주세요.";
  if (error.status === 403) return "이 기능은 담임 교사만 변경할 수 있어요.";
  if (error.code === "AI_APPLICATION_IN_PROGRESS") return "AI 제안을 적용하고 있어요. 적용이 끝난 뒤 다시 시도해 주세요.";
  if (error.code === "POLICY_VERSION_CONFLICT") return "다른 화면에서 설정이 변경됐어요. 최신 상태를 다시 확인한 뒤 실행해 주세요.";
  return error.message;
}

export function AiWorkspace({
  classroomId,
  initialStateVersion,
  initialEnabled,
  initialReason,
  initialPolicyVersion,
  canManage,
  modules,
  policyVersions,
  startDate,
  quizSessionId,
  activeStudentCount,
  effectiveTaxBps,
  onReload,
  onStateVersionChange,
  onSettingsChanged
}: Readonly<{
  classroomId: string;
  initialStateVersion: number;
  initialEnabled: boolean;
  initialReason: string;
  initialPolicyVersion: number;
  canManage: boolean;
  modules: readonly AiModuleRow[];
  policyVersions: readonly AiPolicyVersionRow[];
  startDate: string;
  quizSessionId: string;
  activeStudentCount: number;
  effectiveTaxBps: number;
  onReload: () => Promise<void>;
  onStateVersionChange: (version: number) => void;
  onSettingsChanged: (settings: { enabled: boolean; reason: string; version: number; stateVersion: number }) => void;
}>) {
  const [enabled, setEnabled] = useState(initialEnabled);
  const [stateVersion, setStateVersion] = useState(initialStateVersion);
  const [reason, setReason] = useState(initialReason);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [lastChange, setLastChange] = useState<{ reason: string; at: string } | null>(null);

  useEffect(() => {
    setEnabled(initialEnabled);
    setStateVersion(initialStateVersion);
    setReason(initialReason);
    setConfirming(false);
    setLastChange(initialReason ? { reason: initialReason, at: `정책 버전 ${initialPolicyVersion}` } : null);
  }, [initialEnabled, initialPolicyVersion, initialReason, initialStateVersion]);

  async function disableAi() {
    if (!reason.trim()) {
      setMessage("중지 사유를 입력해 주세요.");
      return;
    }
    setSubmitting(true);
    setMessage("");
    try {
      const response = await apiRequest<AiSettingResponse>(`/classrooms/${classroomId}/ai-settings`, {
        method: "PUT",
        body: JSON.stringify({
          enabled: false,
          reason: reason.trim(),
          expected_state_version: stateVersion,
          screen_id: "SCR-TCH-AI-SETTINGS",
          ui_action_id: "UIA-AI-DISABLE",
          client_occurred_at: new Date().toISOString()
        })
      });
      const nextStateVersion = response.state_version ?? stateVersion + 1;
      setEnabled(false);
      setStateVersion(nextStateVersion);
      onStateVersionChange(nextStateVersion);
      onSettingsChanged({ enabled: false, reason: reason.trim(), version: initialPolicyVersion + 1, stateVersion: nextStateVersion });
      setLastChange({ reason: reason.trim(), at: new Date().toLocaleString("ko-KR") });
      setConfirming(false);
      setMessage("이 학급의 AI 기능을 중지했어요.");
    } catch (error) {
      if (error instanceof ApiError && ["POLICY_VERSION_CONFLICT", "STATE_VERSION_CONFLICT"].includes(error.code)) {
        setConfirming(false);
        await onReload();
        setMessage("다른 화면에서 설정이 변경되어 최신 상태를 불러왔어요. 사유를 확인한 뒤 다시 실행해 주세요.");
      } else {
        setMessage(errorMessage(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page-flow ai-workspace">
      <section className="plain-section">
        <div className="section-title-line">
          <div>
            <span className="section-kicker">학급 AI</span>
            <h2>AI 사용 설정</h2>
          </div>
          <span className={`ai-status ${enabled ? "on" : "off"}`}>
            {enabled ? <Bot size={16} /> : <ShieldOff size={16} />}
            {enabled ? "사용 중" : "사용 중지"}
          </span>
        </div>

        <div className="ai-explanation">
          <strong>AI를 중지해도 수업은 그대로 진행돼요.</strong>
          <p>급여·세율·금리 추천값과 시작 전 점검은 규칙 엔진이 계속 계산합니다.</p>
          <p>중지되는 기능은 AI 설명 문장, 교사 챗봇, 자동 모니터링입니다.</p>
        </div>

        {lastChange ? (
          <dl className="ai-change-history">
            <div><dt>마지막 변경</dt><dd>{lastChange.at}</dd></div>
            <div><dt>변경 사유</dt><dd>{lastChange.reason}</dd></div>
          </dl>
        ) : null}

        {!canManage ? (
          <div className="inline-notice"><AlertTriangle size={17} /> 학급 소유 교사만 AI 사용 설정을 변경할 수 있어요.</div>
        ) : enabled ? (
          <div className="ai-disable-form">
            <label>
              중지 사유
              <textarea
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                placeholder="예: 이번 수업에서는 규칙 기반 추천만 사용합니다."
                rows={3}
              />
            </label>
            {!confirming ? (
              <button type="button" className="danger-action" disabled={!reason.trim()} onClick={() => setConfirming(true)}>
                AI 사용 중지 검토
              </button>
            ) : (
              <div className="ai-confirmation">
                <AlertTriangle size={20} />
                <div>
                  <strong>현재 계약에서는 화면에서 다시 켤 수 없어요.</strong>
                  <p>이 사유로 AI 사용을 중지할까요?</p>
                </div>
                <button type="button" className="ghost-action" onClick={() => setConfirming(false)}>취소</button>
                <button type="button" className="danger-action" disabled={submitting} onClick={() => void disableAi()}>
                  {submitting ? "중지하는 중" : "중지 확정"}
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="ai-disabled-result"><Check size={18} /> AI 설명과 자동 모니터링이 중지되어 있어요.</div>
        )}
        {message ? <div className="inline-notice" role="status">{message}</div> : null}
      </section>

      <TeacherChatPanel
        classroomId={classroomId}
        classroomStartDate={startDate}
        enabled={enabled}
      />

      <ModuleActivationPanel
        classroomId={classroomId}
        modules={modules}
        policyVersions={policyVersions}
        activeStudentCount={activeStudentCount}
        startDate={startDate}
        canManage={canManage}
        onReload={onReload}
        onStateVersionChange={onStateVersionChange}
      />

      <MonitoringBalancingPanel
        classroomId={classroomId}
        enabled={enabled}
        canManage={canManage}
        onReload={onReload}
      />

      {/* 투자 시나리오 생성·검토는 '운영 관리 > 투자' 탭에서 단기·장기 각각의 자리에 있습니다. */}
      {/* 상품 가격 추천은 '상점 상품' 탭에서 상품을 등록·관리하는 자리에 있습니다. */}
      <DraftRecommendationPanel
        kind="quiz"
        classroomId={classroomId}
        policyVersions={policyVersions}
        quizSessionId={quizSessionId}
        activeStudentCount={activeStudentCount}
        effectiveTaxBps={effectiveTaxBps}
        onReload={onReload}
        onStateVersionChange={onStateVersionChange}
      />
    </div>
  );
}
