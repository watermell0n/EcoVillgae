"use client";

import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from "react";
import {
  CalendarRange,
  Bot,
  ChevronLeft,
  ClipboardCheck,
  LayoutDashboard,
  LogOut,
  MonitorPlay,
  School,
  Search,
  Settings,
  ShoppingBag,
  Trash2,
  UserPlus,
  Users,
  X
} from "lucide-react";
import {
  ApiError,
  apiRequest,
  BootstrapClassroom,
  CreateClassroomResponse,
  createTeacherPasswordSession,
  createTeacherSession,
  getClassroomServerState,
  getTeacherBootstrap,
  hasTeacherAccessToken,
  logoutTeacher,
  registerTeacher,
  restoreTeacherSession
} from "./api";
import { consumeTeacherOidcCallback, hasOidcCallback } from "./oidc";
import { AiWorkspace } from "./ai/AiWorkspace";
import { TeacherRealtimeClient } from "./realtime";

type ViewKey = "overview" | "plan" | "setup" | "roster" | "shop" | "control" | "approvals" | "ai";
type TeacherStage = "login" | "classrooms" | "create" | "console";
type CreateStep = "basic" | "period" | "complete";
type LogTone = "success" | "warning" | "error";

type LogEntry = {
  id: string;
  tone: LogTone;
  message: string;
};

type StudentRow = {
  attendance: string;
  nickname: string;
  role: string;
  salary: string;
  status: string;
};

type ModuleRow = {
  name: string;
  status: string;
  studentState: string;
  teacherTodo: string;
  resourceId?: string;
  code?: string;
  stateVersion?: number;
  version?: number;
  config?: Record<string, unknown>;
};

type ProductRow = {
  name: string;
  price: string;
  stock: string;
  imageName: string;
  imageSrc: string;
  resourceId?: string;
  version?: number;
  stateVersion?: number;
  kind?: "PHYSICAL" | "DIGITAL";
  vatRateBps?: number;
};

type AiSettingsSnapshot = {
  enabled: boolean;
  reason: string;
  version: number;
  stateVersion: number;
};

type ApprovalRow = {
  id: string;
  type: string;
  target: string;
  amount: string;
  status: string;
  tradeId?: string;
  stateVersion?: number;
  sample?: boolean;
};

type ClassSettings = {
  className: string;
  schoolName: string;
  grade: string;
  classNumber: string;
  description: string;
  classCode: string;
  startDate: string;
  endDate: string;
  currency: string;
  period: string;
  incomeTax: string;
  studentCount: string;
};

type LessonMaterial = {
  id: string;
  title: string;
  stage: string;
  minutes: string;
  status: string;
};

const views = [
  { key: "overview" as const, label: "운영 현황", icon: LayoutDashboard },
  { key: "plan" as const, label: "운영 계획", icon: CalendarRange },
  { key: "setup" as const, label: "학급 설정", icon: Settings },
  { key: "roster" as const, label: "학생·급여", icon: Users },
  { key: "shop" as const, label: "상점 상품", icon: ShoppingBag },
  { key: "control" as const, label: "수업 제어", icon: MonitorPlay },
  { key: "approvals" as const, label: "승인·정산", icon: ClipboardCheck },
  { key: "ai" as const, label: "AI 설정", icon: Bot }
];

const initialModules: ModuleRow[] = [
  { name: "지갑", status: "열림", studentState: "기본 기능", teacherTodo: "자동 적용" },
  { name: "상점", status: "열림", studentState: "학생 구매 가능", teacherTodo: "상품 2개 판매 중" },
  { name: "은행", status: "열림", studentState: "예금 가입 가능", teacherTodo: "예금 상품 관리" },
  { name: "투자", status: "결과 대기", studentState: "단기·장기 투자 확인", teacherTodo: "시장 결과 준비" },
  { name: "거래", status: "승인 필요", studentState: "친구 거래 가능", teacherTodo: "대기 2건" },
  { name: "퀴즈", status: "준비됨", studentState: "선생님 시작 시 참여", teacherTodo: "문항 3개" }
];

const studentSeed = [
  ["김하늘", "시장 분석가", "150", "정상"], ["박서윤", "은행 매니저", "150", "정상"],
  ["이도윤", "상점 매니저", "130", "급여 확인"], ["최민지", "마을 기록관", "120", "비밀번호 재설정"],
  ["정서준", "시장 분석가", "150", "정상"], ["한지우", "은행 매니저", "150", "정상"],
  ["윤채원", "상점 매니저", "130", "정상"], ["임준호", "마을 기록관", "120", "정상"],
  ["오수아", "투자 분석가", "140", "정상"], ["강민재", "세금 담당관", "140", "정상"],
  ["송예린", "예산 기획자", "140", "정상"], ["조현우", "거래 중재자", "130", "정상"],
  ["백나은", "투자 분석가", "140", "정상"], ["신도현", "세금 담당관", "140", "정상"],
  ["권유진", "예산 기획자", "140", "정상"], ["문시우", "거래 중재자", "130", "정상"],
  ["장하윤", "시장 분석가", "150", "정상"], ["안준서", "은행 매니저", "150", "정상"],
  ["류다인", "상점 매니저", "130", "정상"], ["고은찬", "마을 기록관", "120", "정상"],
  ["배서아", "투자 분석가", "140", "정상"], ["노태윤", "세금 담당관", "140", "정상"],
  ["차유나", "예산 기획자", "140", "정상"], ["황지호", "거래 중재자", "130", "정상"]
] as const;

const initialStudents: StudentRow[] = studentSeed.map(([nickname, role, salary, status], index) => ({
  attendance: String(index + 1),
  nickname,
  role,
  salary,
  status
}));

const initialProducts: ProductRow[] = [
  { name: "연필 교환권", price: "30", stock: "12", imageName: "", imageSrc: "" },
  { name: "간식 우선권", price: "50", stock: "4", imageName: "", imageSrc: "" }
];

const initialApprovals: ApprovalRow[] = [
  { id: "trade-1", type: "거래", target: "김하늘 → 박서윤", amount: "30비빔", status: "교사 승인 대기", sample: true },
  { id: "shop-1", type: "상점", target: "숙제 패스권", amount: "2,000비빔", status: "권리 상품 확인", sample: true },
  { id: "vote-1", type: "투표", target: "공동기금 사용처", amount: "340비빔", status: "결과 정산 대기", sample: true }
];

const courseTemplates = [
  {
    name: "생활 경제 기초",
    description: "월급을 받고 세금을 낸 뒤 합리적으로 소비하는 흐름을 익혀요.",
    modules: ["지갑", "월급", "세금", "상점", "퀴즈"],
    schedule: [
      { day: "1일차", lessonIds: ["salary", "tax"] },
      { day: "2일차", lessonIds: ["spending", "shop"] },
      { day: "3일차", lessonIds: ["quiz"] }
    ]
  },
  {
    name: "저축과 투자",
    description: "소득을 나누어 예금하고 투자 결과를 비교해요.",
    modules: ["지갑", "세금", "예금", "투자", "퀴즈"],
    schedule: [
      { day: "1일차", lessonIds: ["tax", "saving"] },
      { day: "2일차", lessonIds: ["investment"] },
      { day: "3일차", lessonIds: ["quiz"] }
    ]
  },
  {
    name: "우리 반 경제 공동체",
    description: "개인 거래를 경험하고 공동기금의 사용처를 함께 결정해요.",
    modules: ["지갑", "세금", "개인 거래", "공공 예산/투표", "퀴즈"],
    schedule: [
      { day: "1일차", lessonIds: ["trade"] },
      { day: "2일차", lessonIds: ["budget"] },
      { day: "3일차", lessonIds: ["quiz"] }
    ]
  }
];

const initialSettings: ClassSettings = {
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
  incomeTax: "10",
  studentCount: "25"
};

const initialLessonMaterials: LessonMaterial[] = [
  { id: "lesson-1", title: "월급과 실수령액", stage: "1교시", minutes: "15", status: "준비 완료" },
  { id: "lesson-2", title: "세금과 공동기금", stage: "1교시", minutes: "10", status: "준비 완료" },
  { id: "lesson-3", title: "예금과 투자 선택", stage: "2교시", minutes: "20", status: "수정 필요" }
];

const enabledModuleNames = ["지갑", "월급", "세금", "상점", "캐릭터", "개인 거래", "예금", "투자", "공공 예산/투표", "퀴즈"];
const selectableModuleNames = enabledModuleNames.filter((moduleName) => moduleName !== "지갑");
const studentAccessModuleNames = ["상점", "캐릭터", "개인 거래", "예금", "투자", "공공 예산/투표", "퀴즈"];

const moduleDependencies: Record<string, string[]> = {
  월급: ["지갑"],
  세금: ["지갑"],
  상점: ["지갑"],
  캐릭터: ["지갑", "상점"],
  "개인 거래": ["지갑"],
  예금: ["지갑", "세금"],
  투자: ["지갑", "세금"],
  "공공 예산/투표": ["지갑", "세금"],
  퀴즈: []
};

const moduleDescriptions: Record<string, string> = {
  지갑: "학생 잔액과 거래 원장을 관리하는 기본 모듈입니다.",
  월급: "역할별 급여를 지급하고 월급명세서를 생성합니다.",
  세금: "소득세, 부가세, 거래세를 공동기금으로 모읍니다.",
  상점: "학생이 상품을 구매하고 재고와 결제를 처리합니다.",
  캐릭터: "학생이 학급 화폐로 아이템을 사고 캐릭터를 꾸밉니다.",
  "개인 거래": "학생 간 제안, 동의, 교사 승인 거래를 처리합니다.",
  예금: "정기예금과 적금 가입, 이자 계산, 만기 처리를 합니다.",
  투자: "가상 투자 상품 선택과 손익 결과 확인을 제공합니다.",
  "공공 예산/투표": "공동기금 사용처를 정하고 익명 투표를 진행합니다.",
  퀴즈: "경제 퀴즈와 게임형/평가형 수업 활동을 진행합니다."
};

const periodModuleRecommendations: Record<string, string[]> = {
  "1회 체험": ["퀴즈"],
  "1주 프로젝트": ["월급", "세금", "상점", "예금", "퀴즈"],
  "1달 운영": ["월급", "세금", "상점", "캐릭터", "예금", "투자", "공공 예산/투표", "퀴즈"],
  "1학기 운영": selectableModuleNames
};

const moduleCodeByName: Record<string, string> = {
  월급: "ROLE_SALARY",
  세금: "INCOME_TAX",
  상점: "STORE",
  캐릭터: "CHARACTER",
  "개인 거래": "TRADE",
  예금: "DEPOSIT",
  투자: "INVESTMENT",
  "공공 예산/투표": "PUBLIC_FUND",
  퀴즈: "QUIZ_GAME"
};

function courseSizeFor(period: string) {
  return ({
    "1회 체험": "ONE_SESSION",
    "1주 프로젝트": "ONE_WEEK",
    "1달 운영": "ONE_MONTH",
    "1학기 운영": "SEMESTER"
  } as const)[period] ?? "ONE_WEEK";
}

function selectedModuleCodes(selectedModules: Set<string>) {
  const names = new Set<string>();
  for (const selectedModule of selectedModules) {
    names.add(selectedModule);
    getRequiredModules(selectedModule).forEach((requiredModule) => names.add(requiredModule));
  }
  return [...names]
    .map((name) => moduleCodeByName[name])
    .filter((code): code is string => Boolean(code));
}

function getRequiredModules(moduleName: string): string[] {
  const visited = new Set<string>();

  function visit(name: string) {
    for (const dependency of moduleDependencies[name] ?? []) {
      if (visited.has(dependency)) continue;
      visited.add(dependency);
      visit(dependency);
    }
  }

  visit(moduleName);
  return [...visited];
}

export default function TeacherConsole() {
  const [stage, setStage] = useState<TeacherStage>("login");
  const [createStep, setCreateStep] = useState<CreateStep>("basic");
  const [hasClassroom, setHasClassroom] = useState(false);
  const [hasDraft, setHasDraft] = useState(false);
  const [classrooms, setClassrooms] = useState<BootstrapClassroom[]>([]);
  const [schoolId, setSchoolId] = useState("");
  const [activeClassroomId, setActiveClassroomId] = useState("");
  const [activeStateVersion, setActiveStateVersion] = useState(0);
  const [teacherRoles, setTeacherRoles] = useState<string[]>([]);
  const [authBusy, setAuthBusy] = useState(true);
  const [authError, setAuthError] = useState("");
  const [view, setView] = useState<ViewKey>("overview");
  const [mode, setMode] = useState("자율 활동");
  const [modules, setModules] = useState<ModuleRow[]>(initialModules);
  const [students, setStudents] = useState<StudentRow[]>(initialStudents);
  const [products, setProducts] = useState<ProductRow[]>(initialProducts);
  const [aiSettings, setAiSettings] = useState<AiSettingsSnapshot>({
    enabled: true,
    reason: "",
    version: 0,
    stateVersion: 0
  });
  const [approvals, setApprovals] = useState<ApprovalRow[]>(initialApprovals);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [settings, setSettings] = useState<ClassSettings>(initialSettings);
  const title = useMemo(() => views.find((item) => item.key === view)?.label ?? "운영 현황", [view]);

  useEffect(() => {
    let cancelled = false;
    async function restore() {
      try {
        const bootstrap = hasOidcCallback()
          ? await createTeacherSession(await consumeTeacherOidcCallback()).then(() => getTeacherBootstrap())
          : await restoreTeacherSession();
        if (cancelled) return;
        enterClassrooms(bootstrap);
      } catch (error) {
        if (cancelled) return;
        if (hasOidcCallback() || (error instanceof ApiError && error.status !== 401)) {
          setAuthError(error instanceof Error ? error.message : "교사 로그인 상태를 확인하지 못했어요.");
        }
        setStage("login");
      } finally {
        if (!cancelled) setAuthBusy(false);
      }
    }
    void restore();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (stage !== "console" || !activeClassroomId || !hasTeacherAccessToken()) return;

    let refreshTimer: ReturnType<typeof setTimeout> | null = null;
    const refreshClassroom = () => {
      if (refreshTimer) clearTimeout(refreshTimer);
      refreshTimer = setTimeout(() => void openClassroom(activeClassroomId), 200);
    };
    const client = new TeacherRealtimeClient({
      subscriptions: [
        { channelId: "WS-CLS-001", resourceId: activeClassroomId },
        { channelId: "WS-TCH-001", resourceId: activeClassroomId },
        { channelId: "WS-ECO-001", resourceId: activeClassroomId }
      ],
      onEvent: (event) => {
        if (event.event_type === "classroom.app_mode.changed") {
          setMode(event.payload.mode === "GUIDED" ? "선생님 수업" : "자율 활동");
        }
        refreshClassroom();
      },
      onRecoveryRequired: refreshClassroom
    });
    client.start();
    return () => {
      if (refreshTimer) clearTimeout(refreshTimer);
      client.stop();
    };
  }, [stage, activeClassroomId]);

  function addLog(message: string, tone: LogTone = "success") {
    setLogs((items) => [{ id: crypto.randomUUID(), tone, message }, ...items].slice(0, 6));
  }

  function enterClassrooms(bootstrap: Awaited<ReturnType<typeof getTeacherBootstrap>>) {
    setClassrooms(bootstrap.classrooms);
    setSchoolId(bootstrap.schools[0]?.school_id ?? "");
    setTeacherRoles(bootstrap.roles);
    setHasClassroom(bootstrap.classrooms.length > 0);
    if (bootstrap.classrooms[0]) {
      setActiveClassroomId(bootstrap.classrooms[0].resource_id);
      setActiveStateVersion(bootstrap.classrooms[0].state_version ?? 0);
    }
    setAuthError("");
    setStage("classrooms");
  }

  async function loginWithPassword(email: string, password: string) {
    await createTeacherPasswordSession(email, password);
    enterClassrooms(await getTeacherBootstrap());
  }

  async function registerWithPassword(
    schoolCode: string,
    displayName: string,
    email: string,
    password: string
  ) {
    await registerTeacher(schoolCode, displayName, email, password);
    enterClassrooms(await getTeacherBootstrap());
  }

  async function runAction(
    label: string,
    path: string,
    init: RequestInit,
    onSuccess: (result: unknown) => void
  ) {
    try {
      const result = await apiRequest(path, init);
      onSuccess(result);
      addLog(`${label}: 백엔드 처리 완료`);
    } catch (error) {
      const code = error instanceof ApiError ? ` (${error.code})` : "";
      const isConflict = error instanceof ApiError && ["STATE_VERSION_CONFLICT", "POLICY_VERSION_CONFLICT"].includes(error.code);
      if (isConflict && activeClassroomId) {
        await openClassroom(activeClassroomId);
      }
      const conflictGuide = isConflict ? " 최신 상태를 다시 불러왔어요. 변경 내용을 확인한 뒤 다시 실행해 주세요." : "";
      addLog(`${label}: 저장하지 못했어요${code}. ${error instanceof Error ? error.message : ""}${conflictGuide}`, "error");
    }
  }

  async function logout() {
    try {
      await logoutTeacher();
    } catch {
      // The local session must still be cleared when the server is unavailable.
    }
    setStage("login");
    setView("overview");
    setCreateStep("basic");
    setClassrooms([]);
    setHasClassroom(false);
  }

  async function openClassroom(resourceId: string) {
    setActiveClassroomId(resourceId);
    const classroom = classrooms.find((item) => item.resource_id === resourceId);
    setActiveStateVersion(classroom?.state_version ?? 0);
    try {
      const serverState = await getClassroomServerState(resourceId);
      const configuration = (serverState.configuration ?? {}) as {
        classroom_code?: string;
        currency_name?: string;
        module_codes?: string[];
        state_version?: number;
      };
      const modulesState = (serverState.modulesAndPolicies ?? {}) as {
        modules?: Array<{
          resource_id?: string;
          label?: string;
          module_code?: string;
          status?: string;
          version?: number;
          state_version?: number;
          config?: Record<string, unknown>;
        }>;
        policy_versions?: Array<{
          policy_area?: string;
          version?: number;
          state_version?: number;
          values?: Record<string, unknown>;
        }>;
      };
      const rosterState = (serverState.rosterAndSalaries ?? {}) as {
        students?: Array<{ attendance_number?: number; nickname?: string; role_code?: string | null; status?: string; state_version?: number }>;
        salary_roles?: Array<{ role_code?: string; label?: string; gross_amount?: number; state_version?: number }>;
      };
      setSettings((current) => ({
        ...current,
        classCode: configuration.classroom_code ?? current.classCode,
        currency: configuration.currency_name ?? current.currency
      }));
      if (modulesState.modules?.length) {
        setModules(modulesState.modules.map((module) => ({
          name: module.label ?? module.module_code ?? "이름 없는 모듈",
          status: module.status === "ACTIVE" ? "열림" : "닫힘",
          studentState: module.status === "ACTIVE" ? "학생 이용 가능" : "학생 이용 중지",
          teacherTodo: "",
          resourceId: module.resource_id,
          code: module.module_code,
          stateVersion: module.state_version,
          version: module.version,
          config: module.config
        })));
      }
      const aiPolicy = modulesState.policy_versions?.find((policy) => policy.policy_area === "AI_SETTINGS");
      const nextAiSettings: AiSettingsSnapshot = {
        enabled: aiPolicy?.values?.enabled !== false,
        reason: typeof aiPolicy?.values?.reason === "string" ? aiPolicy.values.reason : "",
        version: aiPolicy?.version ?? 0,
        stateVersion: aiPolicy?.state_version ?? classroom?.state_version ?? 0
      };
      setAiSettings(nextAiSettings);
      const latestStateVersion = Math.max(
        classroom?.state_version ?? 0,
        configuration.state_version ?? 0,
        nextAiSettings.stateVersion,
        ...(modulesState.modules?.map((module) => module.state_version ?? 0) ?? []),
        ...(modulesState.policy_versions?.map((policy) => policy.state_version ?? 0) ?? []),
        ...(rosterState.students?.map((student) => student.state_version ?? 0) ?? []),
        ...(rosterState.salary_roles?.map((role) => role.state_version ?? 0) ?? [])
      );
      setActiveStateVersion(latestStateVersion);
      if (rosterState.students) {
        const salaryByRole = new Map(rosterState.salary_roles?.map((role) => [role.role_code, role]) ?? []);
        setStudents(rosterState.students.map((student) => {
          const salaryRole = student.role_code ? salaryByRole.get(student.role_code) : undefined;
          return {
            attendance: String(student.attendance_number ?? ""),
            nickname: student.nickname ?? "",
            role: salaryRole?.label ?? student.role_code ?? "",
            salary: String(salaryRole?.gross_amount ?? 0),
            status: student.status ?? "정상"
          };
        }));
      }
      if (serverState.failures.length) {
        addLog(`${serverState.failures.join(", ")} 정보는 불러오지 못했어요. 나머지 정보만 표시합니다.`, "warning");
      }
      setStage("console");
    } catch (error) {
      addLog(`학급 정보를 불러오지 못했어요. ${error instanceof Error ? error.message : ""}`, "error");
      setStage("console");
    }
  }

  function leaveClassroom() {
    setStage("classrooms");
    setView("overview");
  }

  if (stage === "login") {
    return (
      <TeacherLogin
        busy={authBusy}
        error={authError}
        onLogin={loginWithPassword}
        onRegister={registerWithPassword}
      />
    );
  }

  if (stage === "classrooms") {
    return (
      <ClassroomSelect
        settings={settings}
        hasClassroom={hasClassroom}
        classrooms={classrooms}
        hasDraft={hasDraft}
        onCreate={() => {
          setCreateStep("basic");
          setStage("create");
        }}
        onResume={() => {
          setCreateStep("period");
          setStage("create");
        }}
        onOpen={openClassroom}
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
        schoolId={schoolId}
        onCancel={() => {
          setCreateStep("basic");
          setStage("classrooms");
        }}
        onSaveDraft={() => {
          setHasDraft(true);
          setStage("classrooms");
        }}
        onLogout={logout}
        onCreated={(classroom) => {
          const created: BootstrapClassroom = {
            resource_id: classroom.classroom_id,
            label: classroom.classroom_code,
            status: classroom.status,
            state_version: classroom.state_version,
            classroom_code: classroom.classroom_code,
            currency_name: classroom.currency_name
          };
          setClassrooms((items) => [...items, created]);
          setActiveClassroomId(created.resource_id);
          setActiveStateVersion(created.state_version ?? 0);
        }}
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
          <span>EV</span>
          <div>
            <strong>이코빌리지</strong>
            <small>Teacher Workspace</small>
          </div>
        </div>
        <nav>
          {views.map((item) => (
            <button key={item.key} className={view === item.key ? "active" : ""} onClick={() => setView(item.key)}>
              <item.icon size={17} strokeWidth={2} />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <button type="button" className="classroom-back" onClick={leaveClassroom}>
              <ChevronLeft size={15} />
              내 경제교실
            </button>
            <h1>{title}</h1>
          </div>
          <div className="teacher-meta">
            <span>{settings.className}</span>
            <b>{mode}</b>
            <div>
              <button type="button" onClick={leaveClassroom}>교실 나가기</button>
              <button type="button" onClick={logout}><LogOut size={15} />로그아웃</button>
            </div>
          </div>
        </header>

        <div className="console-content">
          {view === "overview" && <Overview mode={mode} modules={modules} students={students} approvals={approvals} />}
          {view === "plan" && <CoursePlanning modules={modules} setModules={setModules} addLog={addLog} />}
          {view === "setup" && (
            <Setup
              settings={settings}
              setSettings={setSettings}
              modules={modules}
              setModules={setModules}
              addLog={addLog}
              runAction={runAction}
              classroomId={activeClassroomId}
            />
          )}
          {view === "roster" && (
            <Roster
              settings={settings}
              students={students}
              setStudents={setStudents}
              runAction={runAction}
              addLog={addLog}
              classroomId={activeClassroomId}
              hasSalaryModule={modules.some((module) => module.name === "월급" && module.status === "열림")}
            />
          )}
          {view === "shop" && (
            <ShopAdmin
              currencyName={settings.currency}
              products={products}
              setProducts={setProducts}
              addLog={addLog}
              classroomId={activeClassroomId}
              stateVersion={activeStateVersion}
              policyVersion={Number(modules.find((module) => module.code === "STORE")?.config?.policy_version ?? 0)}
              onStateVersionChange={setActiveStateVersion}
            />
          )}
          {view === "control" && (
            <Control
              mode={mode}
              setMode={setMode}
              modules={modules}
              setModules={setModules}
              runAction={runAction}
              addLog={addLog}
              classroomId={activeClassroomId}
            />
          )}
          {view === "approvals" && (
            <Approvals
              currencyName={settings.currency}
              approvals={approvals}
              setApprovals={setApprovals}
              runAction={runAction}
              addLog={addLog}
              classroomId={activeClassroomId}
            />
          )}
          {view === "ai" && (
            <AiWorkspace
              classroomId={activeClassroomId}
              initialStateVersion={activeStateVersion}
              initialEnabled={aiSettings.enabled}
              initialReason={aiSettings.reason}
              initialPolicyVersion={aiSettings.version}
              canManage={teacherRoles.includes("OWN")}
              products={products}
              activeStudentCount={students.length}
              effectiveTaxBps={Math.round(Number(settings.incomeTax || 0) * 100)}
              quizSessionId={String(
                modules.find((module) => module.code === "QUIZ_GAME")?.config?.active_quiz_session_id
                ?? modules.find((module) => module.code === "QUIZ_GAME")?.config?.quiz_session_id
                ?? ""
              )}
              onStateVersionChange={setActiveStateVersion}
              onSettingsChanged={setAiSettings}
              onReload={() => openClassroom(activeClassroomId)}
              onProductPriceApplied={(productId, nextPrice) => setProducts((items) => items.map((item) => (
                item.resourceId === productId ? { ...item, price: String(nextPrice) } : item
              )))}
            />
          )}
        </div>
      </section>
      {logs[0] ? (
        <div className={`console-toast ${logs[0].tone}`} role="status">
          <span>{logs[0].message}</span>
          <button type="button" aria-label="알림 닫기" onClick={() => setLogs((items) => items.slice(1))}><X size={15} /></button>
        </div>
      ) : null}
    </main>
  );
}

function TeacherLogin({
  onLogin,
  onRegister,
  busy,
  error
}: Readonly<{
  onLogin: (email: string, password: string) => Promise<void>;
  onRegister: (
    schoolCode: string,
    displayName: string,
    email: string,
    password: string
  ) => Promise<void>;
  busy: boolean;
  error: string;
}>) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [schoolCode, setSchoolCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [starting, setStarting] = useState(false);
  const [localError, setLocalError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStarting(true);
    setLocalError("");
    try {
      if (mode === "register") {
        await onRegister(schoolCode, displayName, email, password);
      } else {
        await onLogin(email, password);
      }
    } catch (loginError) {
      setLocalError(loginError instanceof Error ? loginError.message : "요청을 처리하지 못했습니다.");
    } finally {
      setStarting(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="login-panel">
        <div className="brand login-brand">
          <span>EV</span>
          <div>
            <strong>이코빌리지</strong>
            <small>교사 관리 웹</small>
          </div>
        </div>
        <h1>{mode === "login" ? "교사 로그인" : "교사 회원가입"}</h1>
        <form className="auth-form" onSubmit={(event) => void submit(event)}>
          <p>{mode === "login" ? "등록한 이메일과 비밀번호로 로그인하세요." : "학교 코드로 소속을 확인한 뒤 교사 계정을 만듭니다."}</p>
          {error || localError ? <div className="inline-error" role="alert">{error || localError}</div> : null}
          {mode === "register" ? (
            <>
              <input required maxLength={40} value={schoolCode} onChange={(event) => setSchoolCode(event.target.value)} placeholder="학교 코드" autoComplete="organization" />
              <input required maxLength={120} value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="이름" autoComplete="name" />
            </>
          ) : null}
          <input required type="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="이메일" autoComplete="email" />
          <input required type="password" minLength={mode === "register" ? 10 : undefined} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="비밀번호" autoComplete={mode === "login" ? "current-password" : "new-password"} />
          <button type="submit" disabled={busy || starting}>
            {busy ? "로그인 상태 확인 중" : starting ? "처리 중" : mode === "login" ? "로그인" : "회원가입하고 로그인"}
          </button>
          <button type="button" className="preview-login" disabled={busy || starting} onClick={() => {
            setMode((current) => current === "login" ? "register" : "login");
            setLocalError("");
          }}>
            {mode === "login" ? "교사 회원가입" : "이미 계정이 있어요"}
          </button>
        </form>
      </section>
    </main>
  );
}

function ClassroomSelect({
  settings,
  hasClassroom,
  classrooms,
  hasDraft,
  onCreate,
  onResume,
  onOpen,
  onLogout
}: Readonly<{
  settings: ClassSettings;
  hasClassroom: boolean;
  classrooms: BootstrapClassroom[];
  hasDraft: boolean;
  onCreate: () => void;
  onResume: () => void;
  onOpen: (resourceId: string) => void;
  onLogout: () => void;
}>) {
  return (
    <main className="select-page">
      <header className="select-topbar">
        <div className="brand">
          <span>EV</span>
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
            classrooms.map((classroom) => (
              <button className="classroom-row" type="button" key={classroom.resource_id} onClick={() => void onOpen(classroom.resource_id)}>
                <span>
                  <strong>{classroom.label || settings.className}</strong>
                  <small>{classroom.classroom_code ? `입장 코드 ${classroom.classroom_code}` : settings.schoolName}</small>
                </span>
                <span>{classroom.currency_name ? `화폐 ${classroom.currency_name}` : `${settings.startDate} ~ ${settings.endDate}`}</span>
                <b>{classroom.status || "운영 중"}</b>
              </button>
            ))
          ) : (
            <div className="empty-classroom">
              <span className="empty-icon"><School size={22} /></span>
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
  schoolId,
  onCancel,
  onSaveDraft,
  onLogout,
  onCreated,
  onDone
}: Readonly<{
  step: CreateStep;
  setStep: React.Dispatch<React.SetStateAction<CreateStep>>;
  settings: ClassSettings;
  setSettings: React.Dispatch<React.SetStateAction<ClassSettings>>;
  modules: ModuleRow[];
  setModules: React.Dispatch<React.SetStateAction<ModuleRow[]>>;
  runAction: (
    label: string,
    path: string,
    init: RequestInit,
    onMockSuccess: (result: unknown) => void
  ) => Promise<void>;
  schoolId: string;
  onCancel: () => void;
  onSaveDraft: () => void;
  onLogout: () => void;
  onCreated: (classroom: CreateClassroomResponse) => void;
  onDone: (view: ViewKey) => void;
}>) {
  const [focusedModule, setFocusedModule] = useState("투자");
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [selectedModules, setSelectedModules] = useState<Set<string>>(() => new Set(
    selectableModuleNames.filter((moduleName) => modules.some((module) => module.name === moduleName && module.status === "열림"))
  ));

  function updateSettings(field: keyof ClassSettings, value: string) {
    setSettings((current) => ({ ...current, [field]: value }));
  }

  function applyModuleSelection(next: Set<string>) {
    const modulesToOpen = new Set<string>(next);
    for (const selectedModule of next) {
      for (const requiredModule of getRequiredModules(selectedModule)) {
        modulesToOpen.add(requiredModule);
      }
    }

    setSelectedModules(next);
    setModules((items) => enabledModuleNames.reduce<ModuleRow[]>((result, name) => {
      const existing = items.find((item) => item.name === name);
      const status = modulesToOpen.has(name) ? "열림" : "닫힘";
      return [...result, existing
        ? { ...existing, status }
        : { name, status, studentState: "학생 노출", teacherTodo: "설정 확인" }];
    }, items.filter((item) => !enabledModuleNames.includes(item.name))));
  }

  function toggleModule(moduleName: string) {
    const next = new Set(selectedModules);
    if (next.has(moduleName)) next.delete(moduleName);
    else next.add(moduleName);
    applyModuleSelection(next);
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
    applyModuleSelection(new Set(periodModuleRecommendations[period] ?? []));
  }

  function changeStartDate(startDate: string) {
    setSettings((current) => ({
      ...current,
      startDate,
      endDate: calculateEndDate(startDate, current.period)
    }));
  }

  function createClassroom() {
    if (!schoolId) {
      return;
    }
    runAction(
      "경제교실 개설",
      "/classrooms",
      {
        method: "POST",
        body: JSON.stringify({
          expected_state_version: 0,
          screen_id: "SCR-WEB-003",
          ui_action_id: "UIA-WEB-003",
          client_occurred_at: new Date().toISOString(),
          school_id: schoolId,
          grade: Number(settings.grade),
          currency_name: settings.currency,
          course_size: courseSizeFor(settings.period),
          starts_on: settings.startDate,
          ends_on: settings.endDate,
          student_count: Math.max(1, Number(settings.studentCount) || 1),
          timezone: "Asia/Seoul",
          module_codes: selectedModuleCodes(selectedModules)
        })
      },
      (result) => {
        onCreated(result as CreateClassroomResponse);
        setShowConfirmation(false);
        setStep("complete");
      }
    );
  }

  return (
    <main className="create-page">
      <header className="create-header">
        <div className="brand">
          <span>EV</span>
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
              <label>학생 수<input type="number" min="1" value={settings.studentCount} onChange={(event) => updateSettings("studentCount", event.target.value)} /></label>
              <label className="wide-field">수업 대상 설명<input value={settings.description} onChange={(event) => updateSettings("description", event.target.value)} /></label>
              <label>화폐명<input value={settings.currency} onChange={(event) => updateSettings("currency", event.target.value)} /></label>
            </div>
            <div className="wizard-actions">
              <button
                type="button"
                className="primary"
                disabled={!settings.className.trim()}
                onClick={() => {
                  selectPeriod(settings.period);
                  setStep("period");
                }}
              >
                다음
              </button>
            </div>
          </div>
        </section>
      )}

      {step === "period" && (
        <section className="create-layout">
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
            <h1>모듈 선택</h1>
            <div className="module-card-grid">
              {selectableModuleNames.map((moduleName) => {
                const isOpen = selectedModules.has(moduleName)
                  || [...selectedModules].some((selectedModule) => getRequiredModules(selectedModule).includes(moduleName));
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
            <div className="wizard-actions">
              <button type="button" onClick={() => setStep("basic")}>이전</button>
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
              <button type="button" className="primary" disabled={!schoolId} onClick={createClassroom}>확정하고 개설하기</button>
            </div>
            {!schoolId ? <p className="inline-error">소속 학교 정보를 불러오지 못했습니다. 다시 로그인해 주세요.</p> : null}
          </section>
        </div>
      ) : null}

      {step === "complete" && (
        <section className="created-panel">
          <span>완료</span>
          <h1>경제교실이 열렸어요</h1>
          <p>입장 코드 {settings.classCode}로 학생들이 접속할 수 있어요.</p>
          <div className="next-task-list">
            <button type="button" onClick={() => onDone("roster")}>학생 명단 등록하기</button>
            <button type="button" onClick={() => onDone("plan")}>운영 계획 세우기</button>
            <button type="button" onClick={() => onDone("overview")}>대시보드로 이동</button>
          </div>
          <button type="button" className="text-action" onClick={() => setStep("period")}>기간·모듈 다시 보기</button>
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
  const requiredModules = getRequiredModules(moduleName).filter((dependency) => dependency !== "지갑");
  const isOpen = modules.some((module) => module.name === moduleName && module.status === "열림");

  return (
    <aside className="module-help-panel">
      <span>{isOpen ? "사용 중" : "선택 전"}</span>
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
  const steps: Array<[CreateStep, string]> = [["basic", "기본 정보"], ["period", "기간·모듈 설정"], ["complete", "개설 완료"]];

  return (
    <ol className="stepper">
      {steps.map(([key, label]) => <li key={key} className={active === key ? "active" : ""}>{label}</li>)}
    </ol>
  );
}

function CreationSummary({ settings, modules }: Readonly<{ settings: ClassSettings; modules: ModuleRow[] }>) {
  const openModules = modules
    .filter((module) => module.status === "열림" && selectableModuleNames.includes(module.name))
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
  const [schedule, setSchedule] = useState(
    courseTemplates[0].schedule.map((item) => ({ ...item, lessonIds: [...item.lessonIds] }))
  );
  const [expandedDays, setExpandedDays] = useState<Set<number>>(new Set([0]));
  const openModules = modules.filter(
    (module) => module.status === "열림" && studentAccessModuleNames.includes(module.name)
  );

  const lessonOptions = [
    { id: "salary", title: "월급과 실수령액", module: "월급", minutes: "15분" },
    { id: "tax", title: "세금과 공동기금", module: "세금", minutes: "10분" },
    { id: "spending", title: "합리적인 소비", module: "상점", minutes: "15분" },
    { id: "shop", title: "우리 반 상점 운영", module: "상점", minutes: "20분" },
    { id: "saving", title: "예금과 적금", module: "예금", minutes: "15분" },
    { id: "investment", title: "단기·장기 투자", module: "투자", minutes: "20분" },
    { id: "trade", title: "친구와 안전하게 거래하기", module: "개인 거래", minutes: "15분" },
    { id: "budget", title: "공동기금 사용처 정하기", module: "공공 예산/투표", minutes: "20분" },
    { id: "quiz", title: "경제 개념 확인 퀴즈", module: "퀴즈", minutes: "10분" }
  ];

  function applyCourse(template: typeof courseTemplates[number]) {
    const selectedModules = new Set(template.modules);
    setSelectedCourse(template.name);
    setSchedule(template.schedule.map((item) => ({ ...item, lessonIds: [...item.lessonIds] })));
    setExpandedDays(new Set([0]));
    setModules((items) => {
      const updated = items.map((item) => studentAccessModuleNames.includes(item.name) || item.name === "월급" || item.name === "세금"
        ? { ...item, status: selectedModules.has(item.name) ? "열림" : "닫힘" }
        : item);
      return template.modules.reduce((current, name) => current.some((item) => item.name === name)
        ? current
        : [...current, { name, status: "열림", studentState: "학생 노출", teacherTodo: "일정 확인" }], updated);
    });
    addLog(`${template.name} 템플릿과 3일 운영 일정을 적용했어요.`);
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
      <PlainSection title="수업 템플릿">
        <div className="course-options">
          {courseTemplates.map((course) => (
            <button
              type="button"
              key={course.name}
              className={selectedCourse === course.name ? "selected" : ""}
              onClick={() => applyCourse(course)}
            >
              <strong>{course.name}</strong>
              <span>{course.description}</span>
              <small>{course.modules.filter((moduleName) => moduleName !== "지갑").join(" · ")}</small>
              {selectedCourse === course.name && <em>현재 일정에 적용됨</em>}
            </button>
          ))}
        </div>
      </PlainSection>

      <PlainSection title="운영 일정">
        <div className="schedule-list">
          <div className="applied-template-summary">
            <span>적용된 템플릿</span>
            <strong>{selectedCourse}</strong>
            <small>{schedule.length}일 과정</small>
          </div>
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
  setSettings,
  modules,
  addLog,
  runAction,
  setModules,
  classroomId
}: Readonly<{
  settings: ClassSettings;
  setSettings: React.Dispatch<React.SetStateAction<ClassSettings>>;
  modules: ModuleRow[];
  addLog: (message: string, tone?: LogTone) => void;
  runAction: (label: string, path: string, init: RequestInit, onMockSuccess: () => void) => Promise<void>;
  setModules: React.Dispatch<React.SetStateAction<ModuleRow[]>>;
  classroomId: string;
}>) {
  function updateSettings(field: keyof ClassSettings, value: string) {
    setSettings((current) => ({ ...current, [field]: value }));
  }

  function toggleModule(moduleName: string) {
    setModules((items) => items.map((item) => (
      item.name === moduleName
        ? { ...item, status: item.status === "열림" ? "닫힘" : "열림" }
        : item
    )));
  }

  return (
    <div className="page-flow">
      <PlainSection title="반 수업 만들기">
        <div className="class-create-panel">
          <div>
            <span>수업방</span>
            <strong>{settings.className}</strong>
          </div>
          <div>
            <span>입장 코드</span>
            <strong>{settings.classCode}</strong>
          </div>
          <button
            type="button"
            onClick={() => runAction(
              "반 수업 생성",
              "/classrooms",
              {
                method: "POST",
                body: JSON.stringify({
                  name: settings.className,
                  currency_name: settings.currency,
                  start_date: settings.startDate,
                  operation_period: settings.period
                })
              },
              () => addLog("반 수업을 만들었어요.")
            )}
          >
            반 수업 만들기
          </button>
        </div>
      </PlainSection>
      <PlainSection title="학급 정보">
        <div className="settings-form">
          <label>
            학급명
            <input value={settings.className} onChange={(event) => updateSettings("className", event.target.value)} />
          </label>
          <label>
            입장 코드
            <input value={settings.classCode} onChange={(event) => updateSettings("classCode", event.target.value.toUpperCase())} />
          </label>
          <label>
            시작일
            <input type="date" value={settings.startDate} onChange={(event) => updateSettings("startDate", event.target.value)} />
          </label>
          <label>
            화폐 이름
            <input value={settings.currency} readOnly aria-describedby="currency-name-help" />
            <small id="currency-name-help">화폐 단위는 거래 원장과 연결되어 있어 교실 개설 후에는 변경할 수 없습니다.</small>
          </label>
          <label>
            운영 기간
            <input value={settings.period} onChange={(event) => updateSettings("period", event.target.value)} />
          </label>
          <label>
            소득세
            <div className="input-with-unit">
              <input value={settings.incomeTax} onChange={(event) => updateSettings("incomeTax", event.target.value)} />
              <span>%</span>
            </div>
          </label>
        </div>
      </PlainSection>
      <PlainSection title="사용 기능">
        <div className="module-toggle-list">
          {selectableModuleNames.map((moduleName) => {
            const module = modules.find((item) => item.name === moduleName);
            const isOpen = module?.status === "열림";

            return (
              <button
                type="button"
                key={moduleName}
                className={isOpen ? "enabled" : ""}
                onClick={() => toggleModule(moduleName)}
              >
                <span>{moduleName}</span>
                <b>{isOpen ? "사용" : "미사용"}</b>
              </button>
            );
          })}
        </div>
      </PlainSection>
      <PlainSection title="저장">
        <ActionLine
          label="운영 기간 저장"
          value={`${settings.period} · ${settings.startDate} ~ ${settings.endDate}`}
          action="저장"
          onClick={() => addLog("운영 기간 변경은 다음 저장 API 연결 작업에서 적용됩니다.", "warning")}
        />
      </PlainSection>
    </div>
  );
}

function Roster({
  settings,
  students,
  setStudents,
  runAction,
  addLog,
  classroomId,
  hasSalaryModule
}: Readonly<{
  settings: ClassSettings;
  students: StudentRow[];
  setStudents: React.Dispatch<React.SetStateAction<StudentRow[]>>;
  runAction: (label: string, path: string, init: RequestInit, onMockSuccess: () => void) => Promise<void>;
  addLog: (message: string, tone?: LogTone) => void;
  classroomId: string;
  hasSalaryModule: boolean;
}>) {
  const [isManaging, setIsManaging] = useState(false);
  const [isAdding, setIsAdding] = useState(false);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [deletingIndex, setDeletingIndex] = useState<number | null>(null);
  const [draftStudent, setDraftStudent] = useState<StudentRow | null>(null);
  const [studentQuery, setStudentQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("전체 상태");
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
    if (!draftStudent.nickname.trim() || (hasSalaryModule && !draftStudent.role.trim())) {
      addLog(hasSalaryModule ? "학생 이름과 직업은 반드시 입력해야 해요." : "학생 이름을 입력해 주세요.", "warning");
      return;
    }

    setStudents((items) => items.map((item, itemIndex) => itemIndex === index ? draftStudent : item));
    cancelEdit();
    addLog(`${draftStudent.nickname} 학생 정보를 수정했어요.`);
  }

  function addStudent() {
    const trimmedNickname = newStudent.nickname.trim();
    const trimmedRole = newStudent.role.trim();

    if (!trimmedNickname || (hasSalaryModule && !trimmedRole)) {
      addLog(hasSalaryModule ? "학생 이름과 직업을 입력해야 추가할 수 있어요." : "학생 이름을 입력해야 추가할 수 있어요.", "warning");
      return;
    }

    const studentToAdd = {
      ...newStudent,
      attendance: newStudent.attendance.trim() || String(students.length + 1),
      nickname: trimmedNickname,
      role: hasSalaryModule ? trimmedRole : "",
      salary: hasSalaryModule ? newStudent.salary.trim() || "150" : "0",
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

  function removeStudent(index: number) {
    const target = students[index];
    setStudents((items) => items.filter((_, itemIndex) => itemIndex !== index));
    setDeletingIndex(null);
    cancelEdit();
    addLog(`${target.nickname} 학생을 명단에서 삭제했어요.`);
  }

  function closeManager() {
    setIsManaging(false);
    setIsAdding(false);
    setDeletingIndex(null);
    setStudentQuery("");
    setStatusFilter("전체 상태");
    cancelEdit();
  }

  const salaryRoleDefinitions = Array.from(
    new Map(students.map((student) => [student.role, { name: student.role, salary: student.salary }])).values()
  );
  const roleCodeByName = new Map(
    salaryRoleDefinitions.map((role, index) => [role.name, `ROLE_${index + 1}`])
  );
  const rosterBody = {
    expected_policy_version: 0,
    expected_state_version: 0,
    students: students.map((student) => ({
      student_id: null,
      attendance_number: Number(student.attendance),
      nickname: student.nickname,
      role_code: hasSalaryModule ? roleCodeByName.get(student.role) : null
    })),
    salary_roles: hasSalaryModule ? salaryRoleDefinitions.map((role, index) => ({
      role_code: `ROLE_${index + 1}`,
      name: role.name,
      gross_amount: Number(role.salary),
      deductions: Math.floor(Number(role.salary) * 0.1),
      sort_order: index + 1
    })) : []
  };
  const grossTotal = students.reduce((sum, student) => sum + Number(student.salary), 0);
  const taxTotal = Math.floor(grossTotal * (Number(settings.incomeTax) / 100));
  const netTotal = grossTotal - taxTotal;
  const statusOptions = useMemo(
    () => Array.from(new Set(students.map((student) => student.status))),
    [students]
  );
  const normalizedQuery = studentQuery.trim().toLowerCase();
  const visibleStudents = students
    .map((student, index) => ({ student, index }))
    .filter(({ student }) => {
      const matchesQuery = !normalizedQuery
        || student.nickname.toLowerCase().includes(normalizedQuery)
        || student.attendance.includes(normalizedQuery)
                  || (hasSalaryModule && student.role.toLowerCase().includes(normalizedQuery));
      const matchesStatus = statusFilter === "전체 상태" || student.status === statusFilter;
      return matchesQuery && matchesStatus;
    });

  return (
    <div className="page-flow">
      <PlainSection title="학생 명단">
        <div className="roster-summary">
          <div>
            <span>현재 인원</span>
            <strong>{students.length}명</strong>
          </div>
          <button type="button" onClick={() => setIsManaging(true)}>
            <Users aria-hidden="true" size={17} />
            명단 수정
          </button>
        </div>
      </PlainSection>

      {isManaging && (
        <div className="confirm-backdrop" role="presentation">
          <section className="roster-dialog" role="dialog" aria-modal="true" aria-labelledby="roster-dialog-title">
            <header className="roster-dialog-header">
              <div>
                <span>현재 인원 {students.length}명</span>
                <h2 id="roster-dialog-title">학생 명단 수정</h2>
              </div>
              <button className="icon-button" type="button" aria-label="명단 수정 닫기" onClick={closeManager}>
                <X aria-hidden="true" size={20} />
              </button>
            </header>

            <div className="roster-toolbar">
              <div className="roster-filter-group">
                <label className="roster-search">
                  <Search aria-hidden="true" size={16} />
                  <input
                    aria-label="학생 검색"
                    value={studentQuery}
                    onChange={(event) => setStudentQuery(event.target.value)}
                    placeholder={hasSalaryModule ? "번호, 이름 또는 직업 검색" : "번호 또는 이름 검색"}
                  />
                </label>
                <select
                  aria-label="학생 상태 필터"
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value)}
                >
                  <option>전체 상태</option>
                  {statusOptions.map((status) => <option key={status}>{status}</option>)}
                </select>
                <span>{visibleStudents.length}명 표시</span>
              </div>
              <button type="button" onClick={() => setIsAdding(true)}>
                <UserPlus aria-hidden="true" size={16} />
                학생 추가
              </button>
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
                {hasSalaryModule ? (
                  <>
                    <label>
                      직업
                      <input value={newStudent.role} onChange={(event) => updateNewStudent("role", event.target.value)} placeholder="예: 은행 매니저" />
                    </label>
                    <label>
                      월급
                      <input value={newStudent.salary} onChange={(event) => updateNewStudent("salary", event.target.value)} />
                    </label>
                  </>
                ) : null}
                <div className="row-actions">
                  <button type="button" onClick={addStudent}>추가</button>
                  <button className="ghost" type="button" onClick={() => setIsAdding(false)}>취소</button>
                </div>
              </div>
            )}

            <div className="student-table-scroll">
              <table className="student-table roster-manage-table">
                <thead>
                  <tr>
                    <th>번호</th>
                    <th>이름</th>
                    {hasSalaryModule ? <th>직업</th> : null}
                    {hasSalaryModule ? <th>월급</th> : null}
                    <th>상태</th>
                    <th aria-label="관리" />
                  </tr>
                </thead>
                <tbody>
                {visibleStudents.map(({ student, index }) => {
                  const activeDraft = editingIndex === index ? draftStudent : null;

                  return (
                    <tr key={`${student.attendance}-${student.nickname}`}>
                      {activeDraft ? (
                        <>
                          <td><input value={activeDraft.attendance} onChange={(event) => updateDraft("attendance", event.target.value)} /></td>
                          <td><input value={activeDraft.nickname} onChange={(event) => updateDraft("nickname", event.target.value)} /></td>
                          {hasSalaryModule ? <td><input value={activeDraft.role} onChange={(event) => updateDraft("role", event.target.value)} /></td> : null}
                          {hasSalaryModule ? <td><input value={activeDraft.salary} onChange={(event) => updateDraft("salary", event.target.value)} /></td> : null}
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
                          {hasSalaryModule ? <td>{student.role}</td> : null}
                          {hasSalaryModule ? <td>{student.salary}비빔</td> : null}
                          <td>{student.status}</td>
                          <td>
                            {deletingIndex === index ? (
                              <div className="row-actions delete-actions">
                                <button className="ghost" type="button" onClick={() => setDeletingIndex(null)}>취소</button>
                                <button className="danger" type="button" onClick={() => removeStudent(index)}>삭제</button>
                              </div>
                            ) : (
                              <div className="row-actions">
                                <button type="button" onClick={() => startEdit(index)}>수정</button>
                                <button className="icon-danger" type="button" aria-label={`${student.nickname} 삭제`} onClick={() => setDeletingIndex(index)}>
                                  <Trash2 aria-hidden="true" size={15} />
                                </button>
                              </div>
                            )}
                          </td>
                        </>
                      )}
                    </tr>
                  );
                })}
                </tbody>
              </table>
              {visibleStudents.length === 0 && (
                <p className="roster-empty">조건에 맞는 학생이 없어요.</p>
              )}
            </div>

            <footer className="roster-dialog-footer">
              <button type="button" onClick={closeManager}>완료</button>
            </footer>
          </section>
        </div>
      )}
      {hasSalaryModule ? <PlainSection title="급여">
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
      </PlainSection> : null}
      <PlainSection title="처리">
        <ActionLine
          label="명단 저장"
          value={`${students.length}명 반영`}
          action="저장"
          onClick={() => runAction(
            hasSalaryModule ? "명단·급여 저장" : "명단 저장",
            `/classrooms/${classroomId}/roster-and-salaries`,
            { method: "PUT", body: JSON.stringify(rosterBody) },
            () => addLog(hasSalaryModule ? "명단과 급여 정책을 저장했어요." : "학생 명단을 저장했어요.")
          )}
        />
        {hasSalaryModule ? <ActionLine
          label="급여 지급"
          value={`${students.length}명에게 ${netTotal}${settings.currency}`}
          action="지급"
          onClick={() => runAction(
            "급여 지급",
            `/classrooms/${classroomId}/payouts`,
            {
              method: "POST",
              body: JSON.stringify({
                payoutType: "SALARY",
                studentIds: [],
                periodKey: "DAY-1",
                amount: grossTotal,
                policyVersion: 0,
                reason: "정기 급여 지급",
                expectedStateVersion: 0,
                clientOccurredAt: new Date().toISOString()
              })
            },
            () => addLog("급여 지급을 처리했어요.")
          )}
        /> : null}
      </PlainSection>
    </div>
  );
}

function ShopAdmin({
  currencyName,
  products,
  setProducts,
  addLog,
  classroomId,
  stateVersion,
  policyVersion,
  onStateVersionChange
}: Readonly<{
  currencyName: string;
  products: ProductRow[];
  setProducts: React.Dispatch<React.SetStateAction<ProductRow[]>>;
  addLog: (message: string, tone?: LogTone) => void;
  classroomId: string;
  stateVersion: number;
  policyVersion: number;
  onStateVersionChange: (version: number) => void;
}>) {
  const [preview, setPreview] = useState("");
  const [fileName, setFileName] = useState("선택된 사진 없음");
  const [name, setName] = useState("연필 교환권");
  const [price, setPrice] = useState("30");
  const [stock, setStock] = useState("12");
  const [kind, setKind] = useState<"PHYSICAL" | "DIGITAL">("PHYSICAL");
  const [submitting, setSubmitting] = useState(false);
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

  async function addProduct() {
    const numericPrice = Number(price);
    const numericStock = Number(stock);
    if (!classroomId || !name.trim() || !Number.isInteger(numericPrice) || numericPrice < 0 || !Number.isInteger(numericStock) || numericStock < 0) {
      addLog("상품명, 가격, 재고를 올바르게 입력해 주세요.", "warning");
      return;
    }
    setSubmitting(true);
    try {
      const response = await apiRequest<{
        product_id: string;
        status: string;
        state_version: number;
        version: number;
      }>(`/classrooms/${classroomId}/store/product-commands`, {
        method: "POST",
        body: JSON.stringify({
          expected_state_version: stateVersion,
          expected_policy_version: policyVersion,
          screen_id: "SCR-TCH-STORE",
          ui_action_id: "UIA-STORE-PRODUCT-CREATE",
          client_occurred_at: new Date().toISOString(),
          command: "CREATE",
          product_id: null,
          name: name.trim(),
          description: "",
          product_kind: kind,
          unit_price: numericPrice,
          vat_rate_bps: 0,
          stock_quantity: numericStock,
          character_source_id: null,
          expected_product_version: null
        })
      });
      setProducts((items) => [{
        name: name.trim(),
        price: String(numericPrice),
        stock: String(numericStock),
        imageName: preview ? fileName : "",
        imageSrc: preview,
        resourceId: response.product_id,
        stateVersion: response.state_version,
        version: response.version,
        kind,
        vatRateBps: 0
      }, ...items]);
      onStateVersionChange(response.state_version);
      setName("");
      setPrice("");
      setStock("");
      setPreview("");
      setFileName("선택된 사진 없음");
      addLog("상품을 서버에 등록했어요.");
    } catch (error) {
      addLog(`상품을 등록하지 못했어요. ${error instanceof Error ? error.message : ""}`, "error");
    } finally {
      setSubmitting(false);
    }
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
          <label>상품 종류
            <select value={kind} onChange={(event) => setKind(event.target.value as "PHYSICAL" | "DIGITAL")}>
              <option value="PHYSICAL">교사가 제공하는 상품</option>
              <option value="DIGITAL">디지털 상품</option>
            </select>
          </label>
          <label className="image-upload">
            상품 사진
            <input type="file" accept="image/*" onChange={handleImageChange} />
            <span>{fileName}</span>
          </label>
          <div className="image-preview">
            {preview ? <img src={preview} alt="상품 사진 미리보기" /> : <span>사진 미리보기</span>}
          </div>
          <button className="wide-action" disabled={submitting} onClick={() => void addProduct()}>
            {submitting ? "등록하는 중" : "상품 올리기"}
          </button>
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
                      <span>{product.price}{currencyName} · 재고 {product.stock}</span>
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
  mode,
  setMode,
  modules,
  setModules,
  runAction,
  addLog,
  classroomId
}: Readonly<{
  mode: string;
  setMode: React.Dispatch<React.SetStateAction<string>>;
  modules: ModuleRow[];
  setModules: React.Dispatch<React.SetStateAction<ModuleRow[]>>;
  runAction: (label: string, path: string, init: RequestInit, onMockSuccess: () => void) => Promise<void>;
  addLog: (message: string, tone?: LogTone) => void;
  classroomId: string;
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
  currencyName,
  approvals,
  setApprovals,
  runAction,
  addLog,
  classroomId
}: Readonly<{
  currencyName: string;
  approvals: ApprovalRow[];
  setApprovals: React.Dispatch<React.SetStateAction<ApprovalRow[]>>;
  runAction: (label: string, path: string, init: RequestInit, onMockSuccess: () => void) => Promise<void>;
  addLog: (message: string, tone?: LogTone) => void;
  classroomId: string;
}>) {
  function updateApproval(id: string, status: string) {
    setApprovals((items) => items.map((item) => item.id === id ? { ...item, status } : item));
  }

  const processedStatuses = new Set(["승인 완료", "처리 완료", "거절"]);
  const pendingApprovals = approvals.filter((item) => !processedStatuses.has(item.status));
  const processedApprovals = approvals.filter((item) => processedStatuses.has(item.status));

  return (
    <div className="page-flow">
      <PlainSection title="승인 대기">
        <div className="approval-list">
          {pendingApprovals.length === 0 ? <p className="empty-copy">승인할 항목이 없어요.</p> : null}
          {pendingApprovals.map((item) => (
            <div className="approval-row" key={item.id}>
              <div>
                <b>{item.type}</b>
                <span>{item.target} · {item.amount}{currencyName} · {item.status}</span>
              </div>
              <div>
                <button onClick={() => {
                  if (item.type === "거래" && item.tradeId) {
                    runAction(
                      "거래 승인",
                      `/classrooms/${classroomId}/trades/${item.tradeId}/teacher-decisions`,
                      {
                        method: "POST",
                        body: JSON.stringify({
                          expected_state_version: item.stateVersion ?? 0,
                          screen_id: "SCR-WEB-006",
                          ui_action_id: "UIA-WEB-006",
                          client_occurred_at: new Date().toISOString(),
                          decision: "APPROVE",
                          reason: "교사 확인"
                        })
                      },
                      () => updateApproval(item.id, "승인 완료")
                    );
                    return;
                  }
                  if (item.type === "거래") {
                    addLog("표시된 거래는 데모 데이터입니다. 실제 거래가 생성되면 그 거래 ID로 승인할 수 있습니다.", "warning");
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
      {processedApprovals.length > 0 ? (
        <PlainSection title="처리 내역">
          <div className="approval-list">
            {processedApprovals.map((item) => (
              <div className="approval-row" key={item.id}>
                <div>
                  <b>{item.type}</b>
                  <span>{item.target} · {item.amount}</span>
                </div>
                <strong className="approval-result">{item.status}</strong>
              </div>
            ))}
          </div>
        </PlainSection>
      ) : null}
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

function PlainSection({
  title,
  description,
  children
}: Readonly<{ title: string; description?: string; children: React.ReactNode }>) {
  return (
    <section className="plain-section">
      <div className="section-title">
        <h2>{title}</h2>
        {description ? <p>{description}</p> : null}
      </div>
      {children}
    </section>
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
  onClick
}: Readonly<{ label: string; value: string; action: string; onClick: () => void }>) {
  return (
    <div className="action-line">
      <div>
        <b>{label}</b>
        <span>{value}</span>
      </div>
      <button onClick={onClick}>{action}</button>
    </div>
  );
}
