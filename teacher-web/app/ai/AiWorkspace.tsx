"use client";

import { useState } from "react";
import { AlertTriangle, Bot, Check, ShieldOff } from "lucide-react";
import { ApiError, apiRequest } from "../api";

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
  canManage
}: Readonly<{
  classroomId: string;
  initialStateVersion: number;
  canManage: boolean;
}>) {
  const [enabled, setEnabled] = useState(true);
  const [stateVersion, setStateVersion] = useState(initialStateVersion);
  const [reason, setReason] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [lastChange, setLastChange] = useState<{ reason: string; at: string } | null>(null);

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
          expected_state_version: stateVersion
        })
      });
      setEnabled(false);
      setStateVersion(response.state_version ?? stateVersion + 1);
      setLastChange({ reason: reason.trim(), at: new Date().toLocaleString("ko-KR") });
      setConfirming(false);
      setMessage("이 학급의 AI 기능을 중지했어요.");
    } catch (error) {
      setMessage(errorMessage(error));
      if (error instanceof ApiError && error.code === "POLICY_VERSION_CONFLICT") setConfirming(false);
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
          <div className="inline-notice"><AlertTriangle size={17} /> 부담임은 AI 사용 설정을 변경할 수 없어요.</div>
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

      <section className="plain-section">
        <div className="section-title-line">
          <div>
            <span className="section-kicker">상품·퀴즈</span>
            <h2>AI 추천 검토</h2>
          </div>
          <span className="connection-pending">백엔드 연결 대기</span>
        </div>
        <p className="section-description">
          상품 가격과 GAME 보상 추천은 시작 전 점검 API 계약이 develop에 합쳐진 뒤 연결됩니다.
          TEST 퀴즈에는 보상 설정을 제공하지 않습니다.
        </p>
      </section>
    </div>
  );
}

