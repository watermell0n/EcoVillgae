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
  }>;
};

export type ClassroomAccess = {
  modules: ClassroomModule[];
  activeCodes: Set<string>;
  resources: {
    quizSessionId?: string;
    voteId?: string;
    guidedActivityId?: string;
  };
};

const useMocks = process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES === "true";

const mockModules: ClassroomModule[] = [
  { resourceId: "mock-wallet", label: "지갑", status: "ACTIVE", version: 1, stateVersion: 1, code: "WALLET", config: {} },
  { resourceId: "mock-store", label: "상점", status: "ACTIVE", version: 1, stateVersion: 1, code: "STORE", config: {} },
  { resourceId: "mock-character", label: "캐릭터", status: "ACTIVE", version: 1, stateVersion: 1, code: "CHARACTER", config: {} },
  { resourceId: "mock-deposit", label: "예금", status: "ACTIVE", version: 1, stateVersion: 1, code: "DEPOSIT", config: {} },
  { resourceId: "mock-investment", label: "투자", status: "ACTIVE", version: 1, stateVersion: 1, code: "INVESTMENT", config: {} },
  { resourceId: "mock-trade", label: "개인 거래", status: "ACTIVE", version: 1, stateVersion: 1, code: "TRADE", config: {} },
  { resourceId: "mock-quiz", label: "퀴즈", status: "ACTIVE", version: 1, stateVersion: 1, code: "QUIZ_GAME", config: { active_quiz_session_id: "mock-quiz-session" } },
  { resourceId: "mock-vote", label: "공공 예산", status: "ACTIVE", version: 1, stateVersion: 1, code: "BUDGET", config: { active_vote_id: "mock-vote" } },
  { resourceId: "mock-paystub", label: "월급명세서", status: "ACTIVE", version: 1, stateVersion: 1, code: "PAYSLIP", config: {} }
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

function createAccess(modules: ClassroomModule[]): ClassroomAccess {
  const activeModules = modules.filter((module) => module.status === "ACTIVE");
  return {
    modules: activeModules,
    activeCodes: new Set(activeModules.map((module) => module.code)),
    resources: {
      quizSessionId: firstConfiguredId(activeModules, ["active_quiz_session_id", "quiz_session_id"]),
      voteId: firstConfiguredId(activeModules, ["active_vote_id", "vote_id"]),
      guidedActivityId: firstConfiguredId(activeModules, ["active_guided_activity_id", "guided_activity_id", "activity_id"])
    }
  };
}

export async function fetchClassroomAccess(classroomId: string): Promise<ClassroomAccess> {
  if (useMocks) return createAccess(mockModules);

  const response = await apiRequest<ModulesAndPoliciesResponse>(`/classrooms/${classroomId}/modules-and-policies`);
  return createAccess(response.modules.map((module) => ({
    resourceId: module.resource_id ?? module.module_code ?? "unknown-module",
    label: module.label ?? module.module_code ?? "모듈",
    status: module.status ?? "DISABLED",
    version: module.version ?? 0,
    stateVersion: module.state_version ?? 0,
    code: module.module_code ?? "UNKNOWN",
    config: module.config ?? {}
  })));
}

export const moduleGroups = {
  wallet: ["WALLET"],
  shop: ["STORE", "PHYSICAL_STORE"],
  character: ["CHARACTER"],
  bank: ["DEPOSIT", "SAVINGS"],
  investment: ["INVESTMENT"],
  trade: ["TRADE"],
  quiz: ["QUIZ_GAME", "QUIZ_TEST"],
  meeting: ["BUDGET", "VOTE"],
  paystub: ["PAYSLIP", "INCOME"]
} as const;

export type ModuleGroup = keyof typeof moduleGroups;

export function hasModule(access: ClassroomAccess | undefined, group: ModuleGroup) {
  return moduleGroups[group].some((code) => access?.activeCodes.has(code));
}
