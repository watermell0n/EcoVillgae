import { apiRequest } from "@/api/client";

export type ClassroomModule = {
  resourceId: string;
  label: string;
  status: string;
  version: number;
  stateVersion: number;
  code: string;
  config: Record<string, unknown>;
};

type ModuleResponse = {
  resource_id?: string;
  label?: string;
  status?: string;
  version?: number;
  state_version?: number;
  module_code?: string;
  config?: Record<string, unknown>;
};

type ModulesAndPoliciesResponse = {
  modules: ModuleResponse[];
  policy_versions: Array<{
    resource_id?: string;
    policy_area?: string;
    version?: number;
    state_version?: number;
    values?: Record<string, unknown>;
  }>;
};

export type ClassroomAccess = {
  modules: ClassroomModule[];
  activeCodes: Set<string>;
  // 선생님이 학생 이용만 잠근 모듈입니다. 메뉴는 보이지만 들어갈 수 없습니다.
  suspendedCodes: Set<string>;
  resources: {
    quizSessionId?: string;
    voteId?: string;
    guidedActivityId?: string;
  };
  mode: "CLOSED" | "AUTONOMOUS" | "GUIDED";
  investmentModes: {
    shortTerm: boolean;
    longTerm: boolean;
  };
};

const useMocks = process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES === "true";

const mockModules: ClassroomModule[] = [
  { resourceId: "mock-wallet", label: "지갑", status: "ACTIVE", version: 1, stateVersion: 1, code: "WALLET", config: {} },
  { resourceId: "mock-store", label: "상점", status: "ACTIVE", version: 1, stateVersion: 1, code: "STORE", config: {} },
  { resourceId: "mock-character", label: "캐릭터", status: "ACTIVE", version: 1, stateVersion: 1, code: "CHARACTER", config: {} },
  { resourceId: "mock-deposit", label: "예금", status: "ACTIVE", version: 1, stateVersion: 1, code: "DEPOSIT", config: {} },
  { resourceId: "mock-savings", label: "적금", status: "ACTIVE", version: 1, stateVersion: 1, code: "SAVINGS", config: {} },
  { resourceId: "mock-investment", label: "투자", status: "ACTIVE", version: 1, stateVersion: 1, code: "INVESTMENT", config: {} },
  { resourceId: "mock-trade", label: "개인 거래", status: "ACTIVE", version: 1, stateVersion: 1, code: "TRADE", config: {} },
  { resourceId: "mock-quiz", label: "퀴즈", status: "ACTIVE", version: 1, stateVersion: 1, code: "QUIZ_GAME", config: { active_quiz_session_id: "mock-quiz-session" } },
  { resourceId: "mock-vote", label: "공공예산·투표", status: "ACTIVE", version: 1, stateVersion: 1, code: "PUBLIC_BUDGET", config: { active_vote_id: "mock-vote" } },
  { resourceId: "mock-paystub", label: "월급명세서", status: "ACTIVE", version: 1, stateVersion: 1, code: "PAYSLIP", config: {} },
  { resourceId: "mock-real-estate", label: "교실 부동산", status: "ACTIVE", version: 1, stateVersion: 1, code: "REAL_ESTATE", config: {} },
  { resourceId: "mock-housing-subscription", label: "청약 통장", status: "ACTIVE", version: 1, stateVersion: 1, code: "HOUSING_SUBSCRIPTION", config: {} },
  { resourceId: "mock-credit", label: "신용 기초", status: "ACTIVE", version: 1, stateVersion: 1, code: "CREDIT", config: {} }
];

function firstConfiguredId(modules: ClassroomModule[], keys: string[]) {
  for (const module of modules) {
    for (const key of keys) {
      const value = module.config[key];
      if (typeof value === "string" && value.trim()) return value;
    }
  }
  return undefined;
}

/**
 * 선생님이 쓰겠다고 고른 투자 방식을 읽습니다.
 *
 * 학급은 단기와 장기를 함께 운영할 수 있어 서버는 investment_modes 목록을 줍니다. 목록이
 * 생기기 전에 활성화된 학급은 investment_mode 단일 값만 갖고 있어 그 값도 함께 읽습니다.
 */
function readInvestmentModes(config: Record<string, unknown> | undefined) {
  const raw = config?.investment_modes;
  const modes = Array.isArray(raw)
    ? raw.map((value) => String(value))
    : typeof config?.investment_mode === "string" ? [config.investment_mode] : [];
  return {
    shortTerm: modes.includes("SHORT_TERM"),
    longTerm: modes.includes("LONG_TERM")
  };
}

function createAccess(
  modules: ClassroomModule[],
  policies: ModulesAndPoliciesResponse["policy_versions"] = []
): ClassroomAccess {
  // 대출 중지를 요청해도 미납 회차 정리가 끝날 때까지 학생이 상환 화면에는 들어갈 수 있어야 합니다.
  // 신규 신청은 백엔드가 ACTIVE 상태에서만 허용합니다.
  const activeModules = modules.filter((module) => (
    module.status === "ACTIVE" || (module.code === "LOAN" && module.status === "STOPPING")
  ));
  const accessPolicy = [...policies]
    .filter((policy) => policy.policy_area === "ACCESS_MODE")
    .sort((left, right) => (right.version ?? 0) - (left.version ?? 0))[0];
  const policyMode = accessPolicy?.values?.mode;
  const guidedActivityId = firstConfiguredId(activeModules, ["active_guided_activity_id", "guided_activity_id", "activity_id"]);
  const investment = activeModules.find((module) => module.code === "INVESTMENT");
  const investmentModes = readInvestmentModes(investment?.config);
  return {
    modules: activeModules,
    activeCodes: new Set(activeModules.map((module) => module.code)),
    suspendedCodes: new Set(
      activeModules
        .filter((module) => module.config.student_access_suspended === true)
        .map((module) => module.code)
    ),
    resources: {
      quizSessionId: firstConfiguredId(activeModules, ["active_quiz_session_id", "quiz_session_id"]),
      voteId: firstConfiguredId(activeModules, ["active_vote_id", "vote_id"]),
      guidedActivityId
    },
    mode: guidedActivityId
      ? "GUIDED"
      : policyMode === "CLOSED" ? "CLOSED" : "AUTONOMOUS",
    investmentModes
  };
}

export async function fetchClassroomAccess(classroomId: string): Promise<ClassroomAccess> {
  if (useMocks) return createAccess(mockModules);
  // 서버가 학생에게 보내 주는 config에는 active_quiz_session_id와 student_access_suspended가 함께 담깁니다.

  const response = await apiRequest<ModulesAndPoliciesResponse>(`/classrooms/${classroomId}/modules-and-policies`);
  return createAccess(response.modules.map((module) => ({
    resourceId: module.resource_id ?? module.module_code ?? "unknown-module",
    label: module.label ?? module.module_code ?? "모듈",
    status: module.status ?? "DISABLED",
    version: module.version ?? 0,
    stateVersion: module.state_version ?? 0,
    code: module.module_code ?? "UNKNOWN",
    config: module.config ?? {}
  })), response.policy_versions);
}

// 코드는 backend ModuleCatalog의 정본 코드와 레거시 별칭을 함께 담습니다.
export const moduleGroups = {
  wallet: ["WALLET"],
  shop: ["STORE", "PHYSICAL_STORE"],
  character: ["CHARACTER"],
  bank: ["DEPOSIT", "SAVINGS", "LOAN"],
  investment: ["INVESTMENT", "SHORT_INVESTMENT", "LONG_INVESTMENT", "INVESTMENT_SHORT_TERM", "INVESTMENT_LONG_TERM"],
  trade: ["TRADE"],
  quiz: ["QUIZ_GAME", "QUIZ_TEST", "QUIZ"],
  meeting: ["PUBLIC_BUDGET", "PUBLIC_FUND", "BUDGET"],
  paystub: ["PAYSLIP", "INCOME"],
  realEstate: ["REAL_ESTATE", "HOUSING_SUBSCRIPTION"],
  subscription: ["HOUSING_SUBSCRIPTION"],
  credit: ["CREDIT"]
} as const;

export type ModuleGroup = keyof typeof moduleGroups;

export function hasModule(access: ClassroomAccess | undefined, group: ModuleGroup) {
  return moduleGroups[group].some((code) => access?.activeCodes.has(code));
}

/**
 * 선생님이 이 기능의 학생 이용을 잠갔는지입니다. 메뉴에는 남기고 회색으로 보여 주며,
 * 그룹 안의 모듈이 모두 잠겼을 때만 잠금으로 봅니다(예: 게임형·평가형 퀴즈 중 하나만 열려 있으면 사용 가능).
 */
export function isModuleSuspended(access: ClassroomAccess | undefined, group: ModuleGroup) {
  if (!hasModule(access, group)) return false;
  const openCodes = moduleGroups[group].filter((code) => access?.activeCodes.has(code));
  return openCodes.every((code) => access?.suspendedCodes.has(code));
}

// 선생님 수업은 모듈 카탈로그 항목이 아니라 교사가 시작한 지도 활동입니다.
export function hasGuidedClass(access: ClassroomAccess | undefined) {
  return Boolean(access?.resources.guidedActivityId);
}
