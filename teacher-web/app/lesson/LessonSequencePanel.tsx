"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, LoaderCircle, Play, Square } from "lucide-react";

import { ApiError, apiRequest } from "../api";

type ManifestItem = {
  no: string;
  title: string;
  file: string;
};

type ManifestCategory = {
  id: string;
  round: number;
  title: string;
  isNew: boolean;
  note: string | null;
  items: ManifestItem[];
};

type Manifest = {
  imageCount: number;
  categoryCount: number;
  categories: ManifestCategory[];
};

type ActivityView = {
  activity_id: string;
  status: string;
  state_version: number;
  activity_type?: string;
  presentation_title?: string;
  presentation_image_url?: string;
  presentation_page?: number;
  presentation_total_pages?: number;
};

// 서버는 snake_case로 돌려줍니다.
type PresentationResult = {
  activity_id: string;
  status: string;
  state_version: number;
  page: number;
  total_pages: number;
  title: string;
  image_url: string;
  has_previous: boolean;
  has_next: boolean;
};

type Running = {
  activityId: string;
  stateVersion: number;
  page: number;
  totalPages: number;
  title: string;
};

const SCREEN_ID = "SCR-TCH-004";

function errorMessage(caught: unknown, fallback: string) {
  if (caught instanceof ApiError && caught.code === "ACTIVITY_ALREADY_LIVE") {
    return "진행 중인 퀴즈나 다른 수업이 있어요. 수업 준비·퀴즈 화면에서 해당 활동을 먼저 마감해 주세요.";
  }
  if (caught instanceof ApiError) return caught.message;
  return fallback;
}

/**
 * 회차 자료를 순서대로 넘기며 진행하는 선생님 수업 모듈이다.
 *
 * <p>자료 파일은 teacher-web이 정적으로 들고 있고 학생 앱은 백엔드만 보므로, 시작할 때
 * 이미지를 데이터 URI로 바꿔 보낸다. 기존 한 장 띄우기도 같은 방식이라 서버·학생 쪽에
 * 새 전달 경로를 만들지 않는다. 다만 학생 화면이 3초마다 현재 장을 다시 받아 가므로,
 * 자료를 정적 주소로 내려주도록 바꾸면 트래픽이 크게 줄어든다.
 */
export function LessonSequencePanel({
  classroomId,
  activeGuidedActivityId,
  canManage,
  onNotice
}: Readonly<{
  classroomId: string;
  activeGuidedActivityId?: string;
  canManage: boolean;
  // page.tsx의 addLog와 같은 모양입니다. 톤은 그쪽 LogTone을 그대로 따릅니다.
  onNotice: (message: string, tone?: "success" | "warning" | "error") => void;
}>) {
  const [manifest, setManifest] = useState<Manifest | null>(null);
  const [categoryId, setCategoryId] = useState("");
  const [running, setRunning] = useState<Running | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let alive = true;
    fetch("/lessons/manifest.json")
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error("manifest"))))
      .then((data: Manifest) => {
        if (!alive) return;
        setManifest(data);
        const first = data.categories.find((category) => category.items.length > 0);
        if (first) setCategoryId(first.id);
      })
      .catch(() => {
        if (alive) setError("수업 자료 목록을 불러오지 못했어요. 새로고침한 뒤 다시 시도해 주세요.");
      });
    return () => {
      alive = false;
    };
  }, []);

  // 새로고침하거나 다른 화면을 다녀와도 진행 중인 수업과 현재 장을 되찾습니다.
  const restore = useCallback(async () => {
    if (!activeGuidedActivityId) {
      setRunning(null);
      return;
    }
    try {
      const activity = await apiRequest<ActivityView>(
        `/classrooms/${classroomId}/guided-activities/${activeGuidedActivityId}`
      );
      if (activity.activity_type !== "ECONOMIC_CONCEPT_IMAGE") return;
      const total = activity.presentation_total_pages ?? 1;
      if (total <= 1) return; // 한 장 띄우기는 아래 기존 패널이 다룹니다.
      setRunning({
        activityId: activity.activity_id,
        stateVersion: activity.state_version,
        page: activity.presentation_page ?? 1,
        totalPages: total,
        title: activity.presentation_title ?? ""
      });
    } catch {
      // 조회 실패는 화면을 막지 않습니다. 진행 중 수업이 없는 것으로 둡니다.
    }
  }, [activeGuidedActivityId, classroomId]);

  useEffect(() => {
    void restore();
  }, [restore]);

  const selected = useMemo(
    () => manifest?.categories.find((category) => category.id === categoryId) ?? null,
    [manifest, categoryId]
  );

  /** 정적 자료를 데이터 URI로 바꿉니다. 학생 앱이 teacher-web 주소를 알지 못하기 때문입니다. */
  async function toDataUrl(file: string) {
    const response = await fetch(`/${file}`);
    if (!response.ok) throw new Error(`자료를 읽지 못했습니다: ${file}`);
    const blob = await response.blob();
    return await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result ?? ""));
      reader.onerror = () => reject(new Error("자료를 변환하지 못했습니다."));
      reader.readAsDataURL(blob);
    });
  }

  async function startLesson() {
    if (!selected || selected.items.length === 0 || busy) return;
    setBusy(true);
    setError("");
    try {
      const slides = [];
      for (const item of selected.items) {
        slides.push({ title: item.title.slice(0, 120), image_url: await toDataUrl(item.file) });
      }
      const result = await apiRequest<{ activity_id: string; state_version: number }>(
        `/classrooms/${classroomId}/guided-activities/image-lessons`,
        {
          method: "POST",
          body: JSON.stringify({
            slides,
            screen_id: SCREEN_ID,
            ui_action_id: "UIA-ACT-010",
            client_occurred_at: new Date().toISOString()
          })
        }
      );
      setRunning({
        activityId: result.activity_id,
        stateVersion: result.state_version,
        page: 1,
        totalPages: slides.length,
        title: selected.items[0].title
      });
      onNotice(`${selected.round}회차 ${selected.title} 수업을 시작했어요. 학생 화면이 첫 장으로 고정됩니다.`);
    } catch (caught) {
      setError(errorMessage(caught, "수업을 시작하지 못했어요."));
    } finally {
      setBusy(false);
    }
  }

  async function move(command: "NEXT" | "PREVIOUS") {
    if (!running || busy) return;
    setBusy(true);
    setError("");
    try {
      const result = await apiRequest<PresentationResult>(
        `/classrooms/${classroomId}/guided-activities/${running.activityId}/presentation-commands`,
        {
          method: "POST",
          body: JSON.stringify({
            command,
            screen_id: SCREEN_ID,
            ui_action_id: command === "NEXT" ? "UIA-ACT-011" : "UIA-ACT-012",
            client_occurred_at: new Date().toISOString()
          })
        }
      );
      setRunning({
        activityId: result.activity_id,
        stateVersion: result.state_version,
        page: result.page,
        totalPages: result.total_pages,
        title: result.title
      });
    } catch (caught) {
      setError(errorMessage(caught, "자료를 넘기지 못했어요."));
    } finally {
      setBusy(false);
    }
  }

  async function finishLesson() {
    if (!running || busy) return;
    setBusy(true);
    setError("");
    try {
      await apiRequest(
        `/classrooms/${classroomId}/guided-activities/${running.activityId}/finish-commands`,
        {
          method: "POST",
          body: JSON.stringify({
            command: "END",
            expected_state_version: running.stateVersion,
            screen_id: SCREEN_ID,
            ui_action_id: "UIA-ACT-013",
            client_occurred_at: new Date().toISOString()
          })
        }
      );
      setRunning(null);
      onNotice("수업을 끝냈어요. 학생 화면이 마을로 돌아갑니다.");
    } catch (caught) {
      setError(errorMessage(caught, "수업을 끝내지 못했어요."));
    } finally {
      setBusy(false);
    }
  }

  const currentItem = running && selected
    ? selected.items[running.page - 1]
    : null;

  return (
    <div className="lesson-sequence-panel">
      <div className="lesson-sequence-heading">
        <h2>회차 자료로 수업하기</h2>
        <p>
          회차를 고르면 그 회차 자료가 순서대로 학생 화면에 뜹니다. 선생님이 넘기는 대로 학생
          화면도 함께 넘어가고, 수업이 끝날 때까지 학생은 다른 활동을 할 수 없습니다.
        </p>
      </div>

      {running ? (
        <div className="lesson-sequence-live">
          <div className="lesson-sequence-progress">
            <strong>
              {running.page} / {running.totalPages}
            </strong>
            <span>{running.title}</span>
            <em>● 현재 학생 화면에 표시 중</em>
          </div>
          {currentItem ? (
            <div className="lesson-sequence-preview">
              <img src={`/${currentItem.file}`} alt={`${currentItem.title} 미리보기`} />
            </div>
          ) : null}
          <div className="lesson-sequence-actions">
            <button type="button" disabled={busy || running.page <= 1} onClick={() => void move("PREVIOUS")}>
              <ChevronLeft size={16} aria-hidden /> 이전
            </button>
            <button
              type="button"
              disabled={busy || running.page >= running.totalPages}
              onClick={() => void move("NEXT")}
            >
              다음 <ChevronRight size={16} aria-hidden />
            </button>
            <button type="button" className="danger-text" disabled={busy} onClick={() => void finishLesson()}>
              <Square size={14} aria-hidden /> 수업 끝내기
            </button>
            {busy ? <LoaderCircle size={16} className="lesson-sequence-spin" aria-hidden /> : null}
          </div>
        </div>
      ) : (
        <div className="lesson-sequence-setup">
          <label>
            회차
            <select
              value={categoryId}
              disabled={busy || !manifest}
              onChange={(event) => setCategoryId(event.target.value)}
            >
              {manifest?.categories.map((category) => (
                <option key={category.id} value={category.id} disabled={category.items.length === 0}>
                  {category.round}회차 · {category.title}
                  {category.items.length === 0 ? " (자료 없음)" : ` (${category.items.length}장)`}
                </option>
              ))}
            </select>
          </label>
          {selected && selected.items.length > 0 ? (
            <ol className="lesson-sequence-list">
              {selected.items.map((item) => (
                <li key={item.no}>{item.title}</li>
              ))}
            </ol>
          ) : selected ? (
            <p className="helper-text">{selected.note ?? "이 회차에는 띄울 자료가 없습니다."}</p>
          ) : null}
          <div className="lesson-sequence-actions">
            <button
              type="button"
              disabled={!canManage || busy || !selected || selected.items.length === 0}
              onClick={() => void startLesson()}
            >
              {busy ? (
                <>
                  <LoaderCircle size={16} className="lesson-sequence-spin" aria-hidden /> 자료 준비 중
                </>
              ) : (
                <>
                  <Play size={15} aria-hidden /> 수업 시작
                </>
              )}
            </button>
          </div>
          {canManage ? null : <p className="helper-text">수업을 시작할 권한이 없습니다.</p>}
        </div>
      )}

      {error ? <p className="lesson-sequence-error">{error}</p> : null}
    </div>
  );
}
