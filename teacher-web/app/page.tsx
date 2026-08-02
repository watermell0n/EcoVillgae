"use client";

import { ChangeEvent, useMemo, useState } from "react";
import {
  ApiError,
  MODULE_DEFINITIONS,
  MODULE_PREREQUISITES,
  STUDENT_ACCESS_MODULE_NAMES,
  apiRequest,
  periodToCourseSize,
  type CreateClassroomResponse,
  type ModuleCode,
  type SetupPrecheckResponse
} from "./api";
import { ModuleActivationPanel, SetupPrecheckSummary } from "./ai/ModuleActivationPanel";
import type { ClassSettings, LogTone, ModuleRow, RunAction, StudentRow } from "./types";
import { PlainSection } from "./ui";

type ViewKey = "overview" | "plan" | "setup" | "roster" | "shop" | "control" | "approvals";
type TeacherStage = "login" | "classrooms" | "create" | "console";
type CreateStep = "basic" | "period" | "modules" | "complete";
type LogEntry = {
  id: string;
  tone: LogTone;
  message: string;
};

type ProductRow = {
  name: string;
  price: string;
  stock: string;
  imageName: string;
  imageSrc: string;
};

type ApprovalRow = {
  id: string;
  type: string;
  target: string;
  amount: string;
  status: string;
};

type LessonMaterial = {
  id: string;
  title: string;
  stage: string;
  minutes: string;
  status: string;
};

const views: Array<{ key: ViewKey; label: string }> = [
  { key: "overview", label: "운영 현황" },
  { key: "plan", label: "운영 계획" },
  { key: "setup", label: "학급 설정" },
  { key: "roster", label: "학생·급여" },
  { key: "shop", label: "상점 상품" },
  { key: "control", label: "수업 제어" },
  { key: "approvals", label: "승인·정산" }
];

const initialModules: ModuleRow[] = MODULE_DEFINITIONS.map((module) => ({
  code: module.code,
  name: module.name,
  status: "미선택",
  studentState: "학생 미노출",
  teacherTodo: "방 생성에서 선택",
  stateVersion: 0,
  policyVersion: null
}));

const initialStudents: StudentRow[] = [];

const initialProducts: ProductRow[] = [
  { name: "연필 교환권", price: "30", stock: "12", imageName: "", imageSrc: "" },
  { name: "간식 우선권", price: "50", stock: "4", imageName: "", imageSrc: "" }
];

const initialApprovals: ApprovalRow[] = [
  { id: "trade-1", type: "거래", target: "김하늘 → 박서윤", amount: "30비빔", status: "교사 승인 대기" },
  { id: "shop-1", type: "상점", target: "숙제 패스권", amount: "2,000비빔", status: "권리 상품 확인" },
  { id: "vote-1", type: "투표", target: "공동기금 사용처", amount: "340비빔", status: "결과 정산 대기" }
];

const initialSettings: ClassSettings = {
  classroomId: process.env.NEXT_PUBLIC_CLASSROOM_ID ?? "",
  schoolId: process.env.NEXT_PUBLIC_SCHOOL_ID ?? "",
  stateVersion: 0,
  className: "4학년 1반",
  schoolName: "이코초등학교",
  grade: "4",
  classNumber: "1",
  description: "우리 반 경제생활 프로젝트",
  classCode: "ABCD2345",
  startDate: "2026-07-31",
  endDate: "2026-08-07",
  currency: "비빔",
  period: "1주 프로젝트",
  studentCount: "24",
  incomeTax: "10"
};

const initialLessonMaterials: LessonMaterial[] = [
  { id: "lesson-1", title: "월급과 실수령액", stage: "1교시", minutes: "15", status: "준비 완료" },
  { id: "lesson-2", title: "세금과 공동기금", stage: "1교시", minutes: "10", status: "준비 완료" },
  { id: "lesson-3", title: "예금과 투자 선택", stage: "2교시", minutes: "20", status: "수정 필요" }
];

const selectableModuleNames: string[] = MODULE_DEFINITIONS.map((module) => module.name);
const moduleDescriptions = Object.fromEntries(MODULE_DEFINITIONS.map((module) => [module.name, module.description]));

function getRequiredModules(moduleName: string): string[] {
  const moduleCode = MODULE_DEFINITIONS.find((module) => module.name === moduleName)?.code;
  if (!moduleCode) return [];
  const visited = new Set<string>();

  function visit(code: ModuleCode) {
    for (const dependency of MODULE_PREREQUISITES[code] ?? []) {
      if (visited.has(dependency)) continue;
      visited.add(dependency);
      visit(dependency);
    }
  }

  visit(moduleCode);
  return [...visited]
    .map((code) => MODULE_DEFINITIONS.find((module) => module.code === code)?.name)
    .filter((name): name is NonNullable<typeof name> => name !== undefined);
}

export default function TeacherConsole() {
  const [stage, setStage] = useState<TeacherStage>("login");
  const [createStep, setCreateStep] = useState<CreateStep>("basic");
  const [hasClassroom, setHasClassroom] = useState(false);
  const [hasDraft, setHasDraft] = useState(false);
  const [view, setView] = useState<ViewKey>("overview");
  const [mode, setMode] = useState("자율 활동");
  const [modules, setModules] = useState<ModuleRow[]>(initialModules);
  const [students, setStudents] = useState<StudentRow[]>(initialStudents);
  const [products, setProducts] = useState<ProductRow[]>(initialProducts);
  const [approvals, setApprovals] = useState<ApprovalRow[]>(initialApprovals);
  const [logs, setLogs] = useState<LogEntry[]>([
    { id: "init", tone: "warning", message: "API 요청에 실패하면 화면 상태를 변경하지 않습니다. 교사 OIDC 연결 전에는 인증 오류가 표시될 수 있습니다." }
  ]);
  const [settings, setSettings] = useState<ClassSettings>(initialSettings);
  const roleSalaryActive = modules.some((module) => module.code === "ROLE_SALARY" && module.status === "사용 중");
  const title = useMemo(() => {
    if (view === "roster" && !roleSalaryActive) return "학생 명단";
    return views.find((item) => item.key === view)?.label ?? "운영 현황";
  }, [roleSalaryActive, view]);

  function addLog(message: string, tone: LogTone = "success") {
    setLogs((items) => [{ id: crypto.randomUUID(), tone, message }, ...items].slice(0, 6));
  }

  async function runAction<T>(label: string, path: string, init: RequestInit, onSuccess: (data: T) => void) {
    try {
      const data = await apiRequest<T>(path, init);
      onSuccess(data);
      addLog(`${label}: 백엔드 처리 완료`);
      return data;
    } catch (error) {
      const detail = error instanceof ApiError ? `${error.code} · ${error.message}` : error instanceof Error ? error.message : "알 수 없는 오류";
      addLog(`${label}: 처리되지 않았습니다. ${detail}`, "error");
      return null;
    }
  }

  function logout() {
    setStage("login");
    setView("overview");
    setCreateStep("basic");
  }

  function leaveClassroom() {
    setStage("classrooms");
    setView("overview");
  }

  if (stage === "login") {
    return <TeacherLogin onLogin={() => setStage("classrooms")} />;
  }

  if (stage === "classrooms") {
    return (
      <ClassroomSelect
        settings={settings}
        hasClassroom={hasClassroom}
        hasDraft={hasDraft}
        onCreate={() => {
          setModules(initialModules);
          setCreateStep("basic");
          setStage("create");
        }}
        onResume={() => {
          setCreateStep("period");
          setStage("create");
        }}
        onOpen={() => {
          setStage("console");
        }}
        onLogout={logout}
      />
    );
  }

  if (stage === "create") {
    return (
      <CreateClassroomFlow
        step={createStep}
        setStep={setCreateStep}
        settings={settings}
        setSettings={setSettings}
        modules={modules}
        setModules={setModules}
        runAction={runAction}
        onCancel={() => {
          setCreateStep("basic");
          setStage("classrooms");
        }}
        onSaveDraft={() => {
          setHasDraft(true);
          setStage("classrooms");
        }}
        onLogout={logout}
        onDone={(nextView) => {
          setHasClassroom(true);
          setHasDraft(false);
          setView(nextView);
          setStage("console");
        }}
      />
    );
  }

  return (
    <main className="teacher-shell">
      <aside className="side">
        <div className="brand">
          <span>🏘️</span>
          <div>
            <strong>이코빌리지</strong>
            <small>교사 콘솔</small>
          </div>
        </div>
        <nav>
          {views.map((item) => (
            <button key={item.key} className={view === item.key ? "active" : ""} onClick={() => setView(item.key)}>
              {item.key === "roster" && !roleSalaryActive ? "학생 명단" : item.label}
            </button>
          ))}
        </nav>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <button type="button" className="classroom-back" onClick={leaveClassroom}>← 내 경제교실</button>
            <h1>{title}</h1>
          </div>
          <div className="teacher-meta">
            <span>{settings.className}</span>
            <b>{mode}</b>
            <div>
              <button type="button" onClick={leaveClassroom}>교실 나가기</button>
              <button type="button" onClick={logout}>로그아웃</button>
            </div>
          </div>
        </header>

        <div className="console-content">
          <section className="integration-log" aria-live="polite">
            {logs.slice(0, 3).map((entry) => (
              <p className={entry.tone} key={entry.id}>{entry.message}</p>
            ))}
          </section>
          {view === "overview" && <Overview mode={mode} modules={modules} students={students} approvals={approvals} />}
          {view === "plan" && <CoursePlanning modules={modules} setModules={setModules} addLog={addLog} />}
          {view === "setup" && (
            <Setup
              settings={settings}
              setSettings={setSettings}
              modules={modules}
              setModules={setModules}
              students={students}
              addLog={addLog}
              runAction={runAction}
            />
          )}
          {view === "roster" && <Roster settings={settings} setSettings={setSettings} modules={modules} students={students} setStudents={setStudents} runAction={runAction} addLog={addLog} />}
          {view === "shop" && <ShopAdmin products={products} setProducts={setProducts} addLog={addLog} />}
          {view === "control" && (
            <Control
              classroomId={settings.classroomId}
              mode={mode}
              setMode={setMode}
              modules={modules}
              setModules={setModules}
              runAction={runAction}
              addLog={addLog}
            />
          )}
          {view === "approvals" && (
            <Approvals classroomId={settings.classroomId} approvals={approvals} setApprovals={setApprovals} runAction={runAction} addLog={addLog} />
          )}
        </div>
      </section>
    </main>
  );
}

function TeacherLogin({ onLogin }: Readonly<{ onLogin: () => void }>) {
  const [email, setEmail] = useState("teacher@school.kr");
  const [password, setPassword] = useState("password123");
  const canLogin = Boolean(email.trim() && password.trim());

  return (
    <main className="auth-page">
      <section className="login-panel">
        <div className="brand login-brand">
          <span>🏘️</span>
          <div>
            <strong>이코빌리지</strong>
            <small>교사 관리 웹</small>
          </div>
        </div>
        <h1>교사 로그인</h1>
        <div className="auth-form">
          <label>
            이메일
            <input value={email} onChange={(event) => setEmail(event.target.value)} />
          </label>
          <label>
            비밀번호
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </label>
          <button type="button" disabled={!canLogin} onClick={onLogin}>교사로 로그인</button>
        </div>
        <div className="auth-links">
          <button type="button">비밀번호 찾기</button>
          <button type="button">처음 사용하는 교사 안내</button>
        </div>
      </section>
    </main>
  );
}

function ClassroomSelect({
  settings,
  hasClassroom,
  hasDraft,
  onCreate,
  onResume,
  onOpen,
  onLogout
}: Readonly<{
  settings: ClassSettings;
  hasClassroom: boolean;
  hasDraft: boolean;
  onCreate: () => void;
  onResume: () => void;
  onOpen: () => void;
  onLogout: () => void;
}>) {
  return (
    <main className="select-page">
      <header className="select-topbar">
        <div className="brand">
          <span>🏘️</span>
          <div>
            <strong>이코빌리지</strong>
            <small>교사 관리 웹</small>
          </div>
        </div>
        <div className="header-actions">
          <button type="button" onClick={onCreate}>새 경제교실 만들기</button>
          <button type="button" className="ghost-action" onClick={onLogout}>로그아웃</button>
        </div>
      </header>
      <section className="select-content">
        <div className="classroom-list">
          <div className="select-heading">
            <span>교실 선택</span>
            <h1>내 경제교실</h1>
          </div>
          {hasClassroom ? (
            <button className="classroom-row" type="button" onClick={onOpen}>
              <span>
                <strong>{settings.className}</strong>
                <small>{settings.schoolName} · {settings.grade}학년 {settings.classNumber}반</small>
              </span>
              <span>{settings.startDate} ~ {settings.endDate}</span>
              <b>운영 중 · 학생 4명</b>
            </button>
          ) : (
            <div className="empty-classroom">
              <span>새 집</span>
              <h1>아직 만든 경제교실이 없어요</h1>
              <p>우리 반 경제생활을 시작하려면 경제교실을 먼저 만들어주세요.</p>
              <button type="button" onClick={onCreate}>경제교실 만들기</button>
            </div>
          )}
          {hasDraft ? (
            <button className="classroom-row draft" type="button" onClick={onResume}>
              <span>
                <strong>{settings.className || "이름 없는 교실"}</strong>
                <small>작성 중인 경제교실</small>
              </span>
              <span>기간과 모듈 설정을 이어서 작성해요.</span>
              <b>임시 저장 · 계속 만들기</b>
            </button>
          ) : null}
        </div>
      </section>
    </main>
  );
}

function CreateClassroomFlow({
  step,
  setStep,
  settings,
  setSettings,
  modules,
  setModules,
  runAction,
  onCancel,
  onSaveDraft,
  onLogout,
  onDone
}: Readonly<{
  step: CreateStep;
  setStep: React.Dispatch<React.SetStateAction<CreateStep>>;
  settings: ClassSettings;
  setSettings: React.Dispatch<React.SetStateAction<ClassSettings>>;
  modules: ModuleRow[];
  setModules: React.Dispatch<React.SetStateAction<ModuleRow[]>>;
  runAction: RunAction;
  onCancel: () => void;
  onSaveDraft: () => void;
  onLogout: () => void;
  onDone: (view: ViewKey) => void;
}>) {
  const [focusedModule, setFocusedModule] = useState("투자");
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [precheck, setPrecheck] = useState<SetupPrecheckResponse | null>(null);
  const [selectedModules, setSelectedModules] = useState<Set<string>>(() => new Set(
    selectableModuleNames.filter((moduleName) => modules.some((module) => module.name === moduleName && module.status !== "미선택"))
  ));

  function updateSettings(field: keyof ClassSettings, value: string) {
    setSettings((current) => ({ ...current, [field]: value }));
  }

  function toggleModule(moduleName: string) {
    const next = new Set(selectedModules);
    if (next.has(moduleName)) next.delete(moduleName);
    else next.add(moduleName);

    const modulesToOpen = new Set<string>(next);
    for (const selectedModule of next) {
      for (const requiredModule of getRequiredModules(selectedModule)) {
        modulesToOpen.add(requiredModule);
      }
    }

    setSelectedModules(next);
    setModules((items) => selectableModuleNames.reduce<ModuleRow[]>((result, name) => {
      const existing = items.find((item) => item.name === name);
      const status = modulesToOpen.has(name) ? "선택됨" : "미선택";
      return [...result, existing
        ? { ...existing, status }
        : { code: MODULE_DEFINITIONS.find((module) => module.name === name)!.code, name, status, studentState: "학생 미노출", teacherTodo: "활성화 전 설정 확인", stateVersion: 0, policyVersion: null }];
    }, items.filter((item) => !selectableModuleNames.includes(item.name))));
  }

  function calculateEndDate(startDate: string, period: string) {
    const start = new Date(`${startDate}T00:00:00`);
    if (Number.isNaN(start.getTime())) return startDate;

    const end = new Date(start);
    if (period === "1주 프로젝트") end.setDate(end.getDate() + 6);
    if (period === "1달 운영") {
      end.setMonth(end.getMonth() + 1);
      end.setDate(end.getDate() - 1);
    }
    if (period === "1학기 운영") {
      end.setMonth(end.getMonth() + 6);
      end.setDate(end.getDate() - 1);
    }

    const year = end.getFullYear();
    const month = String(end.getMonth() + 1).padStart(2, "0");
    const day = String(end.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function selectPeriod(period: string) {
    setSettings((current) => ({
      ...current,
      period,
      endDate: calculateEndDate(current.startDate, period)
    }));
  }

  function changeStartDate(startDate: string) {
    setSettings((current) => ({
      ...current,
      startDate,
      endDate: calculateEndDate(startDate, current.period)
    }));
  }

  async function createClassroom() {
    if (!settings.schoolId) return;
    const selectedCodes = modules
      .filter((module) => selectedModules.has(module.name))
      .map((module) => module.code);
    const created = await runAction<CreateClassroomResponse>(
      "경제교실 개설",
      "/classrooms",
      {
        method: "POST",
        body: JSON.stringify({
          expected_state_version: 0,
          screen_id: "SCR-WEB-002",
          ui_action_id: "UIA-CLS-001",
          client_occurred_at: new Date().toISOString(),
          school_id: settings.schoolId,
          currency_name: settings.currency,
          grade: Number(settings.grade),
          course_size: periodToCourseSize(settings.period),
          starts_on: settings.startDate,
          ends_on: settings.endDate,
          student_count: Number(settings.studentCount),
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Seoul",
          salary_policy: null,
          module_codes: selectedCodes,
          autonomous_schedules: []
        })
      },
      (data) => {
        setSettings((current) => ({
          ...current,
          classroomId: data.classroom_id,
          classCode: data.classroom_code,
          stateVersion: data.state_version
        }));
        setModules((items) => items.map((module) => ({
          ...module,
          status: selectedCodes.includes(module.code) ? "설정 필요" : "미선택",
          studentState: "학생 미노출",
          teacherTodo: selectedCodes.includes(module.code) ? "추천 확인 후 활성화" : "선택하지 않음",
          stateVersion: 0,
          policyVersion: null
        })));
        setShowConfirmation(false);
        setStep("complete");
      }
    );
    if (!created) return;
    await runAction<SetupPrecheckResponse>(
      "AI 시작 전 공통 점검",
      `/classrooms/${created.classroom_id}/ai/balancing-analysis-operations`,
      {
        method: "POST",
        body: JSON.stringify({
          analysis_mode: "SETUP_PRECHECK",
          trigger_context: {
            context_type: "CLASSROOM_SETUP",
            active_student_count: Number(settings.studentCount)
          }
        })
      },
      setPrecheck
    );
  }

  return (
    <main className="create-page">
      <header className="create-header">
        <div className="brand">
          <span>🏘️</span>
          <div>
            <strong>이코빌리지</strong>
            <small>경제교실 만들기</small>
          </div>
        </div>
        <div className="header-actions">
          <Stepper active={step} />
          {step !== "complete" ? <button type="button" className="ghost-action" onClick={onSaveDraft}>임시 저장</button> : null}
          <button type="button" className="ghost-action" onClick={onCancel}>{step === "complete" ? "교실 목록" : "만들기 취소"}</button>
          <button type="button" className="ghost-action" onClick={onLogout}>로그아웃</button>
        </div>
      </header>

      {step === "basic" && (
        <section className="create-layout single">
          <div>
            <h1>기본 정보</h1>
            <div className="settings-form">
              <label>교실 이름<input value={settings.className} onChange={(event) => updateSettings("className", event.target.value)} /></label>
              <label>학교명<input value={settings.schoolName} onChange={(event) => updateSettings("schoolName", event.target.value)} /></label>
              <label>학년<input value={settings.grade} onChange={(event) => updateSettings("grade", event.target.value)} /></label>
              <label>반<input value={settings.classNumber} onChange={(event) => updateSettings("classNumber", event.target.value)} /></label>
              <label>예상 학생 수<input type="number" min="1" value={settings.studentCount} onChange={(event) => updateSettings("studentCount", event.target.value)} /></label>
              <label className="wide-field">수업 대상 설명<input value={settings.description} onChange={(event) => updateSettings("description", event.target.value)} /></label>
              <label>화폐명<input value={settings.currency} onChange={(event) => updateSettings("currency", event.target.value)} /></label>
            </div>
            {!settings.schoolId ? <p className="contract-warning">교사 인증 화면이 아직 OIDC와 연결되지 않았습니다. 실제 개설 테스트에는 <code>NEXT_PUBLIC_SCHOOL_ID</code>가 필요합니다.</p> : null}
            <div className="wizard-actions">
              <button type="button" className="primary" disabled={!settings.className.trim()} onClick={() => setStep("period")}>다음</button>
            </div>
          </div>
        </section>
      )}

      {step === "period" && (
        <section className="create-layout single">
          <div>
            <h1>운영 기간</h1>
            <div className="period-options">
              {["1회 체험", "1주 프로젝트", "1달 운영", "1학기 운영"].map((period) => (
                <button key={period} type="button" className={settings.period === period ? "selected" : ""} onClick={() => selectPeriod(period)}>
                  {period}
                </button>
              ))}
            </div>
            <div className="settings-form">
              <label>시작일<input type="date" value={settings.startDate} onChange={(event) => changeStartDate(event.target.value)} /></label>
              <label>종료일<input type="date" value={settings.endDate} onChange={(event) => updateSettings("endDate", event.target.value)} /></label>
            </div>
            <div className="wizard-actions">
              <button type="button" onClick={() => setStep("basic")}>이전</button>
              <button type="button" className="primary" onClick={() => setStep("modules")}>다음</button>
            </div>
          </div>
        </section>
      )}

      {step === "modules" && (
        <section className="create-layout">
          <div>
            <h1>모듈 선택</h1>
            <div className="module-card-grid">
              {selectableModuleNames.map((moduleName) => {
                const isOpen = selectedModules.has(moduleName);
                const isRequired = !selectedModules.has(moduleName)
                  && [...selectedModules].some((selectedModule) => getRequiredModules(selectedModule).includes(moduleName));

                return (
                  <div className={`module-card ${isOpen ? "selected" : ""} ${isRequired ? "required" : ""}`} key={moduleName}>
                    <button
                      type="button"
                      className="module-main-button"
                      aria-pressed={isOpen}
                      onClick={() => {
                        if (!isRequired) toggleModule(moduleName);
                      }}
                    >
                      <strong>{moduleName}</strong>
                    </button>
                    <button
                      type="button"
                      className="module-info-button"
                      aria-label={`${moduleName} 연결 기능 설명`}
                      onClick={() => setFocusedModule(moduleName)}
                    >
                      i
                    </button>
                  </div>
                );
              })}
            </div>
            {settings.period === "1회 체험" ? (
              <p className="helper-text">
                1회 코스에는 퀴즈·게임을 기본 추천하지만, 수업 일정과 정책을 확인해 다른 모듈도 선택할 수 있습니다.
              </p>
            ) : null}
            <div className="wizard-actions">
              <button type="button" onClick={() => setStep("period")}>이전</button>
              <button type="button" className="primary" onClick={() => setShowConfirmation(true)}>개설하기</button>
            </div>
          </div>
          <ModuleDependencyPanel moduleName={focusedModule} settings={settings} modules={modules} />
        </section>
      )}

      {showConfirmation ? (
        <div className="confirm-backdrop" role="presentation">
          <section className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="classroom-confirm-title">
            <span>최종 확인</span>
            <h1 id="classroom-confirm-title">개설 요약을 확정하시겠습니까?</h1>
            <CreationSummary settings={settings} modules={modules} />
            <div className="wizard-actions">
              <button type="button" onClick={() => setShowConfirmation(false)}>다시 확인</button>
              <button type="button" className="primary" disabled={!settings.schoolId} onClick={createClassroom}>확정하고 개설하기</button>
            </div>
          </section>
        </div>
      ) : null}

      {step === "complete" && (
        <section className="created-panel">
          <span>완료</span>
          <h1>경제교실이 열렸어요</h1>
          <p>입장 코드 {settings.classCode}로 학생들이 접속할 수 있어요.</p>
          {precheck ? <SetupPrecheckSummary result={precheck} /> : <p className="contract-warning">공통 점검 결과를 받지 못했습니다. 모듈 활성화 전에 다시 점검해주세요.</p>}
          <div className="next-task-list">
            <button type="button" onClick={() => onDone("roster")}>학생 명단 등록하기</button>
            <button type="button" onClick={() => onDone("plan")}>운영 계획 세우기</button>
            <button type="button" onClick={() => onDone("overview")}>대시보드로 이동</button>
          </div>
          <button type="button" className="text-action" onClick={() => setStep("modules")}>모듈 다시 보기</button>
        </section>
      )}
    </main>
  );
}

function ModuleDependencyPanel({
  moduleName,
  settings,
  modules
}: Readonly<{ moduleName: string; settings: ClassSettings; modules: ModuleRow[] }>) {
  const requiredModules = getRequiredModules(moduleName);
  const isOpen = modules.some((module) => module.name === moduleName && module.status !== "미선택");

  return (
    <aside className="module-help-panel">
      <span>{isOpen ? "선택됨 · 활성화 전" : "선택 전"}</span>
      <h2>{moduleName}</h2>
      <p>{moduleDescriptions[moduleName]}</p>
      <div>
        <b>함께 사용되는 기능</b>
        {requiredModules.length > 0 ? (
          <ul>
            {requiredModules.map((dependency) => (
              <li key={dependency}>
                <strong>{dependency}</strong>
                <small>{moduleDescriptions[dependency]}</small>
              </li>
            ))}
          </ul>
        ) : (
          <p>별도 추가 기능 없이 사용할 수 있어요.</p>
        )}
      </div>
      <dl>
        <div><dt>교실</dt><dd>{settings.className}</dd></div>
        <div><dt>화폐</dt><dd>{settings.currency}</dd></div>
      </dl>
    </aside>
  );
}

function Stepper({ active }: Readonly<{ active: CreateStep }>) {
  const steps: Array<[CreateStep, string]> = [["basic", "기본 정보"], ["period", "기간 설정"], ["modules", "모듈 선택"], ["complete", "개설 완료"]];

  return (
    <ol className="stepper">
      {steps.map(([key, label]) => <li key={key} className={active === key ? "active" : ""}>{label}</li>)}
    </ol>
  );
}

function CreationSummary({ settings, modules }: Readonly<{ settings: ClassSettings; modules: ModuleRow[] }>) {
  const openModules = modules
    .filter((module) => module.status !== "미선택" && selectableModuleNames.includes(module.name))
    .map((module) => module.name)
    .slice(0, 6);

  return (
    <aside className="creation-summary">
      <h2>개설 요약</h2>
      <dl>
        <div><dt>교실</dt><dd>{settings.className}</dd></div>
        <div><dt>학교</dt><dd>{settings.schoolName}</dd></div>
        <div><dt>기간</dt><dd>{settings.period}</dd></div>
        <div><dt>화폐</dt><dd>{settings.currency}</dd></div>
        <div><dt>모듈</dt><dd>{openModules.join(", ") || "선택 전"}</dd></div>
      </dl>
    </aside>
  );
}

function CoursePlanning({
  modules,
  setModules,
  addLog
}: Readonly<{
  modules: ModuleRow[];
  setModules: React.Dispatch<React.SetStateAction<ModuleRow[]>>;
  addLog: (message: string, tone?: LogTone) => void;
}>) {
  const [selectedCourse, setSelectedCourse] = useState("생활 경제 기초");
  const [schedule, setSchedule] = useState([
    { day: "1일차", lessonIds: ["salary", "tax"] },
    { day: "2일차", lessonIds: ["spending", "shop"] },
    { day: "3일차", lessonIds: ["saving", "investment"] }
  ]);
  const [expandedDays, setExpandedDays] = useState<Set<number>>(new Set([0]));
  const openModules = modules.filter(
    (module) => module.status === "사용 중" && STUDENT_ACCESS_MODULE_NAMES.includes(module.name)
  );

  const courses = [
    { name: "생활 경제 기초", description: "역할·급여, 소득세, 상점으로 돈의 흐름을 익혀요.", modules: ["역할·급여", "소득세", "상점"] },
    { name: "저축과 투자", description: "예금과 투자의 차이를 선택 활동으로 배워요.", modules: ["예금·적금", "투자"] },
    { name: "우리 반 경제 공동체", description: "거래와 공동기금 투표까지 함께 운영해요.", modules: ["개인 거래", "공공 예산/투표"] }
  ];
  const lessonOptions = [
    { id: "salary", title: "월급과 실수령액", module: "역할·급여", minutes: "15분" },
    { id: "tax", title: "세금과 공동기금", module: "소득세", minutes: "10분" },
    { id: "spending", title: "합리적인 소비", module: "상점", minutes: "15분" },
    { id: "shop", title: "우리 반 상점 운영", module: "상점", minutes: "20분" },
    { id: "saving", title: "예금과 적금", module: "예금·적금", minutes: "15분" },
    { id: "investment", title: "단기·장기 투자", module: "투자", minutes: "20분" },
    { id: "trade", title: "친구와 안전하게 거래하기", module: "개인 거래", minutes: "15분" },
    { id: "budget", title: "공동기금 사용처 정하기", module: "공공 예산/투표", minutes: "20분" },
    { id: "quiz", title: "경제 개념 확인 퀴즈", module: "퀴즈", minutes: "10분" }
  ];

  function applyCourse(courseName: string, moduleNames: string[]) {
    setSelectedCourse(courseName);
    addLog(`${courseName} 추천안을 선택했습니다. 코스 설정 저장 API가 구현되기 전에는 기존 모듈을 변경하지 않습니다.`, "warning");
  }

  function togglePermission(moduleName: string) {
    setModules((items) => items.map((item) => item.name === moduleName
      ? { ...item, studentState: item.studentState === "자율 이용" ? "교사 수업 중 이용" : "자율 이용" }
      : item));
  }

  function toggleDay(index: number) {
    setExpandedDays((current) => {
      const next = new Set(current);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return next;
    });
  }

  function toggleLesson(dayIndex: number, lessonId: string) {
    setSchedule((items) => items.map((item, index) => {
      if (index !== dayIndex) return item;
      const lessonIds = item.lessonIds.includes(lessonId)
        ? item.lessonIds.filter((id) => id !== lessonId)
        : [...item.lessonIds, lessonId];
      return { ...item, lessonIds };
    }));
  }

  return (
    <div className="page-flow plan-page">
      <PlainSection title="추천 운영안">
        <div className="course-options">
          {courses.map((course) => (
            <button
              type="button"
              key={course.name}
              className={selectedCourse === course.name ? "selected" : ""}
              onClick={() => applyCourse(course.name, course.modules)}
            >
              <strong>{course.name}</strong>
              <span>{course.description}</span>
              <small>{course.modules.join(" · ")}</small>
            </button>
          ))}
        </div>
      </PlainSection>

      <PlainSection title="운영 일정">
        <div className="schedule-list">
          {schedule.map((item, index) => (
            <div className={`schedule-day ${expandedDays.has(index) ? "expanded" : ""}`} key={item.day}>
              <button
                type="button"
                className="schedule-summary"
                aria-expanded={expandedDays.has(index)}
                onClick={() => toggleDay(index)}
              >
                <b>{item.day}</b>
                <span>
                  {item.lessonIds.length > 0
                    ? item.lessonIds.map((lessonId) => lessonOptions.find((lesson) => lesson.id === lessonId)?.title).filter(Boolean).join(", ")
                    : "수업 내용을 선택해주세요"}
                </span>
                <small>{item.lessonIds.length}개 선택</small>
                <i aria-hidden="true">▾</i>
              </button>
              {expandedDays.has(index) ? (
                <div className="lesson-picker">
                  {lessonOptions.map((lesson) => (
                    <label key={lesson.id}>
                      <input
                        type="checkbox"
                        checked={item.lessonIds.includes(lesson.id)}
                        onChange={() => toggleLesson(index, lesson.id)}
                      />
                      <span>
                        <strong>{lesson.title}</strong>
                        <small>{lesson.module} · {lesson.minutes}</small>
                      </span>
                    </label>
                  ))}
                </div>
              ) : null}
            </div>
          ))}
          <button
            type="button"
            className="line-add-button"
            onClick={() => {
              const nextIndex = schedule.length;
              setSchedule((items) => [...items, { day: `${items.length + 1}일차`, lessonIds: [] }]);
              setExpandedDays((current) => new Set([...current, nextIndex]));
            }}
          >
            + 수업일 추가
          </button>
        </div>
      </PlainSection>

      <PlainSection title="학생 메뉴 이용 방식">
        <div className="permission-list">
          <div className="permission-head"><span>기능</span><span>학생 메뉴</span><span>이용 방식</span></div>
          {openModules.map((module) => (
            <div className="permission-row" key={module.name}>
              <strong>{module.name}</strong>
              <span>표시</span>
              <button
                type="button"
                className={module.studentState === "자율 이용" ? "autonomous" : "guided"}
                onClick={() => togglePermission(module.name)}
              >
                {module.studentState === "자율 이용" ? "자율 이용" : "수업 중 이용"}
              </button>
            </div>
          ))}
          <button type="button" className="primary-save" onClick={() => addLog("운영 일정과 학생 이용 권한을 저장했어요.")}>운영 계획 저장</button>
        </div>
      </PlainSection>
    </div>
  );
}

function Overview({
  mode,
  modules,
  students,
  approvals
}: Readonly<{ mode: string; modules: ModuleRow[]; students: StudentRow[]; approvals: ApprovalRow[] }>) {
  const summaryRows = [
    ["학급 코드", "ABCD2345", ""],
    ["운영 상태", mode, ""],
    ["학생", `${students.length}명`, ""],
    ["승인 대기", `${approvals.filter((item) => item.status.includes("대기") || item.status.includes("확인")).length}건`, ""]
  ];

  return (
    <div className="page-flow">
      <PlainSection title="오늘 운영 요약">
        <DefinitionList rows={summaryRows} />
      </PlainSection>
      <PlainSection title="열린 기능">
        <DataTable
          headers={["기능", "상태", "관리"]}
          rows={modules
            .filter((module) => selectableModuleNames.includes(module.name))
            .map((module) => [module.name, module.status, module.teacherTodo])}
        />
      </PlainSection>
    </div>
  );
}

function Setup({
  settings,
  modules,
  students,
  addLog,
  runAction,
  setModules
}: Readonly<{
  settings: ClassSettings;
  setSettings: React.Dispatch<React.SetStateAction<ClassSettings>>;
  modules: ModuleRow[];
  students: StudentRow[];
  addLog: (message: string, tone?: LogTone) => void;
  runAction: RunAction;
  setModules: React.Dispatch<React.SetStateAction<ModuleRow[]>>;
}>) {
  const selectedModules = modules.filter((module) => module.status !== "미선택");

  return (
    <div className="page-flow">
      <PlainSection title="학급 연결 상태">
        <div className="class-create-panel">
          <div>
            <span>수업방</span>
            <strong>{settings.className}</strong>
          </div>
          <div>
            <span>입장 코드</span>
            <strong>{settings.classCode}</strong>
          </div>
          <div><span>백엔드 ID</span><strong>{settings.classroomId || "연결 전"}</strong></div>
        </div>
      </PlainSection>
      <PlainSection title="학급 정보">
        <DefinitionList rows={[
          ["운영 기간", `${settings.startDate} ~ ${settings.endDate}`, settings.period],
          ["화폐", settings.currency, "1단위 금액 사용 가능"],
          ["선택 모듈", `${selectedModules.length}개`, "필수 모듈 없음"]
        ]} />
        <p className="contract-warning">학급 기본 설정 변경 API는 아직 백엔드에 없어 이 화면에서는 조회만 제공합니다.</p>
      </PlainSection>
      <ModuleActivationPanel
        settings={settings}
        modules={modules}
        students={students}
        addLog={addLog}
        runAction={runAction}
        setModules={setModules}
      />
    </div>
  );
}

function Roster({
  settings,
  setSettings,
  modules,
  students,
  setStudents,
  runAction,
  addLog
}: Readonly<{
  settings: ClassSettings;
  setSettings: React.Dispatch<React.SetStateAction<ClassSettings>>;
  modules: ModuleRow[];
  students: StudentRow[];
  setStudents: React.Dispatch<React.SetStateAction<StudentRow[]>>;
  runAction: RunAction;
  addLog: (message: string, tone?: LogTone) => void;
}>) {
  const roleSalaryModule = modules.find((module) => module.code === "ROLE_SALARY");
  const roleSalaryActive = roleSalaryModule?.status === "사용 중";
  const [isAdding, setIsAdding] = useState(false);
  const [rosterSaved, setRosterSaved] = useState(false);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [draftStudent, setDraftStudent] = useState<StudentRow | null>(null);
  const [newStudent, setNewStudent] = useState<StudentRow>({
    attendance: String(students.length + 1),
    nickname: "",
    role: "",
    salary: "150",
    status: "정상"
  });

  function updateDraft(field: keyof StudentRow, value: string) {
    setDraftStudent((current) => current ? { ...current, [field]: value } : current);
  }

  function updateNewStudent(field: keyof StudentRow, value: string) {
    setNewStudent((current) => ({ ...current, [field]: value }));
  }

  function startEdit(index: number) {
    setEditingIndex(index);
    setDraftStudent(students[index]);
  }

  function cancelEdit() {
    setEditingIndex(null);
    setDraftStudent(null);
  }

  function saveEdit(index: number) {
    if (!draftStudent) return;

    setStudents((items) => items.map((item, itemIndex) => itemIndex === index ? draftStudent : item));
    cancelEdit();
    addLog(`${draftStudent.nickname} 학생 정보를 수정했어요.`);
  }

  function addStudent() {
    const trimmedNickname = newStudent.nickname.trim();
    const trimmedRole = newStudent.role.trim();

    if (!trimmedNickname || (roleSalaryActive && !trimmedRole)) {
      addLog(roleSalaryActive ? "학생 이름과 역할을 입력해야 추가할 수 있어요." : "학생 이름을 입력해야 추가할 수 있어요.", "warning");
      return;
    }

    const studentToAdd = {
      ...newStudent,
      attendance: newStudent.attendance.trim() || String(students.length + 1),
      nickname: trimmedNickname,
      role: roleSalaryActive ? trimmedRole : "",
      salary: roleSalaryActive ? newStudent.salary.trim() || "150" : "0",
      status: "정상"
    };

    setStudents((items) => [...items, studentToAdd]);
    setNewStudent({
      attendance: String(students.length + 2),
      nickname: "",
      role: "",
      salary: "150",
      status: "정상"
    });
    setIsAdding(false);
    addLog(`${studentToAdd.nickname} 학생을 추가했어요.`);
  }

  const rosterBody = {
    expected_policy_version: roleSalaryActive ? roleSalaryModule?.policyVersion : null,
    expected_state_version: settings.stateVersion,
    screen_id: "SCR-WEB-006",
    ui_action_id: "UIA-CLS-003",
    client_occurred_at: new Date().toISOString(),
    students: students.map((student, index) => ({
      student_id: null,
      attendance_number: Number(student.attendance),
      nickname: student.nickname,
      role_code: roleSalaryActive ? `ROLE_${index + 1}` : null
    })),
    salary_roles: roleSalaryActive ? students.map((student, index) => ({
      role_code: `ROLE_${index + 1}`,
      name: `${student.role} ${index + 1}`,
      gross_amount: Number(student.salary),
      deductions: {},
      sort_order: index + 1
    })) : null
  };
  const grossTotal = students.reduce((sum, student) => sum + Number(student.salary), 0);
  const taxTotal = Math.floor(grossTotal * (Number(settings.incomeTax) / 100));
  const netTotal = grossTotal - taxTotal;

  return (
    <div className="page-flow">
      <PlainSection title="학생 명단">
        <div className="roster-board">
          <div className="roster-toolbar">
            <span>{students.length}명 등록됨</span>
            <button type="button" disabled={rosterSaved} onClick={() => setIsAdding(true)}>학생 추가</button>
          </div>

          {isAdding && (
            <div className="student-editor">
              <label>
                번호
                <input value={newStudent.attendance} onChange={(event) => updateNewStudent("attendance", event.target.value)} />
              </label>
              <label>
                이름
                <input value={newStudent.nickname} onChange={(event) => updateNewStudent("nickname", event.target.value)} placeholder="학생 이름" />
              </label>
              {roleSalaryActive ? <label>
                역할
                <input value={newStudent.role} onChange={(event) => updateNewStudent("role", event.target.value)} placeholder="예: 은행 매니저" />
              </label> : null}
              {roleSalaryActive ? <label>
                월급
                <input value={newStudent.salary} onChange={(event) => updateNewStudent("salary", event.target.value)} />
              </label> : null}
              <div className="row-actions">
                <button type="button" onClick={addStudent}>추가</button>
                <button className="ghost" type="button" onClick={() => setIsAdding(false)}>취소</button>
              </div>
            </div>
          )}

          <table className="student-table">
            <thead>
              <tr>
                <th>번호</th>
                <th>이름</th>
                {roleSalaryActive ? <th>역할</th> : null}
                {roleSalaryActive ? <th>월급</th> : null}
                <th>상태</th>
                <th aria-label="관리" />
              </tr>
            </thead>
            <tbody>
              {students.map((student, index) => {
                const activeDraft = editingIndex === index ? draftStudent : null;

                return (
                  <tr key={`${student.attendance}-${student.nickname}`}>
                    {activeDraft ? (
                      <>
                        <td><input value={activeDraft.attendance} onChange={(event) => updateDraft("attendance", event.target.value)} /></td>
                        <td><input value={activeDraft.nickname} onChange={(event) => updateDraft("nickname", event.target.value)} /></td>
                        {roleSalaryActive ? <td><input value={activeDraft.role} onChange={(event) => updateDraft("role", event.target.value)} /></td> : null}
                        {roleSalaryActive ? <td><input value={activeDraft.salary} onChange={(event) => updateDraft("salary", event.target.value)} /></td> : null}
                        <td>{activeDraft.status}</td>
                        <td>
                          <div className="row-actions">
                            <button type="button" onClick={() => saveEdit(index)}>저장</button>
                            <button className="ghost" type="button" onClick={cancelEdit}>취소</button>
                          </div>
                        </td>
                      </>
                    ) : (
                      <>
                        <td>{student.attendance}</td>
                        <td>{student.nickname}</td>
                        {roleSalaryActive ? <td>{student.role}</td> : null}
                        {roleSalaryActive ? <td>{student.salary}{settings.currency}</td> : null}
                        <td>{student.status}</td>
                        <td>
                          <div className="row-actions">
                            <button type="button" disabled={rosterSaved} onClick={() => startEdit(index)}>수정</button>
                          </div>
                        </td>
                      </>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </PlainSection>
      {roleSalaryActive ? <PlainSection title="급여">
        <div className="payroll-panel">
          <div className="payroll-total">
            <span>이번 지급액</span>
            <strong>{netTotal}{settings.currency}</strong>
          </div>
          <div className="payroll-breakdown">
            <div><span>총 급여</span><b>{grossTotal}{settings.currency}</b></div>
            <div><span>소득세</span><b>-{taxTotal}{settings.currency}</b></div>
            <div><span>학생 수령액</span><b>{netTotal}{settings.currency}</b></div>
          </div>
        </div>
      </PlainSection> : <PlainSection title="직업 없이 운영">
        <p className="empty-copy">역할·급여 모듈을 선택하지 않았으므로 역할 배정과 급여명세서·급여 지급 기능을 표시하지 않습니다.</p>
      </PlainSection>}
      <PlainSection title="처리">
        <ActionLine
          label="명단 저장"
          value={`${students.length}명 반영`}
          action="저장"
          disabled={rosterSaved || !settings.classroomId || students.length === 0 || (roleSalaryActive && roleSalaryModule?.policyVersion == null)}
          onClick={() => runAction<{ classroom_id: string; student_count: number; salary_role_count: number; state_version: number }>(
            "명단·급여 저장",
            `/classrooms/${settings.classroomId}/roster-and-salaries`,
            { method: "PUT", body: JSON.stringify(rosterBody) },
            (response) => {
              setSettings((current) => ({ ...current, stateVersion: response.state_version }));
              setRosterSaved(true);
            }
          )}
        />
        {rosterSaved ? <p className="contract-warning">명단 조회 API가 없어 학생 ID를 복원할 수 없습니다. 중복 생성을 막기 위해 현재 세션에서는 재수정을 잠갔습니다.</p> : null}
        {roleSalaryActive ? <p className="contract-warning">학생 ID를 돌려주는 명단 조회 API가 아직 없어, 잘못된 대상에게 지급되지 않도록 급여 지급 연결은 제외했습니다.</p> : null}
      </PlainSection>
    </div>
  );
}

function ShopAdmin({
  products,
  setProducts,
  addLog
}: Readonly<{
  products: ProductRow[];
  setProducts: React.Dispatch<React.SetStateAction<ProductRow[]>>;
  addLog: (message: string, tone?: LogTone) => void;
}>) {
  const [preview, setPreview] = useState("");
  const [fileName, setFileName] = useState("선택된 사진 없음");
  const [name, setName] = useState("연필 교환권");
  const [price, setPrice] = useState("30");
  const [stock, setStock] = useState("12");
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [draftProduct, setDraftProduct] = useState<ProductRow | null>(null);

  function handleImageChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      setPreview("");
      setFileName("선택된 사진 없음");
      return;
    }

    setFileName(file.name);
    const reader = new FileReader();
    reader.onload = () => setPreview(String(reader.result));
    reader.readAsDataURL(file);
  }

  function addProduct() {
    setProducts((items) => [{ name, price, stock, imageName: preview ? fileName : "", imageSrc: preview }, ...items]);
    setName("");
    setPrice("");
    setStock("");
    setPreview("");
    setFileName("선택된 사진 없음");
    addLog("상품을 등록했어요.");
  }

  function startEdit(index: number) {
    setEditingIndex(index);
    setDraftProduct(products[index]);
  }

  function updateDraft(field: keyof ProductRow, value: string) {
    setDraftProduct((current) => current ? { ...current, [field]: value } : current);
  }

  function saveProduct(index: number) {
    if (!draftProduct) return;
    setProducts((items) => items.map((item, itemIndex) => itemIndex === index ? draftProduct : item));
    setEditingIndex(null);
    setDraftProduct(null);
    addLog("상품 정보를 수정했어요.");
  }

  function removeProduct(index: number) {
    setProducts((items) => items.filter((_, itemIndex) => itemIndex !== index));
    addLog("상품을 내렸어요.");
  }

  return (
    <div className="page-flow">
      <PlainSection title="상품 등록">
        <div className="shop-admin-form">
          <label>상품명<input value={name} onChange={(event) => setName(event.target.value)} /></label>
          <label>가격<input value={price} onChange={(event) => setPrice(event.target.value)} /></label>
          <label>재고<input value={stock} onChange={(event) => setStock(event.target.value)} /></label>
          <label className="image-upload">
            상품 사진
            <input type="file" accept="image/*" onChange={handleImageChange} />
            <span>{fileName}</span>
          </label>
          <div className="image-preview">
            {preview ? <img src={preview} alt="상품 사진 미리보기" /> : <span>사진 미리보기</span>}
          </div>
          <button className="wide-action" onClick={addProduct}>상품 올리기</button>
        </div>
      </PlainSection>
      <PlainSection title="상품 관리">
        <div className="product-list">
          {products.map((product, index) => {
            const activeDraft = editingIndex === index ? draftProduct : null;

            return (
              <div className="product-row" key={`${product.name}-${index}`}>
                <div className="product-thumb">
                  {product.imageSrc ? <img src={product.imageSrc} alt={product.name} /> : <span>사진 없음</span>}
                </div>
                {activeDraft ? (
                  <>
                    <input value={activeDraft.name} onChange={(event) => updateDraft("name", event.target.value)} />
                    <input value={activeDraft.price} onChange={(event) => updateDraft("price", event.target.value)} />
                    <input value={activeDraft.stock} onChange={(event) => updateDraft("stock", event.target.value)} />
                    <div className="row-actions">
                      <button type="button" onClick={() => saveProduct(index)}>저장</button>
                      <button className="ghost" type="button" onClick={() => setEditingIndex(null)}>취소</button>
                    </div>
                  </>
                ) : (
                  <>
                    <div>
                      <b>{product.name}</b>
                      <span>{product.price}비빔 · 재고 {product.stock}</span>
                    </div>
                    <div className="row-actions product-actions">
                      <button type="button" onClick={() => startEdit(index)}>수정</button>
                      <button className="ghost" type="button" onClick={() => removeProduct(index)}>내리기</button>
                    </div>
                  </>
                )}
              </div>
            );
          })}
        </div>
      </PlainSection>
    </div>
  );
}

function Control({
  classroomId,
  mode,
  setMode,
  modules,
  setModules,
  runAction,
  addLog
}: Readonly<{
  classroomId: string;
  mode: string;
  setMode: React.Dispatch<React.SetStateAction<string>>;
  modules: ModuleRow[];
  setModules: React.Dispatch<React.SetStateAction<ModuleRow[]>>;
  runAction: RunAction;
  addLog: (message: string, tone?: LogTone) => void;
}>) {
  const [lessonMaterials, setLessonMaterials] = useState<LessonMaterial[]>(initialLessonMaterials);
  const [editingLessonId, setEditingLessonId] = useState<string | null>(null);
  const [draftLesson, setDraftLesson] = useState<LessonMaterial | null>(null);

  function updateModule(name: string, status: string, teacherTodo: string) {
    setModules((items) => items.map((item) => item.name === name ? { ...item, status, teacherTodo } : item));
  }

  function startLessonEdit(material: LessonMaterial) {
    setEditingLessonId(material.id);
    setDraftLesson(material);
  }

  function updateLessonDraft(field: keyof LessonMaterial, value: string) {
    setDraftLesson((current) => current ? { ...current, [field]: value } : current);
  }

  function saveLesson() {
    if (!draftLesson) return;
    setLessonMaterials((items) => items.map((item) => item.id === draftLesson.id ? draftLesson : item));
    setEditingLessonId(null);
    setDraftLesson(null);
    addLog("수업 자료를 수정했어요.");
  }

  return (
    <div className="page-flow">
      <PlainSection title="수업 준비">
        <div className="lesson-list">
          {lessonMaterials.map((material) => {
            const activeDraft = editingLessonId === material.id ? draftLesson : null;

            return (
              <div className="lesson-row" key={material.id}>
                {activeDraft ? (
                  <>
                    <input value={activeDraft.stage} onChange={(event) => updateLessonDraft("stage", event.target.value)} />
                    <input value={activeDraft.title} onChange={(event) => updateLessonDraft("title", event.target.value)} />
                    <input value={activeDraft.minutes} onChange={(event) => updateLessonDraft("minutes", event.target.value)} />
                    <select value={activeDraft.status} onChange={(event) => updateLessonDraft("status", event.target.value)}>
                      <option>준비 완료</option>
                      <option>수정 필요</option>
                    </select>
                    <div className="row-actions">
                      <button type="button" onClick={saveLesson}>저장</button>
                      <button className="ghost" type="button" onClick={() => setEditingLessonId(null)}>취소</button>
                    </div>
                  </>
                ) : (
                  <>
                    <span>{material.stage}</span>
                    <b>{material.title}</b>
                    <span>{material.minutes}분</span>
                    <span>{material.status}</span>
                    <div className="row-actions">
                      <button type="button" onClick={() => startLessonEdit(material)}>수정</button>
                    </div>
                  </>
                )}
              </div>
            );
          })}
        </div>
      </PlainSection>
      <PlainSection title="수업 시작">
        <ActionLine
          label="현재 모드"
          value={mode}
          action={mode === "자율 활동" ? "수업 시작" : "자율 전환"}
          onClick={() => runAction(
            "운영 모드 전환",
            `/classrooms/${classroomId}/access-mode-commands`,
            {
              method: "POST",
              body: JSON.stringify({
                expected_state_version: 0,
                command: mode === "자율 활동" ? "CLOSE" : "OPEN",
                duration_minutes: 40,
                client_occurred_at: new Date().toISOString()
              })
            },
            () => setMode((current) => current === "자율 활동" ? "선생님 수업" : "자율 활동")
          )}
        />
        <ActionLine
          label="지도 활동 시작"
          value="준비한 수업 자료를 학생 화면에 열기"
          action="시작"
          onClick={() => runAction(
            "지도 활동 시작",
            `/classrooms/${classroomId}/guided-activities/${crypto.randomUUID()}/start-commands`,
            { method: "POST", body: JSON.stringify({ expected_state_version: 0, participant_ids: [], public_fields: ["nickname", "wallet"] }) },
            () => {
              setMode("선생님 수업");
              addLog("수업 자료 화면을 학생에게 열었어요.");
            }
          )}
        />
      </PlainSection>
      <PlainSection title="수업 중 기능">
        <ActionLine
          label="예금 상품"
          value="3일 예금"
          action="준비"
          onClick={() => runAction(
            "예금 상품 생성",
            `/classrooms/${classroomId}/financial-product-commands`,
            {
              method: "POST",
              body: JSON.stringify({
                expectedStateVersion: 0,
                command: "CREATE",
                productType: "DEPOSIT",
                name: "3일 예금",
                termUnits: 3,
                interestRateBps: 1000,
                earlyTerminationRateBps: 100,
                minimumAmount: 30,
                maximumAmount: 300,
                expectedProductVersion: 0
              })
            },
            () => updateModule("은행", "상품 준비", "3일 예금 생성")
          )}
        />
        <ActionLine
          label="경제 퀴즈"
          value="소득세 기본 문항"
          action="준비"
          onClick={() => runAction(
            "퀴즈 문항 생성",
            `/classrooms/${classroomId}/quiz-question-commands`,
            {
              method: "POST",
              body: JSON.stringify({
                expectedStateVersion: 0,
                command: "CREATE",
                contentType: "ECONOMY",
                questionType: "MULTIPLE_CHOICE",
                prompt: "소득세는 어디에 모이나요?",
                options: [
                  { optionKey: "A", optionText: "내 캐릭터 보관함", isCorrect: false, sortOrder: 1 },
                  { optionKey: "B", optionText: "우리 반 공동기금", isCorrect: true, sortOrder: 2 }
                ],
                explanation: "소득세는 공동기금으로 모여 함께 사용할 수 있어요.",
                expectedQuestionVersion: 0
              })
            },
            () => updateModule("퀴즈", "문항 생성", "문항 1개 추가")
          )}
        />
        <ActionLine
          label="투표 열기"
          value="공동기금 사용처 투표"
          action="오픈"
          onClick={() => runAction(
            "투표 오픈",
            `/classrooms/${classroomId}/votes/${crypto.randomUUID()}/commands`,
            {
              method: "POST",
              body: JSON.stringify({
                expected_state_version: 0,
                command: "CREATE",
                title: "공동기금 사용처",
                description: "우리 반 공동기금을 어디에 쓸까요?",
                public_display_enabled: true,
                options: [
                  { label: "학급 간식", description: "간식 사기", budget_amount: 120 },
                  { label: "보드게임", description: "수업용 게임", budget_amount: 180 }
                ]
              })
            },
            () => updateModule("퀴즈", "투표 진행", "공동기금 투표 열림")
          )}
        />
      </PlainSection>
      <PlainSection title="학생에게 열려 있는 기능">
        <DataTable headers={["기능", "상태", "관리"]} rows={modules.map((module) => [module.name, module.status, module.teacherTodo])} />
      </PlainSection>
    </div>
  );
}

function Approvals({
  classroomId,
  approvals,
  setApprovals,
  runAction,
  addLog
}: Readonly<{
  classroomId: string;
  approvals: ApprovalRow[];
  setApprovals: React.Dispatch<React.SetStateAction<ApprovalRow[]>>;
  runAction: RunAction;
  addLog: (message: string, tone?: LogTone) => void;
}>) {
  function updateApproval(id: string, status: string) {
    setApprovals((items) => items.map((item) => item.id === id ? { ...item, status } : item));
  }

  return (
    <div className="page-flow">
      <PlainSection title="승인 대기">
        <div className="approval-list">
          {approvals.map((item) => (
            <div className="approval-row" key={item.id}>
              <div>
                <b>{item.type}</b>
                <span>{item.target} · {item.amount} · {item.status}</span>
              </div>
              <div>
                <button onClick={() => {
                  if (item.type === "거래") {
                    runAction(
                      "거래 승인",
                      `/classrooms/${classroomId}/trades/${crypto.randomUUID()}/teacher-decisions`,
                      { method: "POST", body: JSON.stringify({ expectedStateVersion: 0, decision: "APPROVE", reason: "교사 확인" }) },
                      () => updateApproval(item.id, "승인 완료")
                    );
                    return;
                  }
                  updateApproval(item.id, "처리 완료");
                  addLog(`${item.type} 항목을 처리했어요.`);
                }}>승인</button>
                <button onClick={() => updateApproval(item.id, "거절")}>거절</button>
              </div>
            </div>
          ))}
        </div>
      </PlainSection>
      <PlainSection title="운영 기록">
        <ActionLine
          label="리포트 조회"
          value="잔액, 지출, 예금, 퀴즈 참여"
          action="조회"
          onClick={() => {
            const to = new Date();
            const from = new Date(Date.now() - 24 * 60 * 60 * 1000);
            runAction(
              "운영 리포트 조회",
              `/classrooms/${classroomId}/reports?from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`,
              { method: "GET" },
              () => addLog("리포트 조회 결과를 요청했어요.")
            );
          }}
        />
      </PlainSection>
    </div>
  );
}

function DefinitionList({ rows }: Readonly<{ rows: string[][] }>) {
  return (
    <dl className="definition-list">
      {rows.map(([label, value, note]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>
            <strong>{value}</strong>
            {note ? <span>{note}</span> : null}
          </dd>
        </div>
      ))}
    </dl>
  );
}

function DataTable({ headers, rows }: Readonly<{ headers: string[]; rows: string[][] }>) {
  return (
    <table className="data-table">
      <thead>
        <tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.join("-")}>
            {row.map((cell, index) => <td key={`${cell}-${index}`}>{cell}</td>)}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ActionLine({
  label,
  value,
  action,
  disabled = false,
  onClick
}: Readonly<{ label: string; value: string; action: string; disabled?: boolean; onClick: () => void }>) {
  return (
    <div className="action-line">
      <div>
        <b>{label}</b>
        <span>{value}</span>
      </div>
      <button disabled={disabled} onClick={onClick}>{action}</button>
    </div>
  );
}
