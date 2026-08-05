"use client";

import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from "react";
import {
  Bot,
  ChevronLeft,
  ClipboardCheck,
  Landmark,
  LayoutDashboard,
  ListChecks,
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
  CourseSize,
  CreateClassroomResponse,
  courseSizeToPeriod,
  createTeacherPasswordSession,
  createTeacherSession,
  getClassroomServerState,
  getTeacherBootstrap,
  hasTeacherAccessToken,
  logoutTeacher,
  MODULE_DEFINITIONS,
  periodToCourseSize,
  registerTeacher,
  restoreTeacherSession,
  SetupPrecheckResponse
} from "./api";
import { consumeTeacherOidcCallback, hasOidcCallback } from "./oidc";
import { AiWorkspace } from "./ai/AiWorkspace";
import { QuizDraftWorkspace } from "./ai/QuizDraftWorkspace";
import { DraftRecommendationPanel } from "./ai/DraftRecommendationPanel";
import { ModuleActivationPanel, SetupPrecheckSummary } from "./ai/ModuleActivationPanel";
import type { AiModuleRow, AiPolicyVersionRow } from "./types";
import { TeacherRealtimeClient } from "./realtime";

type ViewKey = "overview" | "setup" | "roster" | "shop" | "finance" | "quiz" | "control" | "approvals" | "ai";
type TeacherStage = "login" | "classrooms" | "create" | "console";
type CreateStep = "basic" | "period" | "complete";
type LogTone = "success" | "warning" | "error";

type LogEntry = {
  id: string;
  tone: LogTone;
  message: string;
};

type StudentRow = {
  resourceId?: string;
  attendance: string;
  nickname: string;
  role: string;
  salary: string;
  status: string;
  presence?: "ONLINE" | "AWAY" | "OFFLINE" | "UNKNOWN";
  currentScreen?: string;
  lastSeenAt?: string;
};

type PublishedQuizQuestion = {
  version_id: string;
  status: string;
  content_type: string;
};

async function openPublishedQuizForStudents({
  classroomId,
  stateVersion,
  students,
  runAction,
  addLog
}: {
  classroomId: string;
  stateVersion: number;
  students: StudentRow[];
  runAction: (label: string, path: string, init: RequestInit, onSuccess: () => void) => Promise<void>;
  addLog: (message: string, tone?: LogTone) => void;
}) {
  const participantIds = students.flatMap((student) => student.resourceId ? [student.resourceId] : []);
  if (!participantIds.length) {
    addLog("학생 명단을 저장한 뒤 퀴즈를 열어 주세요.", "warning");
    return;
  }

  try {
    const response = await apiRequest<{ questions?: PublishedQuizQuestion[] }>(
      `/classrooms/${classroomId}/quiz-questions`
    );
    const questionVersionIds = (response.questions ?? [])
      .filter((question) => question.status === "PUBLISHED" && question.content_type === "ECONOMY")
      .map((question) => question.version_id);
    if (!questionVersionIds.length) {
      addLog("공개된 경제 퀴즈 문항이 없어요. 문항을 공개한 뒤 다시 시도해 주세요.", "warning");
      return;
    }

    await runAction(
      "학생 퀴즈 열기",
      `/classrooms/${classroomId}/quiz-sessions/${crypto.randomUUID()}/commands`,
      {
        method: "POST",
        body: JSON.stringify({
          expectedStateVersion: stateVersion,
          command: "START",
          questionVersionIds,
          participantIds,
          mode: "GAME",
          contentType: "ECONOMY",
          rewardPolicyPublic: {},
          leaderboardPublic: true
        })
      },
      () => addLog(`${participantIds.length}명에게 퀴즈를 열었어요.`)
    );
  } catch (error) {
    const code = error instanceof ApiError ? ` (${error.code})` : "";
    addLog(`공개 퀴즈를 불러오지 못했어요${code}. ${error instanceof Error ? error.message : ""}`, "error");
  }
}

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
  { key: "setup" as const, label: "학급 설정", icon: Settings },
  { key: "roster" as const, label: "학생·급여", icon: Users },
  { key: "shop" as const, label: "상점 상품", icon: ShoppingBag },
  { key: "finance" as const, label: "운영 관리", icon: Landmark },
  { key: "quiz" as const, label: "퀴즈 문제은행", icon: ListChecks },
  { key: "control" as const, label: "수업 준비", icon: MonitorPlay },
  { key: "approvals" as const, label: "승인·정산", icon: ClipboardCheck },
  { key: "ai" as const, label: "AI 설정", icon: Bot }
];

const initialModules: ModuleRow[] = [
  { name: "지갑", status: "열림", studentState: "기본 기능", teacherTodo: "자동 적용" },
  { name: "상점", status: "열림", studentState: "학생 구매 가능", teacherTodo: "상품 2개 판매 중" },
  { name: "예금", status: "열림", studentState: "예금 가입 가능", teacherTodo: "예금 상품 관리" },
  { name: "적금", status: "열림", studentState: "적금 가입 가능", teacherTodo: "적금 상품 관리" },
  { name: "대출", status: "닫힘", studentState: "학생 이용 중지", teacherTodo: "대출 정책 설정" },
  { name: "단기 투자", status: "열림", studentState: "다음 날 결과 확인", teacherTodo: "시장 결과 준비" },
  { name: "장기 투자", status: "열림", studentState: "매일 소식 확인", teacherTodo: "투자 뉴스 확인" },
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
  status,
  presence: index < 17 ? "ONLINE" : index < 20 ? "AWAY" : index < 22 ? "OFFLINE" : "UNKNOWN",
  currentScreen: index < 17 ? ["마을 광장", "이코 은행", "퀴즈"][index % 3] : "",
  lastSeenAt: index < 20 ? new Date(Date.now() - index * 45_000).toISOString() : undefined
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
    modules: ["지갑", "세금", "예금", "적금", "단기 투자", "장기 투자", "퀴즈"],
    schedule: [
      { day: "1일차", lessonIds: ["tax", "saving"] },
      { day: "2일차", lessonIds: ["short-investment", "long-investment"] },
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

const enabledModuleNames = ["지갑", "월급", "세금", "상점", "캐릭터", "개인 거래", "예금", "적금", "신용", "대출", "청약", "부동산", "단기 투자", "장기 투자", "공공 예산/투표", "퀴즈"];
const creditLevelLabels: Record<string, string> = { GOOD: "우수", NORMAL: "보통", CAUTION: "주의", RESTRICTED: "제한" };
const selectableModuleNames = enabledModuleNames.filter((moduleName) => moduleName !== "지갑");
const studentAccessModuleNames = ["상점", "캐릭터", "개인 거래", "예금", "적금", "대출", "청약", "부동산", "단기 투자", "장기 투자", "공공 예산/투표", "퀴즈"];

const moduleDependencies: Record<string, string[]> = {
  월급: ["지갑"],
  세금: ["지갑"],
  상점: ["지갑"],
  캐릭터: ["지갑", "상점"],
  "개인 거래": ["지갑"],
  예금: [],
  적금: [],
  신용: [],
  대출: ["예금", "신용"],
  "단기 투자": ["지갑", "세금"],
  "장기 투자": ["지갑", "세금"],
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
  적금: "정한 금액을 회차마다 모으고 적금 진행률을 확인합니다.",
  신용: "상환 이력에 따라 개인 신용점수와 등급을 관리합니다.",
  대출: "교육용 대출 한도와 상환 계획을 확인하고 신청합니다.",
  "단기 투자": "오늘 투자하고 다음 날 확정되는 투자 활동입니다.",
  "장기 투자": "매일 공개되는 소식을 보고 보유 또는 회수를 결정합니다.",
  "공공 예산/투표": "공동기금 사용처를 정하고 익명 투표를 진행합니다.",
  퀴즈: "경제 퀴즈와 게임형/평가형 수업 활동을 진행합니다."
};

const periodModuleRecommendations: Record<string, string[]> = {
  "1회 체험": ["퀴즈"],
  "1주 프로젝트": ["월급", "세금", "상점", "예금", "퀴즈"],
  "1달 운영": ["월급", "세금", "상점", "캐릭터", "예금", "단기 투자", "장기 투자", "공공 예산/투표", "퀴즈"],
  "1학기 운영": selectableModuleNames
};

const lessonQuizCatalog = [
  { id: "salary", title: "월급과 실수령액", module: "월급", minutes: "15분", concept: "월급은 일을 하고 받는 돈이며, 세금을 내고 실제로 받는 금액을 실수령액이라고 해요.", question: "월급에서 세금을 낸 뒤 실제로 받는 돈을 무엇이라고 하나요?", options: ["매출액", "실수령액", "대출금"], answer: 1 },
  { id: "tax", title: "세금과 공동기금", module: "세금", minutes: "15분", concept: "세금은 공동체에 필요한 일을 함께 해결하기 위해 구성원이 나누어 내는 돈이에요.", question: "우리 반에서 모은 소득세는 어디에 모이나요?", options: ["개인 지갑", "공동기금", "캐릭터 상점"], answer: 1 },
  { id: "spending", title: "필요와 욕구 구분하기", module: "상점", minutes: "15분", concept: "꼭 필요한 것과 갖고 싶은 것을 구분하면 정해진 돈 안에서 더 합리적으로 소비할 수 있어요.", question: "합리적인 소비를 위해 가장 먼저 할 일은 무엇인가요?", options: ["모두 바로 사기", "필요와 욕구 구분하기", "가격을 보지 않기"], answer: 1 },
  { id: "shop", title: "우리 반 상점 운영", module: "상점", minutes: "20분", concept: "가격과 재고를 확인하고 꼭 필요한 상품을 선택하는 과정에서 소비 계획을 연습해요.", question: "상품을 사기 전에 함께 확인할 것은 무엇인가요?", options: ["가격과 잔액", "친구의 이름", "화면 색깔"], answer: 0 },
  { id: "saving", title: "예금과 적금", module: "예금", minutes: "20분", concept: "예금은 목돈을 한 번 맡기고, 적금은 일정한 금액을 여러 번 나누어 모으는 금융 활동이에요.", question: "매일 일정한 금액을 나누어 모으는 상품은 무엇인가요?", options: ["예금", "적금", "대출"], answer: 1 },
  { id: "loan", title: "빌린 돈과 상환", module: "대출", minutes: "15분", concept: "대출은 필요한 돈을 빌리고 약속한 기간에 원금과 이자를 갚는 금융 활동이에요.", question: "대출받은 뒤 지켜야 할 약속은 무엇인가요?", options: ["정해진 때에 갚기", "기록 지우기", "이자를 무시하기"], answer: 0 },
  { id: "housing-subscription", title: "청약과 내 집 마련", module: "청약", minutes: "20분", concept: "청약은 새로 지어지는 집에 살 기회를 얻기 위해 신청하는 제도예요. 조건과 순서를 확인해 계획적으로 준비해요.", question: "청약을 준비할 때 가장 먼저 확인할 것은 무엇인가요?", options: ["신청 조건", "친구의 취미", "집 색깔"], answer: 0 },
  { id: "real-estate", title: "부동산과 주거 선택", module: "부동산", minutes: "20분", concept: "부동산은 땅과 건물처럼 옮길 수 없는 재산이에요. 가격뿐 아니라 위치와 필요한 비용을 함께 살펴봐요.", question: "집을 고를 때 함께 살펴볼 내용은 무엇인가요?", options: ["위치와 비용", "광고 색깔", "유행어"], answer: 0 },
  { id: "short-investment", title: "단기 투자와 결과", module: "단기 투자", minutes: "20분", concept: "단기 투자는 가까운 시점의 결과를 예상해 투자하며 수익과 손실이 모두 생길 수 있어요.", question: "투자에서 반드시 기억해야 하는 것은 무엇인가요?", options: ["항상 수익이 난다", "손실이 생길 수 있다", "가격은 변하지 않는다"], answer: 1 },
  { id: "long-investment", title: "장기 투자와 정보 판단", module: "장기 투자", minutes: "20분", concept: "장기 투자는 여러 날의 정보와 변화를 살피며 계속 보유할지 회수할지 판단해요.", question: "장기 투자 판단에 가장 필요한 것은 무엇인가요?", options: ["하루의 느낌", "여러 날의 정보", "친구의 추측만 듣기"], answer: 1 },
  { id: "trade", title: "친구와 안전하게 거래하기", module: "개인 거래", minutes: "15분", concept: "개인 거래는 주고받을 금액과 물건을 서로 확인하고 동의한 뒤 진행해야 해요.", question: "안전한 개인 거래에 필요한 것은 무엇인가요?", options: ["서로의 동의", "몰래 바꾸기", "기록 삭제"], answer: 0 },
  { id: "budget", title: "공동기금과 투표", module: "공공 예산/투표", minutes: "20분", concept: "공동기금은 모두를 위해 사용하며, 투표로 사용처를 공정하게 결정할 수 있어요.", question: "공동기금 사용처를 정하는 공정한 방법은 무엇인가요?", options: ["한 명이 결정", "익명 투표", "먼저 가져가기"], answer: 1 }
  ,{ id: "quiz", title: "경제 개념 확인 퀴즈", module: "퀴즈", minutes: "10분", concept: "앞에서 배운 월급, 세금, 소비, 금융 개념을 짧은 문제로 다시 확인해요.", question: "경제 활동을 기록하면 좋은 점은 무엇인가요?", options: ["돈의 흐름을 알 수 있다", "잔액이 사라진다", "세금을 내지 않는다"], answer: 0 }
] as const;

const moduleCodeByName: Record<string, string> = {
  월급: "ROLE_SALARY",
  세금: "INCOME_TAX",
  상점: "STORE",
  캐릭터: "CHARACTER",
  "개인 거래": "TRADE",
  예금: "DEPOSIT",
  적금: "SAVINGS",
  신용: "CREDIT",
  대출: "LOAN",
  투자: "INVESTMENT",
  "공공 예산/투표": "PUBLIC_FUND",
  퀴즈: "QUIZ_GAME"
};

moduleCodeByName["부동산"] = "REAL_ESTATE";
moduleCodeByName["청약"] = "HOUSING_SUBSCRIPTION";

const moduleNameByCode = new Map(
  Object.entries(moduleCodeByName).map(([name, code]) => [code, name])
);

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
  const [classroomStateVersion, setClassroomStateVersion] = useState(0);
  const [coursePolicyVersion, setCoursePolicyVersion] = useState(0);
  const [salaryPolicyVersion, setSalaryPolicyVersion] = useState(0);
  // 상점 상품 AI 가격 추천이 expected_policy_version으로 되돌려 보낼 정책 버전 목록입니다.
  const [policyVersions, setPolicyVersions] = useState<AiPolicyVersionRow[]>([]);
  const [realtimeRevision, setRealtimeRevision] = useState(0);
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
        if (event.event_type.startsWith("loan.")) {
          setRealtimeRevision((revision) => revision + 1);
        }
        refreshClassroom();
      },
      onRecoveryRequired: () => {
        setRealtimeRevision((revision) => revision + 1);
        refreshClassroom();
      }
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

  // 진행 중인 퀴즈 세션 id입니다. 학생 앱도 같은 위치(활성 퀴즈 모듈 config)를 읽습니다.
  const activeQuizSessionId = ["QUIZ_GAME", "QUIZ_TEST"].flatMap((code) => {
    const config = modules.find((module) => module.code === code && module.status === "열림")?.config;
    const value = config?.active_quiz_session_id ?? config?.quiz_session_id;
    return typeof value === "string" && value.trim() ? [value] : [];
  })[0];

  function enterClassrooms(bootstrap: Awaited<ReturnType<typeof getTeacherBootstrap>>) {
    setClassrooms(bootstrap.classrooms);
    setSchoolId(bootstrap.schools[0]?.school_id ?? "");
    setTeacherRoles(bootstrap.roles);
    setHasClassroom(bootstrap.classrooms.length > 0);
    if (bootstrap.classrooms[0]) {
      setActiveClassroomId(bootstrap.classrooms[0].resource_id);
      setActiveStateVersion(bootstrap.classrooms[0].state_version ?? 0);
      setClassroomStateVersion(bootstrap.classrooms[0].state_version ?? 0);
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
    setClassroomStateVersion(classroom?.state_version ?? 0);
    try {
      const serverState = await getClassroomServerState(resourceId);
      const configuration = (serverState.configuration ?? {}) as {
        classroom_code?: string;
        display_name?: string;
        class_number?: number;
        description?: string;
        currency_name?: string;
        grade?: number;
        course_size?: CourseSize;
        student_count?: number;
        starts_on?: string;
        ends_on?: string;
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
        students?: Array<{
          resource_id?: string;
          attendance_number?: number;
          nickname?: string;
          role_code?: string | null;
          status?: string;
          state_version?: number;
          presence_status?: StudentRow["presence"];
          current_screen?: string;
          last_seen_at?: string;
        }>;
        salary_roles?: Array<{ role_code?: string; label?: string; gross_amount?: number; state_version?: number }>;
      };
      const economicPolicy = modulesState.policy_versions?.find(
        (policy) => policy.policy_area === "ECONOMIC"
      );
      const storedIncomeTaxBps = economicPolicy?.values?.income_tax_rate_bps;
      setSettings((current) => ({
        ...current,
        className: configuration.display_name ?? current.className,
        classNumber: configuration.class_number == null
          ? current.classNumber
          : String(configuration.class_number),
        description: configuration.description ?? "",
        classCode: configuration.classroom_code ?? current.classCode,
        currency: configuration.currency_name ?? current.currency,
        grade: configuration.grade == null ? current.grade : String(configuration.grade),
        period: configuration.course_size == null
          ? current.period
          : courseSizeToPeriod(configuration.course_size),
        studentCount: configuration.student_count == null
          ? current.studentCount
          : String(configuration.student_count),
        incomeTax: typeof storedIncomeTaxBps === "number"
          ? String(storedIncomeTaxBps / 100)
          : "0",
        startDate: configuration.starts_on ?? current.startDate,
        endDate: configuration.ends_on ?? current.endDate
      }));
      if (modulesState.modules) {
        setModules(modulesState.modules.flatMap((module) => {
          const names = module.module_code === "DEPOSIT"
            ? ["예금"]
            : module.module_code === "SAVINGS"
              ? ["적금"]
            : module.module_code === "INVESTMENT"
              ? ["단기 투자", "장기 투자"]
              : module.module_code === "LOAN"
                ? ["대출"]
                : [moduleNameByCode.get(module.module_code ?? "")
                  ?? module.label
                  ?? module.module_code
                  ?? "이름 없는 모듈"];
          return names.map((name) => ({
            name,
            // 설정만 남은 모듈은 학생에게 닫혀 있지만 교사에게는 구분해서 보여준다.
            status: module.status === "ACTIVE"
              ? "열림"
              : module.status === "PENDING_CONFIGURATION" ? "설정 대기" : "닫힘",
            studentState: module.status === "ACTIVE" ? "학생 이용 가능" : "학생 이용 중지",
            teacherTodo: "",
            resourceId: module.resource_id,
            code: module.module_code,
            stateVersion: module.state_version,
            version: module.version,
            config: module.config
          }));
        }));
      } else {
        setModules([]);
      }
      setPolicyVersions(modulesState.policy_versions ?? []);
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
      setClassroomStateVersion(configuration.state_version ?? classroom?.state_version ?? 0);
      setCoursePolicyVersion(
        modulesState.policy_versions?.find(
          (policy) => policy.policy_area === "COURSE_CONFIGURATION"
        )?.version ?? 0
      );
      setSalaryPolicyVersion(
        modulesState.policy_versions?.find(
          (policy) => policy.policy_area === "SALARY"
        )?.version ?? 0
      );
      if (rosterState.students) {
        const salaryByRole = new Map(rosterState.salary_roles?.map((role) => [role.role_code, role]) ?? []);
        setStudents(rosterState.students.map((student) => {
          const salaryRole = student.role_code ? salaryByRole.get(student.role_code) : undefined;
          return {
            resourceId: student.resource_id,
            attendance: String(student.attendance_number ?? ""),
            nickname: student.nickname ?? "",
            role: salaryRole?.label ?? student.role_code ?? "",
            salary: String(salaryRole?.gross_amount ?? 0),
            status: student.status ?? "정상",
            presence: student.presence_status ?? "UNKNOWN",
            currentScreen: student.current_screen ?? "",
            lastSeenAt: student.last_seen_at
          };
        }));
      } else {
        setStudents([]);
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
          setClassroomStateVersion(created.state_version ?? 0);
        }}
        onDone={(nextView) => {
          setHasClassroom(true);
          setHasDraft(false);
          setView(nextView);
          void openClassroom(activeClassroomId);
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
          {view === "overview" && (
            <Overview
              mode={mode}
              setMode={setMode}
              modules={modules}
              students={students}
              classroomId={activeClassroomId}
              stateVersion={classroomStateVersion}
              runAction={runAction}
              addLog={addLog}
              onPrepareLesson={() => setView("control")}
              onReload={() => openClassroom(activeClassroomId)}
            />
          )}
          {view === "setup" && (
            <Setup
              settings={settings}
              setSettings={setSettings}
              modules={modules}
              runAction={runAction}
              addLog={addLog}
              classroomId={activeClassroomId}
              stateVersion={classroomStateVersion}
              coursePolicyVersion={coursePolicyVersion}
              onReload={() => openClassroom(activeClassroomId)}
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
              stateVersion={classroomStateVersion}
              salaryPolicyVersion={salaryPolicyVersion}
              onReload={() => openClassroom(activeClassroomId)}
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
              policyVersions={policyVersions}
              activeStudentCount={students.length}
              effectiveTaxBps={Math.round(Number(settings.incomeTax || 0) * 100)}
              onReload={() => openClassroom(activeClassroomId)}
              onStateVersionChange={setActiveStateVersion}
            />
          )}
          {view === "finance" && (
            <FinanceAdmin
              classroomId={activeClassroomId}
              stateVersion={classroomStateVersion}
              realtimeRevision={realtimeRevision}
              currencyName={settings.currency}
              savingsAvailable={settings.period === "1학기 운영"}
              addLog={addLog}
            />
          )}
          {view === "quiz" && (
            <QuizDraftWorkspace
              classroomId={activeClassroomId}
              initialGrade={Math.min(6, Math.max(4, Number(settings.grade) || 5))}
              aiEnabled={aiSettings.enabled}
              students={students}
              activeQuizSessionId={activeQuizSessionId}
              onReload={() => openClassroom(activeClassroomId)}
            />
          )}
          {view === "control" && (
            <Control
              runAction={runAction}
              addLog={addLog}
              classroomId={activeClassroomId}
              stateVersion={classroomStateVersion}
              students={students}
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
              modules={modules}
              policyVersions={[]}
              startDate={settings.startDate}
              activeStudentCount={students.length}
              effectiveTaxBps={Math.round(Number(settings.incomeTax || 0) * 100)}
              quizSessionId={String(
                activeQuizSessionId
                ?? ""
              )}
              onStateVersionChange={setActiveStateVersion}
              onSettingsChanged={setAiSettings}
              onReload={() => openClassroom(activeClassroomId)}
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
        const normalizedSchoolCode = schoolCode.trim().toUpperCase();
        if (!/^[A-Z0-9-]{3,40}$/.test(normalizedSchoolCode)) {
          throw new Error("학교 코드는 영문 대문자, 숫자, 하이픈을 사용해 3~40자로 입력해 주세요.");
        }
        if (!displayName.trim()) {
          throw new Error("이름을 입력해 주세요.");
        }
        if (password.length < 4) {
          throw new Error("비밀번호는 4자 이상으로 입력해 주세요.");
        }
        await onRegister(normalizedSchoolCode, displayName.trim(), email.trim(), password);
      } else {
        await onLogin(email.trim(), password);
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
              <label>
                학교 코드
                <input required maxLength={40} value={schoolCode} onChange={(event) => setSchoolCode(event.target.value.toUpperCase())} placeholder="예: ECO-SEOUL" autoComplete="organization" />
                <small>학교에서 안내받은 코드가 필요합니다.</small>
              </label>
              <label>
                이름
                <input required maxLength={120} value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="이름" autoComplete="name" />
              </label>
            </>
          ) : null}
          <label>
            이메일
            <input required type="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="teacher@example.com" autoComplete="email" />
          </label>
          <label>
            비밀번호
            <input required type="password" minLength={4} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="4자 이상 입력" autoComplete={mode === "login" ? "current-password" : "new-password"} />
            {mode === "register" ? <small>4~72자로 입력해 주세요.</small> : null}
          </label>
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
  onCreated: (classroom: CreateClassroomResponse, precheck?: SetupPrecheckResponse) => void;
  onDone: (view: ViewKey) => void;
}>) {
  const [focusedModule, setFocusedModule] = useState("단기 투자");
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [creationError, setCreationError] = useState("");
  const [createdClassroom, setCreatedClassroom] = useState<CreateClassroomResponse | null>(null);
  const [setupPrecheck, setSetupPrecheck] = useState<SetupPrecheckResponse | null>(null);
  const [activationModules, setActivationModules] = useState<AiModuleRow[]>([]);
  const [activationPolicyVersions, setActivationPolicyVersions] = useState<AiPolicyVersionRow[]>([]);
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

  async function requestClassroomSetupPrecheck(classroomId: string) {
    return apiRequest<SetupPrecheckResponse>(
      `/classrooms/${classroomId}/ai/balancing-analysis-operations`,
      {
        method: "POST",
        body: JSON.stringify({
          analysis_mode: "SETUP_PRECHECK",
          trigger_context: {
            context_type: "CLASSROOM_SETUP",
            active_student_count: Math.max(Number(settings.studentCount) || 1, 1),
            system_amount_cap: 1_000_000,
            non_reward_issuance: 0,
            existing_reward_issuance: 0,
            expected_recovery: 0,
            other_periodic_issuance_per_student: 0,
            effective_tax_bps: Math.max(Math.round(Number(settings.incomeTax || 0) * 100), 0)
          },
          metric_scopes: []
        })
      }
    );
  }

  async function loadActivationState(classroomId: string) {
    const serverState = await getClassroomServerState(classroomId);
    const modulesAndPolicies = (serverState.modulesAndPolicies ?? {}) as {
      modules?: Array<{
        resource_id?: string;
        label?: string;
        module_code?: string;
        status?: string;
        state_version?: number;
        config?: Record<string, unknown>;
      }>;
      policy_versions?: AiPolicyVersionRow[];
    };
    setActivationModules((modulesAndPolicies.modules ?? []).map((module) => ({
      name: module.label ?? module.module_code ?? "이름 없는 모듈",
      status: module.status === "ACTIVE" ? "열림" : module.status === "PENDING_CONFIGURATION" ? "설정 대기" : "닫힘",
      code: module.module_code,
      resourceId: module.resource_id,
      stateVersion: module.state_version,
      config: module.config
    })));
    setActivationPolicyVersions(modulesAndPolicies.policy_versions ?? []);
  }

  async function createClassroom() {
    const studentCount = Number(settings.studentCount);
    if (!schoolId) {
      setCreationError("소속 학교 정보를 확인하지 못했어요. 다시 로그인해 주세요.");
      return;
    }
    if (!Number.isInteger(studentCount) || studentCount < 1) {
      setCreationError("예상 학생 수는 1명 이상으로 입력해 주세요.");
      return;
    }
    const supportedCodes = new Set<string>(MODULE_DEFINITIONS.map((module) => module.code));
    const moduleCodes = modules
      .filter((module) => module.status === "열림")
      .map((module) => moduleCodeByName[module.name])
      .filter((code): code is string => code !== undefined && supportedCodes.has(code));

    setSubmitting(true);
    setCreationError("");
    try {
      const classroom = await apiRequest<CreateClassroomResponse>("/classrooms", {
        method: "POST",
        body: JSON.stringify({
          expected_state_version: 0,
          screen_id: "SCR-WEB-003",
          ui_action_id: "UIA-CLS-001",
          client_occurred_at: new Date().toISOString(),
          school_id: schoolId,
          // develop이 추가한 학급 표시 정보다.
          display_name: settings.className,
          class_number: Number(settings.classNumber),
          description: settings.description,
          currency_name: settings.currency,
          grade: Number(settings.grade),
          course_size: periodToCourseSize(settings.period),
          starts_on: settings.startDate,
          ends_on: settings.endDate,
          student_count: studentCount,
          timezone: "Asia/Seoul",
          salary_policy: null,
          module_codes: moduleCodes,
          autonomous_schedules: []
        })
      });
      setCreatedClassroom(classroom);
      setSettings((current) => ({ ...current, classCode: classroom.classroom_code }));
      setShowConfirmation(false);
      setStep("complete");
      onCreated(classroom);
      try {
        const [precheck] = await Promise.all([
          requestClassroomSetupPrecheck(classroom.classroom_id),
          loadActivationState(classroom.classroom_id)
        ]);
        setSetupPrecheck(precheck);
        onCreated(classroom, precheck);
      } catch (error) {
        setCreationError(`교실은 만들어졌지만 시작 전 점검을 완료하지 못했어요. ${error instanceof Error ? error.message : ""}`);
      }
    } catch (error) {
      const code = error instanceof ApiError ? ` (${error.code})` : "";
      setCreationError(`경제교실을 개설하지 못했어요${code}. ${error instanceof Error ? error.message : ""}`);
    } finally {
      setSubmitting(false);
    }
  }

  async function retryClassroomSetupPrecheck() {
    if (!createdClassroom) return;
    setSubmitting(true);
    setCreationError("");
    try {
      const precheck = await requestClassroomSetupPrecheck(createdClassroom.classroom_id);
      setSetupPrecheck(precheck);
      onCreated(createdClassroom, precheck);
    } catch (error) {
      setCreationError(`시작 전 점검을 완료하지 못했어요. ${error instanceof Error ? error.message : ""}`);
    } finally {
      setSubmitting(false);
    }
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
                const unavailable = moduleName === "적금" && settings.period !== "1학기 운영";
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
                      disabled={unavailable}
                      title={unavailable ? "적금은 한 학기 운영에서만 사용할 수 있어요." : undefined}
                      onClick={() => {
                        if (!isRequired && !unavailable) toggleModule(moduleName);
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
          {setupPrecheck ? <SetupPrecheckSummary result={setupPrecheck} /> : null}
          {!setupPrecheck ? (
            <div className="inline-notice" role="alert">
              <span>{creationError || "학생 이용을 열기 전에 시작 전 점검이 필요해요."}</span>
              <button type="button" disabled={submitting || !createdClassroom} onClick={() => void retryClassroomSetupPrecheck()}>
                {submitting ? "점검 중" : "다시 점검"}
              </button>
            </div>
          ) : null}
          {createdClassroom && activationModules.length > 0 ? (
            <ModuleActivationPanel
              classroomId={createdClassroom.classroom_id}
              modules={activationModules}
              policyVersions={activationPolicyVersions}
              activeStudentCount={Math.max(Number(settings.studentCount) || 1, 1)}
              startDate={settings.startDate}
              canManage
              onReload={() => loadActivationState(createdClassroom.classroom_id)}
              onStateVersionChange={(stateVersion) => {
                setCreatedClassroom((current) => current ? { ...current, state_version: stateVersion } : current);
              }}
            />
          ) : null}
          <div className="next-task-list">
            <button type="button" onClick={() => onDone("roster")}>학생 명단 등록하기</button>
            <button type="button" onClick={() => onDone("control")}>수업 준비하기</button>
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
  addLog,
  runAction,
  classroomId
}: Readonly<{
  modules: ModuleRow[];
  setModules: React.Dispatch<React.SetStateAction<ModuleRow[]>>;
  addLog: (message: string, tone?: LogTone) => void;
  runAction: (label: string, path: string, init: RequestInit, onMockSuccess: () => void) => Promise<void>;
  classroomId: string;
}>) {
  const [selectedCourse, setSelectedCourse] = useState("생활 경제 기초");
  const [schedule, setSchedule] = useState(
    courseTemplates[0].schedule.map((item) => ({ ...item, lessonIds: [...item.lessonIds] }))
  );
  const [expandedDays, setExpandedDays] = useState<Set<number>>(new Set([0]));
  const [selectedQuizLessonId, setSelectedQuizLessonId] = useState<string>(lessonQuizCatalog[0].id);
  const [preparedQuizIds, setPreparedQuizIds] = useState<Set<string>>(new Set());
  const openModules = modules.filter(
    (module) => module.status === "열림" && studentAccessModuleNames.includes(module.name)
  );

  const lessonOptions = lessonQuizCatalog;
  const selectedQuizLesson = lessonQuizCatalog.find((lesson) => lesson.id === selectedQuizLessonId) ?? lessonQuizCatalog[0];

  function prepareQuiz() {
    void runAction(
      `${selectedQuizLesson.title} 퀴즈 저장`,
      `/classrooms/${classroomId}/quiz-question-commands`,
      {
        method: "POST",
        body: JSON.stringify({
          expectedStateVersion: 0,
          command: "CREATE",
          contentType: "ECONOMY",
          questionType: "MULTIPLE_CHOICE",
          prompt: selectedQuizLesson.question,
          options: selectedQuizLesson.options.map((option, index) => ({
            optionKey: String.fromCharCode(65 + index),
            optionText: option,
            isCorrect: index === selectedQuizLesson.answer,
            sortOrder: index + 1
          })),
          explanation: selectedQuizLesson.concept
        })
      },
      () => setPreparedQuizIds((current) => new Set([...current, selectedQuizLesson.id]))
    );
  }

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

      <PlainSection title="차시별 개념과 퀴즈">
        <div className="quiz-lesson-workspace">
          <div className="quiz-lesson-list" role="list">
            {lessonQuizCatalog.map((lesson) => (
              <button
                type="button"
                key={lesson.id}
                className={selectedQuizLesson.id === lesson.id ? "selected" : ""}
                onClick={() => setSelectedQuizLessonId(lesson.id)}
              >
                <span>{lesson.minutes}</span>
                <strong>{lesson.title}</strong>
                <small>{lesson.module}</small>
              </button>
            ))}
          </div>
          <div className="quiz-lesson-detail">
            <div className="quiz-concept">
              <span>{selectedQuizLesson.module} · {selectedQuizLesson.minutes}</span>
              <h3>{selectedQuizLesson.title}</h3>
              <p>{selectedQuizLesson.concept}</p>
            </div>
            <div className="quiz-question-preview">
              <span>학생에게 낼 문제</span>
              <h4>{selectedQuizLesson.question}</h4>
              <ol>
                {selectedQuizLesson.options.map((option, index) => (
                  <li className={index === selectedQuizLesson.answer ? "answer" : ""} key={option}>{option}</li>
                ))}
              </ol>
            </div>
            <div className="quiz-actions">
              <button type="button" onClick={prepareQuiz}>
                {preparedQuizIds.has(selectedQuizLesson.id) ? "문항 다시 저장" : "이 문항 저장"}
              </button>
              <button
                type="button"
                className="primary"
                disabled={!preparedQuizIds.has(selectedQuizLesson.id)}
                title="백엔드가 문항 버전 ID와 참가자 목록을 제공하면 학생 퀴즈 세션을 열 수 있어요."
                onClick={() => addLog("학생 퀴즈 세션 공개 API 연결이 필요해요.", "warning")}
              >
                학생에게 열기
              </button>
            </div>
          </div>
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
  setMode,
  modules,
  students,
  classroomId,
  stateVersion,
  runAction,
  addLog,
  onPrepareLesson,
  onReload
}: Readonly<{
  mode: string;
  setMode: React.Dispatch<React.SetStateAction<string>>;
  modules: ModuleRow[];
  students: StudentRow[];
  classroomId: string;
  stateVersion: number;
  runAction: (label: string, path: string, init: RequestInit, onMockSuccess: () => void) => Promise<void>;
  addLog: (message: string, tone?: LogTone) => void;
  onPrepareLesson: () => void;
  onReload: () => Promise<void>;
}>) {
  const openCount = modules.filter((module) => module.status === "열림").length;
  const onlineCount = students.filter((student) => student.presence === "ONLINE").length;
  const awayCount = students.filter((student) => student.presence === "AWAY").length;

  function setLessonMode() {
    const isFreeMode = mode === "자율 활동";
    void runAction(
      "수업 모드 전환",
      `/classrooms/${classroomId}/access-mode-commands`,
      {
        method: "POST",
        body: JSON.stringify({
          expected_state_version: stateVersion,
          command: isFreeMode ? "CLOSE" : "OPEN",
          duration_minutes: 40,
          client_occurred_at: new Date().toISOString()
        })
      },
      () => {
        setMode(isFreeMode ? "선생님 수업" : "자율 활동");
        void onReload();
      }
    );
  }

  function openStudentQuiz() {
    void openPublishedQuizForStudents({
      classroomId,
      stateVersion,
      students,
      runAction,
      addLog
    });
  }

  return (
    <div className="page-flow live-classroom-page">
      <PlainSection title="수업 진행">
        <div className="live-class-toolbar">
          <div>
            <span className={mode === "자율 활동" ? "mode-chip free" : "mode-chip class"}>{mode}</span>
            <p>접속 중 {onlineCount}명 · 화면 이탈 {awayCount}명 · 사용 기능 {openCount}개</p>
          </div>
          <button type="button" className={mode === "자율 활동" ? "live-toggle is-off" : "live-toggle"} onClick={setLessonMode}>
            <span aria-hidden="true" />
            {mode === "자율 활동" ? "수업 시작" : "수업 종료"}
          </button>
        </div>
        <div className="live-class-actions">
          <button type="button" onClick={onPrepareLesson}>수업 준비 열기</button>
          <button type="button" disabled title="학생 자료 화면 연동은 아직 준비 중이에요.">자료 띄우기</button>
          <button type="button" onClick={openStudentQuiz}>퀴즈 띄우기</button>
        </div>
      </PlainSection>
      <PlainSection title="학생 실시간 현황">
        <div className="live-student-list" role="list">
          {students.map((student) => (
            <div className="live-student-row" role="listitem" key={`${student.attendance}-${student.nickname}`}>
              <span className={`presence-dot ${(student.presence ?? "UNKNOWN").toLowerCase()}`} />
              <strong>{student.attendance}. {student.nickname}</strong>
              <span>{student.currentScreen || "대기 중"}</span>
              <em>{student.presence === "ONLINE" ? "접속 중" : student.presence === "AWAY" ? "화면 이탈" : "미접속"}</em>
            </div>
          ))}
        </div>
      </PlainSection>
    </div>
  );
}

function Setup({
  settings,
  setSettings,
  modules,
  runAction,
  addLog,
  classroomId,
  stateVersion,
  coursePolicyVersion,
  onReload
}: Readonly<{
  settings: ClassSettings;
  setSettings: React.Dispatch<React.SetStateAction<ClassSettings>>;
  modules: ModuleRow[];
  runAction: (label: string, path: string, init: RequestInit, onMockSuccess: () => void) => Promise<void>;
  addLog: (message: string, tone?: LogTone) => void;
  classroomId: string;
  stateVersion: number;
  coursePolicyVersion: number;
  onReload: () => Promise<void>;
}>) {
  function updateSettings(field: keyof ClassSettings, value: string) {
    setSettings((current) => ({ ...current, [field]: value }));
  }

  function toggleModule(moduleName: string) {
    const existing = modules.find((item) => item.name === moduleName);
    if (!existing?.code) {
      addLog(`${moduleName} 모듈이 이 교실에 생성되지 않았어요. 교실 생성 설정을 확인해 주세요.`, "error");
      return;
    }

    const activating = existing.status !== "열림";
    const path = activating
      ? `/classrooms/${classroomId}/modules/${existing.code}/activation-commands`
      : `/classrooms/${classroomId}/modules/${existing.code}/stop-commands`;
    const body = activating
      ? {
        expected_state_version: stateVersion,
        screen_id: "SCR-WEB-003",
        ui_action_id: "UIA-MOD-001",
        client_occurred_at: new Date().toISOString(),
        confirm_prerequisites: true,
        initial_policy_values: existing.code === "DEPOSIT" ? { rate_bps: 500 } : {},
        recommendation_action_ids: []
      }
      : {
        expected_state_version: stateVersion,
        screen_id: "SCR-WEB-003",
        ui_action_id: "UIA-MOD-002",
        client_occurred_at: new Date().toISOString(),
        reason: "교사 설정에서 비활성화"
      };

    void runAction(
      `${moduleName} 기능 ${activating ? "활성화" : "비활성화"}`,
      path,
      { method: "POST", body: JSON.stringify(body) },
      () => { void onReload(); }
    );
  }

  function saveCourseConfiguration() {
    const grade = Number(settings.grade);
    void runAction(
      "학급 설정 저장",
      `/classrooms/${classroomId}/course-configuration`,
      {
        method: "PUT",
        body: JSON.stringify({
          expected_state_version: stateVersion,
          screen_id: "SCR-WEB-003",
          ui_action_id: "UIA-CLS-002",
          client_occurred_at: new Date().toISOString(),
          grade,
          course_size: courseSizeFor(settings.period),
          starts_on: settings.startDate,
          ends_on: settings.endDate,
          recommended_course_id: null,
          difficulty_profile: `GRADE_${grade}_${grade === 4 ? "BASIC" : grade === 5 ? "STANDARD" : "ADVANCED"}`,
          expected_policy_version: coursePolicyVersion,
          classroom_code: settings.classCode,
          currency_name: settings.currency,
          display_name: settings.className,
          class_number: Number(settings.classNumber),
          description: settings.description
        })
      },
      () => { void onReload(); }
    );
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
            onClick={saveCourseConfiguration}
          >
            학급 설정 저장
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
            학년
            <input type="number" min="4" max="6" value={settings.grade} onChange={(event) => updateSettings("grade", event.target.value)} />
          </label>
          <label>
            반
            <input type="number" min="1" max="99" value={settings.classNumber} onChange={(event) => updateSettings("classNumber", event.target.value)} />
          </label>
          <label>
            학급 설명
            <input value={settings.description} maxLength={500} onChange={(event) => updateSettings("description", event.target.value)} />
          </label>
          <label>
            학생 정원
            <input value={settings.studentCount} readOnly aria-describedby="student-capacity-help" />
            <small id="student-capacity-help">학생 계정은 이 정원 내에서만 생성됩니다.</small>
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
          onClick={saveCourseConfiguration}
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
  hasSalaryModule,
  stateVersion,
  salaryPolicyVersion,
  onReload
}: Readonly<{
  settings: ClassSettings;
  students: StudentRow[];
  setStudents: React.Dispatch<React.SetStateAction<StudentRow[]>>;
  runAction: (label: string, path: string, init: RequestInit, onSuccess: (result: unknown) => void) => Promise<void>;
  addLog: (message: string, tone?: LogTone) => void;
  classroomId: string;
  hasSalaryModule: boolean;
  stateVersion: number;
  salaryPolicyVersion: number;
  onReload: () => Promise<void>;
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
    status: "정상",
    presence: "UNKNOWN"
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
    if (!draftStudent.nickname.trim()) {
      addLog("학생 이름을 입력해 주세요.", "warning");
      return;
    }

    setStudents((items) => items.map((item, itemIndex) => itemIndex === index ? draftStudent : item));
    cancelEdit();
    addLog(`${draftStudent.nickname} 학생 정보를 수정했어요.`);
  }

  function addStudent() {
    const trimmedNickname = newStudent.nickname.trim();
    const trimmedRole = newStudent.role.trim();

    if (!trimmedNickname) {
      addLog("학생 이름을 입력해야 추가할 수 있어요.", "warning");
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
      status: "정상",
      presence: "UNKNOWN"
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
    expected_policy_version: salaryPolicyVersion,
    expected_state_version: stateVersion,
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
  const onlineStudents = students.filter((student) => student.presence === "ONLINE");
  const awayStudents = students.filter((student) => student.presence === "AWAY");
  const offlineStudents = students.filter((student) => student.presence === "OFFLINE");
  const unknownStudents = students.filter((student) => !student.presence || student.presence === "UNKNOWN");

  function presenceLabel(presence: StudentRow["presence"]) {
    if (presence === "ONLINE") return "접속 중";
    if (presence === "AWAY") return "화면 이탈";
    if (presence === "OFFLINE") return "접속 종료";
    return "상태 확인 대기";
  }

  return (
    <div className="page-flow">
      <PlainSection title="학생 접속 상태">
        <div className="presence-summary">
          <div><span className="presence-dot online" /><b>{onlineStudents.length}</b><span>접속 중</span></div>
          <div><span className="presence-dot away" /><b>{awayStudents.length}</b><span>화면 이탈</span></div>
          <div><span className="presence-dot offline" /><b>{offlineStudents.length}</b><span>접속 종료</span></div>
          {unknownStudents.length > 0 ? <div><span className="presence-dot unknown" /><b>{unknownStudents.length}</b><span>연동 대기</span></div> : null}
        </div>
        <div className="presence-list">
          {students.map((student) => (
            <div className="presence-row" key={`presence-${student.attendance}-${student.nickname}`}>
              <span className={`presence-dot ${(student.presence ?? "UNKNOWN").toLowerCase()}`} />
              <b>{student.attendance}. {student.nickname}</b>
              <span>{student.currentScreen || "화면 정보 없음"}</span>
              <em>{presenceLabel(student.presence)}</em>
            </div>
          ))}
        </div>
        {unknownStudents.length === students.length ? <p className="presence-api-note">학생 접속 상태 API가 연결되면 이 목록이 자동으로 갱신됩니다.</p> : null}
      </PlainSection>
      <PlainSection title="학생 명단">
        <div className="roster-summary">
          <div>
            <span>현재 인원</span>
            <strong>{students.length}명</strong>
          </div>
          <div className="class-code-summary">
            <span>학생 접속 코드</span>
            <strong>{settings.classCode}</strong>
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
                  <input value={newStudent.nickname} onChange={(event) => updateNewStudent("nickname", event.target.value)} placeholder="학생이 로그인할 이름" />
                </label>
                {hasSalaryModule ? (
                  <>
                    <label>
                      직업 (선택)
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
            () => {
              addLog(hasSalaryModule ? "명단과 급여 정책을 저장했어요." : "학생 명단을 저장했어요.");
              void onReload();
            }
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
  policyVersions,
  activeStudentCount,
  effectiveTaxBps,
  onReload,
  onStateVersionChange
}: Readonly<{
  currencyName: string;
  products: ProductRow[];
  setProducts: React.Dispatch<React.SetStateAction<ProductRow[]>>;
  addLog: (message: string, tone?: LogTone) => void;
  classroomId: string;
  stateVersion: number;
  policyVersion: number;
  policyVersions: readonly AiPolicyVersionRow[];
  activeStudentCount: number;
  effectiveTaxBps: number;
  onReload: () => Promise<void>;
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
  const [inflationRate, setInflationRate] = useState("10");

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

  function applyInflation() {
    const rate = Number(inflationRate);
    if (!Number.isFinite(rate) || rate <= 0) return addLog("인상률을 0보다 큰 숫자로 입력해 주세요.", "warning");
    if (!window.confirm(`모든 상품 가격을 ${rate}% 올릴까요?`)) return;
    setProducts((items) => items.map((item) => ({ ...item, price: String(Math.round(Number(item.price) * (1 + rate / 100))) })));
    addLog(`상점 상품 가격을 ${rate}% 인상했어요.`);
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
        <div className="inflation-control">
          <div><b>인플레이션 적용</b><span>모든 상품 가격을 같은 비율로 올려요.</span></div>
          <label>인상률 <input inputMode="decimal" value={inflationRate} onChange={(event) => setInflationRate(event.target.value)} />%</label>
          <button type="button" onClick={applyInflation}>적용하기</button>
        </div>
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
      {/* 가격 추천은 상품을 등록·관리하는 이 자리에서 바로 받습니다. */}
      <DraftRecommendationPanel
        kind="product"
        classroomId={classroomId}
        products={products}
        policyVersions={policyVersions}
        activeStudentCount={activeStudentCount}
        effectiveTaxBps={effectiveTaxBps}
        onReload={onReload}
        onStateVersionChange={onStateVersionChange}
        onProductPriceApplied={(productId, nextPrice) => setProducts((items) => items.map((item) => (
          item.resourceId === productId ? { ...item, price: String(nextPrice) } : item
        )))}
      />
    </div>
  );
}

type FinancialProductDraft = {
  resourceId?: string;
  version: number;
  stateVersion: number;
  productType: "DEPOSIT" | "SAVINGS";
  name: string;
  status: string;
  termUnits: string;
  interestRate: string;
  earlyTerminationRate: string;
  installmentCount: string;
  installmentAmount: string;
  minimumAmount: string;
  maximumAmount: string;
};

type LoanPolicyDraft = {
  version: number;
  maximumPrincipal: string;
  interestRate: string;
  termWeeks: string;
  installmentCount: string;
  graceDays: string;
  onTimeCredit: string;
  overdueCredit: string;
  repaidCredit: string;
};

type LoanApplicationRow = {
  applicationId: string;
  studentId: string;
  studentNickname: string;
  requestedAmount: number;
  creditScore: number;
  creditLevel: string;
  status: string;
};

const defaultFinancialProducts: FinancialProductDraft[] = [
  { productType: "DEPOSIT", name: "1주 예금", status: "DRAFT", version: 0, stateVersion: 0, termUnits: "7", interestRate: "10", earlyTerminationRate: "1", installmentCount: "", installmentAmount: "", minimumAmount: "30", maximumAmount: "300" },
  { productType: "SAVINGS", name: "4주 적금", status: "DRAFT", version: 0, stateVersion: 0, termUnits: "4", interestRate: "15", earlyTerminationRate: "2", installmentCount: "4", installmentAmount: "20", minimumAmount: "20", maximumAmount: "80" }
];

function FinanceAdmin({
  classroomId,
  stateVersion,
  realtimeRevision,
  currencyName,
  savingsAvailable,
  addLog
}: Readonly<{
  classroomId: string;
  stateVersion: number;
  realtimeRevision: number;
  currencyName: string;
  savingsAvailable: boolean;
  addLog: (message: string, tone?: LogTone) => void;
}>) {
  const [activeTab, setActiveTab] = useState<"DEPOSIT" | "SAVINGS" | "LOAN" | "INVESTMENT">("DEPOSIT");
  const [products, setProducts] = useState<FinancialProductDraft[]>(defaultFinancialProducts);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [loanPolicy, setLoanPolicy] = useState<LoanPolicyDraft>({ version: 0, maximumPrincipal: "300", interestRate: "7", termWeeks: "4", installmentCount: "4", graceDays: "3", onTimeCredit: "10", overdueCredit: "-30", repaidCredit: "20" });
  const [loanApplications, setLoanApplications] = useState<LoanApplicationRow[]>([]);
  const [loanAmounts, setLoanAmounts] = useState<Record<string, string>>({});
  const [loanReasons, setLoanReasons] = useState<Record<string, string>>({});
  const [loanSaving, setLoanSaving] = useState(false);
  const [loanDecisionId, setLoanDecisionId] = useState<string | null>(null);
  const [investment, setInvestment] = useState({
    shortName: "단기 투자",
    shortReturnRate: "8",
    shortRisk: "보통",
    longName: "장기 투자",
    longVolatility: "보통",
    longPeriod: "5",
    shortScenario: "",
    longScenario: ""
  });

  useEffect(() => {
    if (!savingsAvailable && activeTab === "SAVINGS") setActiveTab("DEPOSIT");
  }, [activeTab, savingsAvailable]);

  useEffect(() => {
    if (!classroomId) return;
    let cancelled = false;
    void apiRequest<{ financial_products: Array<{
      resource_id: string;
      label: string;
      status: string;
      version: number;
      state_version: number;
      product_type: "DEPOSIT" | "SAVINGS";
      term_units: number;
      interest_rate_bps: number;
      early_termination_rate_bps: number;
      installment_count?: number | null;
      installment_amount?: number | null;
      minimum_amount: number;
      maximum_amount: number;
    }> }>(`/classrooms/${classroomId}/financial-products`)
      .then((response) => {
        if (cancelled || response.financial_products.length === 0) return;
        setProducts(response.financial_products.map((product) => ({
          resourceId: product.resource_id,
          version: product.version,
          stateVersion: product.state_version,
          productType: product.product_type,
          name: product.label,
          status: product.status,
          termUnits: String(product.product_type === "SAVINGS" ? product.term_units / 7 : product.term_units),
          interestRate: String(product.interest_rate_bps / 100),
          earlyTerminationRate: String(product.early_termination_rate_bps / 100),
          installmentCount: product.installment_count ? String(product.installment_count) : "",
          installmentAmount: product.installment_amount ? String(product.installment_amount) : "",
          minimumAmount: String(product.minimum_amount),
          maximumAmount: String(product.maximum_amount)
        })));
      })
      .catch(() => undefined);
    return () => { cancelled = true; };
  }, [classroomId]);

  useEffect(() => {
    if (!classroomId) return;
    let cancelled = false;
    void apiRequest<{
      policy: { version: number; maximum_principal: number; interest_rate_bps: number; term_units: number; installment_count: number; grace_units: number; on_time_credit: number; overdue_credit: number; repaid_credit: number };
      applications: Array<{ application_id: string; student_id: string; student_nickname: string; requested_amount: number; credit_score: number; credit_level: string; status: string }>;
    }>(`/classrooms/${classroomId}/loan-approval-queue`).then((response) => {
      if (cancelled) return;
      setLoanPolicy({
        version: response.policy.version,
        maximumPrincipal: String(response.policy.maximum_principal),
        interestRate: String(response.policy.interest_rate_bps / 100),
        termWeeks: String(response.policy.term_units / 7),
        installmentCount: String(response.policy.installment_count),
        graceDays: String(response.policy.grace_units),
        onTimeCredit: String(response.policy.on_time_credit),
        overdueCredit: String(response.policy.overdue_credit),
        repaidCredit: String(response.policy.repaid_credit)
      });
      setLoanApplications(response.applications.map((item) => ({
        applicationId: item.application_id,
        studentId: item.student_id,
        studentNickname: item.student_nickname,
        requestedAmount: item.requested_amount,
        creditScore: item.credit_score,
        creditLevel: item.credit_level,
        status: item.status
      })));
      setLoanAmounts(Object.fromEntries(response.applications.map((item) => [item.application_id, String(item.requested_amount)])));
    }).catch(() => undefined);
    return () => { cancelled = true; };
  }, [classroomId, realtimeRevision]);

  const activeProducts = products.filter((product) => product.productType === activeTab);

  function updateProduct(index: number, field: keyof FinancialProductDraft, value: string) {
    setProducts((items) => items.map((item, itemIndex) => {
      if (itemIndex !== index) return item;
      if (item.productType === "SAVINGS" && field === "termUnits") {
        return { ...item, termUnits: value, installmentCount: value };
      }
      return { ...item, [field]: value };
    }));
  }

  function addProduct(type: "DEPOSIT" | "SAVINGS") {
    setProducts((items) => [...items, {
      productType: type,
      name: type === "DEPOSIT" ? "새 예금 상품" : "새 적금 상품",
      status: "DRAFT",
      version: 0,
      stateVersion,
      termUnits: type === "DEPOSIT" ? "7" : "4",
      interestRate: "10",
      earlyTerminationRate: "1",
      installmentCount: type === "SAVINGS" ? "4" : "",
      installmentAmount: type === "SAVINGS" ? "20" : "",
      minimumAmount: "20",
      maximumAmount: "300"
    }]);
  }

  async function saveProduct(product: FinancialProductDraft, command: "CREATE" | "UPDATE" | "PUBLISH" | "STOP") {
    if (!classroomId) return addLog("먼저 교실을 선택해 주세요.", "warning");
    const term = Number(product.termUnits);
    const interest = Number(product.interestRate);
    const earlyInterest = Number(product.earlyTerminationRate);
    const minimum = Number(product.minimumAmount);
    const maximum = Number(product.maximumAmount);
    if (!product.name.trim() || !Number.isInteger(term) || term < 1 || !Number.isFinite(interest) || interest < 0 || interest > 100 || !Number.isFinite(earlyInterest) || earlyInterest < 0 || earlyInterest > 100 || !Number.isInteger(minimum) || minimum < 1 || !Number.isInteger(maximum) || maximum < minimum) {
      addLog("상품 이름과 금액, 금리를 다시 확인해 주세요.", "warning");
      return;
    }
    const installmentAmount = Number(product.installmentAmount);
    if (product.productType === "SAVINGS" && (term < 2 || term > 12 || Number(product.installmentCount) !== term || !Number.isInteger(installmentAmount) || installmentAmount < 1)) {
      addLog("적금 만기는 2~12주이며, 주 1회 납입액은 1 이상이어야 해요.", "warning");
      return;
    }
    const key = product.resourceId ?? `${product.productType}-${product.name}`;
    setSavingId(key);
    try {
      const response = await apiRequest<{ financial_resource_id: string; status: string; state_version: number }>(
        `/classrooms/${classroomId}/financial-product-commands`,
        {
          method: "POST",
          body: JSON.stringify({
            expected_state_version: stateVersion,
            screen_id: "SCR-TCH-FINANCE",
            ui_action_id: "UIA-FIN-001",
            client_occurred_at: new Date().toISOString(),
            command,
            product_id: product.resourceId ?? null,
            product_type: product.productType,
            name: product.name.trim(),
            term_units: product.productType === "SAVINGS" ? Number(product.termUnits) * 7 : Number(product.termUnits),
            interest_rate_bps: Math.round(Number(product.interestRate) * 100),
            early_termination_rate_bps: Math.round(Number(product.earlyTerminationRate) * 100),
            installment_count: product.productType === "SAVINGS" ? Number(product.installmentCount) : null,
            installment_amount: product.productType === "SAVINGS" ? Number(product.installmentAmount) : null,
            minimum_amount: Number(product.minimumAmount),
            maximum_amount: Number(product.maximumAmount),
            expected_product_version: product.version
          })
        }
      );
      setProducts((items) => items.map((item) => item === product ? {
        ...item,
        resourceId: response.financial_resource_id,
        status: response.status,
        stateVersion: response.state_version,
        version: item.version + 1
      } : item));
      addLog(`${product.name} ${command === "PUBLISH" ? "학생에게 공개" : command === "STOP" ? "판매 중지" : "저장"} 완료`);
    } catch (error) {
      addLog(`${product.name} 저장에 실패했어요. ${error instanceof Error ? error.message : ""}`, "error");
    } finally {
      setSavingId(null);
    }
  }

  function updateLoanPolicy(field: Exclude<keyof LoanPolicyDraft, "version">, value: string) {
    setLoanPolicy((current) => ({ ...current, [field]: value }));
  }

  async function saveLoanPolicy() {
    if (!classroomId) return addLog("먼저 교실을 선택해 주세요.", "warning");
    const [maximum, interest, weeks, installmentCount, grace, onTime, overdue, repaid] = [loanPolicy.maximumPrincipal, loanPolicy.interestRate, loanPolicy.termWeeks, loanPolicy.installmentCount, loanPolicy.graceDays, loanPolicy.onTimeCredit, loanPolicy.overdueCredit, loanPolicy.repaidCredit].map(Number);
    const integers = [maximum, weeks, installmentCount, grace, onTime, overdue, repaid];
    if (integers.some((value) => !Number.isInteger(value)) || !Number.isFinite(interest) || maximum < 1 || interest < 0 || interest > 20 || weeks < 1 || weeks > 12 || installmentCount < 1 || installmentCount > 12 || installmentCount > weeks * 7 || grace < 0 || grace > 7 || onTime < 0 || onTime > 100 || overdue < -100 || overdue > 0 || repaid < 0 || repaid > 100) {
      addLog("대출 정책의 숫자를 다시 확인해 주세요.", "warning");
      return;
    }
    setLoanSaving(true);
    try {
      const response = await apiRequest<{ version: number }>(`/classrooms/${classroomId}/loan-policy`, {
        method: "PUT",
        body: JSON.stringify({
          maximum_principal: Number(loanPolicy.maximumPrincipal),
          interest_rate_bps: Math.round(Number(loanPolicy.interestRate) * 100),
          term_units: Math.round(Number(loanPolicy.termWeeks) * 7),
          installment_count: Number(loanPolicy.installmentCount),
          grace_units: Number(loanPolicy.graceDays),
          on_time_credit: Number(loanPolicy.onTimeCredit),
          overdue_credit: Number(loanPolicy.overdueCredit),
          repaid_credit: Number(loanPolicy.repaidCredit),
          expected_policy_version: loanPolicy.version,
          screen_id: "SCR-WEB-016",
          ui_action_id: "UIA-FIN-010",
          client_occurred_at: new Date().toISOString()
        })
      });
      setLoanPolicy((current) => ({ ...current, version: response.version }));
      addLog("새 대출 정책을 저장했어요. 이후 신청부터 적용됩니다.");
    } catch (error) {
      addLog(`대출 정책 저장에 실패했어요. ${error instanceof Error ? error.message : ""}`, "error");
    } finally {
      setLoanSaving(false);
    }
  }

  async function decideLoan(application: LoanApplicationRow, decision: "APPROVE" | "REJECT") {
    if (!classroomId) return;
    const approvedAmount = Number(loanAmounts[application.applicationId] ?? application.requestedAmount);
    if (decision === "APPROVE" && (!Number.isInteger(approvedAmount) || approvedAmount < Number(loanPolicy.installmentCount) || approvedAmount > application.requestedAmount)) {
      addLog(`승인 금액은 ${loanPolicy.installmentCount} 이상, 신청 금액 이내로 입력해 주세요.`, "warning");
      return;
    }
    setLoanDecisionId(application.applicationId);
    try {
      await apiRequest(`/classrooms/${classroomId}/loan-applications/${application.applicationId}/teacher-decisions`, {
        method: "POST",
        body: JSON.stringify({ decision, approved_amount: decision === "APPROVE" ? approvedAmount : null, reason: loanReasons[application.applicationId] ?? "" })
      });
      setLoanApplications((items) => items.filter((item) => item.applicationId !== application.applicationId));
      addLog(`${application.studentNickname} 학생의 대출을 ${decision === "APPROVE" ? "승인하고 지급했어요" : "거절했어요"}.`);
    } catch (error) {
      addLog(`대출 처리에 실패했어요. ${error instanceof Error ? error.message : ""}`, "error");
    } finally {
      setLoanDecisionId(null);
    }
  }

  return (
    <div className="page-flow finance-page">
      <PlainSection title="운영 관리" description="학생에게 보여 줄 금융 상품과 투자 활동 조건을 정합니다.">
        <div className="finance-tabs" role="tablist" aria-label="운영 관리 종류">
          <button type="button" role="tab" aria-selected={activeTab === "DEPOSIT"} className={activeTab === "DEPOSIT" ? "active" : ""} onClick={() => setActiveTab("DEPOSIT")}>예금</button>
          <button type="button" role="tab" disabled={!savingsAvailable} title={!savingsAvailable ? "적금은 한 학기 운영에서만 사용할 수 있어요." : undefined} aria-selected={activeTab === "SAVINGS"} className={activeTab === "SAVINGS" ? "active" : ""} onClick={() => setActiveTab("SAVINGS")}>적금</button>
          <button type="button" role="tab" aria-selected={activeTab === "LOAN"} className={activeTab === "LOAN" ? "active" : ""} onClick={() => setActiveTab("LOAN")}>대출·신용</button>
          <button type="button" role="tab" aria-selected={activeTab === "INVESTMENT"} className={activeTab === "INVESTMENT" ? "active" : ""} onClick={() => setActiveTab("INVESTMENT")}>투자</button>
        </div>
      </PlainSection>

      {activeTab === "INVESTMENT" ? (
        <PlainSection title="투자 운영안" description="단기는 다음 날 확정 결과를, 장기는 기간 동안 변동하는 시장 소식을 제공합니다.">
          <div className="investment-mode-grid">
            <section>
              <div className="investment-heading"><span>단기 투자</span><small>다음 날 결과 확정</small></div>
              <div className="investment-settings">
                <label>상품 이름<input value={investment.shortName} onChange={(event) => setInvestment((current) => ({ ...current, shortName: event.target.value }))} /></label>
                <label>결과 수익률(%)<input inputMode="decimal" value={investment.shortReturnRate} onChange={(event) => setInvestment((current) => ({ ...current, shortReturnRate: event.target.value }))} /></label>
                <label>위험도<select value={investment.shortRisk} onChange={(event) => setInvestment((current) => ({ ...current, shortRisk: event.target.value }))}><option>낮음</option><option>보통</option><option>높음</option></select></label>
              </div>
              <button type="button" onClick={() => setInvestment((current) => ({ ...current, shortScenario: `${current.shortRisk} 위험도인 ${current.shortName}: 내일 ${current.shortReturnRate}% 결과가 확정되는 투자 시나리오` }))}>AI 시나리오 생성</button>
              {investment.shortScenario ? <p className="ai-scenario">{investment.shortScenario}</p> : null}
            </section>
            <section>
              <div className="investment-heading"><span>장기 투자</span><small>매일 시장 소식 제공</small></div>
              <div className="investment-settings">
                <label>상품 이름<input value={investment.longName} onChange={(event) => setInvestment((current) => ({ ...current, longName: event.target.value }))} /></label>
                <label>변동성<select value={investment.longVolatility} onChange={(event) => setInvestment((current) => ({ ...current, longVolatility: event.target.value }))}><option>낮음</option><option>보통</option><option>높음</option></select></label>
                <label>운영 기간(일)<input inputMode="numeric" value={investment.longPeriod} onChange={(event) => setInvestment((current) => ({ ...current, longPeriod: event.target.value }))} /></label>
              </div>
              <button type="button" onClick={() => setInvestment((current) => ({ ...current, longScenario: `${current.longPeriod}일 동안 ${current.longVolatility} 변동성으로 매일 새로운 시장 소식을 제공하는 ${current.longName}` }))}>AI 상품 생성</button>
              {investment.longScenario ? <p className="ai-scenario">{investment.longScenario}</p> : null}
            </section>
          </div>
          <button type="button" className="primary-save" onClick={() => addLog("투자 운영안을 저장했어요. 투자 상품 API가 연결되면 학생 화면에도 공개됩니다.", "warning")}>투자 운영안 저장</button>
        </PlainSection>
      ) : activeTab === "LOAN" ? (
        <div className="page-flow">
          <PlainSection title="대출 정책" description="저장한 값은 이후의 새 신청부터 적용됩니다. 기간은 주 단위이며 회차별 자동 상환됩니다.">
            <div className="financial-product-editor">
              <div className="financial-product-heading"><strong>정책 버전 {loanPolicy.version || "기본"}</strong><span>대출·신용</span></div>
              <div className="financial-fields">
                <label>최대 대출 한도({currencyName})<input type="number" min="1" value={loanPolicy.maximumPrincipal} onChange={(event) => updateLoanPolicy("maximumPrincipal", event.target.value)} /></label>
                <label>전체 기간 금리(%, 0~20)<input type="number" min="0" max="20" step="0.01" value={loanPolicy.interestRate} onChange={(event) => updateLoanPolicy("interestRate", event.target.value)} /></label>
                <label>대출 기간(주, 1~12)<input type="number" min="1" max="12" value={loanPolicy.termWeeks} onChange={(event) => updateLoanPolicy("termWeeks", event.target.value)} /></label>
                <label>상환 횟수(1~12)<input type="number" min="1" max="12" value={loanPolicy.installmentCount} onChange={(event) => updateLoanPolicy("installmentCount", event.target.value)} /></label>
                <label>연체 재상환 유예(일, 0~7)<input type="number" min="0" max="7" value={loanPolicy.graceDays} onChange={(event) => updateLoanPolicy("graceDays", event.target.value)} /></label>
                <label>정시 상환 신용점수(0~100)<input type="number" min="0" max="100" value={loanPolicy.onTimeCredit} onChange={(event) => updateLoanPolicy("onTimeCredit", event.target.value)} /></label>
                <label>연체 신용점수(-100~0)<input type="number" min="-100" max="0" value={loanPolicy.overdueCredit} onChange={(event) => updateLoanPolicy("overdueCredit", event.target.value)} /></label>
                <label>완납 신용점수(0~100)<input type="number" min="0" max="100" value={loanPolicy.repaidCredit} onChange={(event) => updateLoanPolicy("repaidCredit", event.target.value)} /></label>
              </div>
              <p className="helper-text">신용 750점 이상은 한도 100%, 600점 이상은 80%, 450점 이상은 60%를 신청할 수 있습니다. 450점 미만은 신규 신청이 제한됩니다.</p>
              <div className="financial-actions"><button type="button" disabled={loanSaving} onClick={() => void saveLoanPolicy()}>{loanSaving ? "저장 중" : "대출 정책 저장"}</button></div>
            </div>
          </PlainSection>
          <PlainSection title="대출 승인 대기" description="학생의 신청 금액과 신용점수를 확인한 뒤 승인 금액을 정할 수 있습니다.">
            <div className="financial-product-list">
              {loanApplications.length === 0 ? <p className="empty-state">현재 승인 대기 중인 신청이 없습니다.</p> : loanApplications.map((application) => (
                <div className="financial-product-editor" key={application.applicationId}>
                  <div className="financial-product-heading"><strong>{application.studentNickname}</strong><span>신용 {application.creditScore}점 · {creditLevelLabels[application.creditLevel] ?? application.creditLevel}</span></div>
                  <div className="financial-fields">
                    <label>신청 금액({currencyName})<input value={application.requestedAmount} readOnly /></label>
                    <label>승인 금액({currencyName})<input inputMode="numeric" value={loanAmounts[application.applicationId] ?? application.requestedAmount} onChange={(event) => setLoanAmounts((current) => ({ ...current, [application.applicationId]: event.target.value }))} /></label>
                    <label>승인·거절 사유<input value={loanReasons[application.applicationId] ?? ""} onChange={(event) => setLoanReasons((current) => ({ ...current, [application.applicationId]: event.target.value }))} placeholder="선택 입력" /></label>
                  </div>
                  <div className="financial-actions">
                    <button type="button" disabled={loanDecisionId === application.applicationId} onClick={() => void decideLoan(application, "APPROVE")}>승인·지급</button>
                    <button type="button" className="ghost" disabled={loanDecisionId === application.applicationId} onClick={() => void decideLoan(application, "REJECT")}>거절</button>
                  </div>
                </div>
              ))}
            </div>
          </PlainSection>
        </div>
      ) : (
        <PlainSection
          title={activeTab === "DEPOSIT" ? "예금 상품" : "적금 상품"}
          description="저장한 상품은 초안 상태이며, 학생에게 보여 주려면 공개 버튼을 누릅니다."
        >
          <div className="financial-product-list">
            {activeProducts.map((product) => {
              const index = products.indexOf(product);
              const busy = savingId === (product.resourceId ?? `${product.productType}-${product.name}`);
              return (
                <div className="financial-product-editor" key={product.resourceId ?? `${product.productType}-${index}`}>
                  <div className="financial-product-heading"><strong>{product.status === "PUBLISHED" ? "학생 공개 중" : "초안"}</strong><span>{product.productType === "DEPOSIT" ? "예금" : "적금"}</span></div>
                  <div className="financial-fields">
                    <label>상품 이름<input value={product.name} onChange={(event) => updateProduct(index, "name", event.target.value)} /></label>
                    <label>{product.productType === "SAVINGS" ? "적금 만기(주, 2~12)" : "예금 만기(일)"}<input type="number" min={product.productType === "SAVINGS" ? 2 : 1} max={product.productType === "SAVINGS" ? 12 : undefined} value={product.termUnits} onChange={(event) => updateProduct(index, "termUnits", event.target.value)} /></label>
                    <label>이자율(%)<input inputMode="decimal" value={product.interestRate} onChange={(event) => updateProduct(index, "interestRate", event.target.value)} /></label>
                    <label>중도 해지 이자율(%)<input inputMode="decimal" value={product.earlyTerminationRate} onChange={(event) => updateProduct(index, "earlyTerminationRate", event.target.value)} /></label>
                    {product.productType === "SAVINGS" ? <label>납입 횟수(주 1회 자동)<input type="number" value={product.installmentCount} readOnly /></label> : null}
                    {product.productType === "SAVINGS" ? <label>한 번 납입액({currencyName})<input inputMode="numeric" value={product.installmentAmount} onChange={(event) => updateProduct(index, "installmentAmount", event.target.value)} /></label> : null}
                    <label>최소 금액({currencyName})<input inputMode="numeric" value={product.minimumAmount} onChange={(event) => updateProduct(index, "minimumAmount", event.target.value)} /></label>
                    <label>최대 금액({currencyName})<input inputMode="numeric" value={product.maximumAmount} onChange={(event) => updateProduct(index, "maximumAmount", event.target.value)} /></label>
                  </div>
                  <div className="financial-actions">
                    <button type="button" disabled={busy} onClick={() => void saveProduct(product, product.resourceId ? "UPDATE" : "CREATE")}>{busy ? "저장 중" : "저장"}</button>
                    <button type="button" disabled={busy || !product.resourceId} onClick={() => void saveProduct(product, "PUBLISH")}>학생에게 공개</button>
                    <button type="button" className="ghost" disabled={busy || !product.resourceId || product.status !== "PUBLISHED"} onClick={() => void saveProduct(product, "STOP")}>공개 중지</button>
                  </div>
                </div>
              );
            })}
          </div>
          <button type="button" className="add-financial-product" onClick={() => addProduct(activeTab)}>+ {activeTab === "DEPOSIT" ? "예금" : "적금"} 상품 추가</button>
        </PlainSection>
      )}
    </div>
  );
}

function Control({
  runAction,
  addLog,
  classroomId,
  stateVersion,
  students
}: Readonly<{
  runAction: (label: string, path: string, init: RequestInit, onMockSuccess: () => void) => Promise<void>;
  addLog: (message: string, tone?: LogTone) => void;
  classroomId: string;
  stateVersion: number;
  students: StudentRow[];
}>) {
  const [lessonMaterials, setLessonMaterials] = useState<LessonMaterial[]>(initialLessonMaterials);
  const [editingLessonId, setEditingLessonId] = useState<string | null>(null);
  const [draftLesson, setDraftLesson] = useState<LessonMaterial | null>(null);
  const [newQuizTitle, setNewQuizTitle] = useState("");

  function startLessonEdit(material: LessonMaterial) {
    setEditingLessonId(material.id);
    setDraftLesson(material);
  }

  function updateLessonDraft(field: keyof LessonMaterial, value: string) {
    setDraftLesson((current) => current ? { ...current, [field]: value } : current);
  }

  function selectLessonTemplate(title: string) {
    const selected = lessonQuizCatalog.find((lesson) => lesson.title === title);
    if (!selected) return;
    setDraftLesson((current) => current ? {
      ...current,
      title: selected.title,
      minutes: selected.minutes.replace("분", ""),
      status: "준비 완료"
    } : current);
  }

  function saveLesson() {
    if (!draftLesson) return;
    setLessonMaterials((items) => items.map((item) => item.id === draftLesson.id ? draftLesson : item));
    setEditingLessonId(null);
    setDraftLesson(null);
    addLog("수업 자료를 수정했어요.");
  }

  function addLesson() {
    const source = lessonQuizCatalog[0];
    setLessonMaterials((items) => [...items, {
      id: crypto.randomUUID(),
      stage: `${items.length + 1}차시`,
      title: source.title,
      minutes: source.minutes.replace("분", ""),
      status: "준비 중"
    }]);
  }

  function removeLesson(id: string) {
    setLessonMaterials((items) => items.filter((item) => item.id !== id));
    addLog("차시를 수업 준비에서 뺐어요.");
  }

  function openQuizForStudents() {
    void openPublishedQuizForStudents({
      classroomId,
      stateVersion,
      students,
      runAction,
      addLog
    });
  }

  function createQuiz() {
    if (!newQuizTitle.trim()) return addLog("퀴즈 제목을 입력해 주세요.", "warning");
    addLog(`${newQuizTitle} 퀴즈 초안을 만들었어요.`);
    setNewQuizTitle("");
  }

  return (
    <div className="page-flow">
      <PlainSection title="수업 준비" description="차시를 추가하고, 준비된 자료와 퀴즈를 학생 화면에 바로 열 수 있어요.">
        <div className="lesson-list">
          {lessonMaterials.map((material) => {
            const activeDraft = editingLessonId === material.id ? draftLesson : null;
            const template = lessonQuizCatalog.find((lesson) => lesson.title === material.title);

            return (
              <div className="lesson-row" key={material.id}>
                {activeDraft ? (
                  <>
                    <select value={activeDraft.stage} onChange={(event) => updateLessonDraft("stage", event.target.value)}>
                      {Array.from({ length: Math.max(lessonMaterials.length, 6) }, (_, index) => `${index + 1}교시`).map((stage) => (
                        <option key={stage} value={stage}>{stage}</option>
                      ))}
                    </select>
                    <select value={activeDraft.title} onChange={(event) => selectLessonTemplate(event.target.value)}>
                      {lessonQuizCatalog.map((lesson) => (
                        <option key={lesson.id} value={lesson.title}>{lesson.title}</option>
                      ))}
                    </select>
                    <span>{activeDraft.minutes}분</span>
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
                      <button type="button" className="ghost" disabled title="학생 자료 화면 연동은 아직 준비 중이에요.">자료 띄우기</button>
                      <button type="button" className="ghost" onClick={openQuizForStudents}>퀴즈 열기</button>
                      <button type="button" className="danger-text" onClick={() => removeLesson(material.id)}>삭제</button>
                    </div>
                  </>
                )}
                {!activeDraft && template ? <div className="lesson-template-preview"><b>{template.module}</b><span>{template.concept}</span><em>퀴즈: {template.question}</em></div> : null}
              </div>
            );
          })}
        </div>
        <button type="button" className="add-financial-product" onClick={addLesson}>+ 차시 추가</button>
      </PlainSection>
      <PlainSection title="새 퀴즈 만들기">
        <div className="new-quiz-line">
          <input value={newQuizTitle} onChange={(event) => setNewQuizTitle(event.target.value)} placeholder="예: 2차시 예금 개념 확인" />
          <button type="button" onClick={createQuiz}>퀴즈 초안 만들기</button>
        </div>
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
        {pendingApprovals.length === 0 ? <p className="empty-copy">승인할 항목이 없어요.</p> : (
          <div className="approval-table-scroll"><table className="approval-table"><thead><tr><th>구분</th><th>내용</th><th>금액</th><th>상태</th><th>처리</th></tr></thead><tbody>
          {pendingApprovals.map((item) => (
            <tr key={item.id}>
              <td><b>{item.type}</b></td>
              <td>{item.target}</td>
              <td>{item.amount}{currencyName}</td>
              <td>{item.status}</td>
              <td>
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
              </td>
            </tr>
          ))}
          </tbody></table></div>
        )}
      </PlainSection>
      {processedApprovals.length > 0 ? (
        <PlainSection title="처리 내역">
          <div className="approval-table-scroll"><table className="approval-table"><thead><tr><th>구분</th><th>내용</th><th>금액</th><th>처리 상태</th></tr></thead><tbody>
            {processedApprovals.map((item) => (
              <tr key={item.id}><td><b>{item.type}</b></td><td>{item.target}</td><td>{item.amount}</td><td><strong className="approval-result">{item.status}</strong></td></tr>
            ))}
          </tbody></table></div>
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
