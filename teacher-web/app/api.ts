export type ApiMeta = {
  request_id: string;
  correlation_id: string;
  timestamp: string;
};

type ApiEnvelope<T> = {
  data: T;
  meta: ApiMeta;
};

type ApiErrorEnvelope = {
  error?: {
    code?: string;
    message?: string;
    retryable?: boolean;
    details?: Array<{ field?: string; reason?: string }>;
  };
};

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly retryable: boolean;

  constructor(message: string, code: string, status: number, retryable = false) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.retryable = retryable;
  }
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const TEACHER_SESSION_KEY = "ecovillage.teacher.session";

function joinUrl(path: string) {
  return `${API_BASE_URL.replace(/\/$/, "")}/${path.replace(/^\//, "")}`;
}

function savedAccessToken() {
  if (typeof window === "undefined") return null;
  const saved = window.sessionStorage.getItem(TEACHER_SESSION_KEY);
  if (!saved) return null;
  try {
    const session = JSON.parse(saved) as { accessToken?: string };
    return session.accessToken ?? null;
  } catch {
    window.sessionStorage.removeItem(TEACHER_SESSION_KEY);
    return null;
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!headers.has("Idempotency-Key") && init.method && init.method !== "GET") {
    headers.set("Idempotency-Key", crypto.randomUUID());
  }
  const token = savedAccessToken();
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(joinUrl(path), {
    ...init,
    credentials: "include",
    headers
  });
  const text = await response.text();
  let parsed: ApiEnvelope<T> | ApiErrorEnvelope | null = null;
  if (text) {
    try {
      parsed = JSON.parse(text) as ApiEnvelope<T> | ApiErrorEnvelope;
    } catch {
      throw new ApiError("서버 응답 형식을 확인할 수 없습니다.", "INVALID_RESPONSE", response.status);
    }
  }

  if (!response.ok) {
    const body = parsed as ApiErrorEnvelope | null;
    throw new ApiError(
      body?.error?.message ?? "요청을 처리하지 못했습니다.",
      body?.error?.code ?? "HTTP_ERROR",
      response.status,
      body?.error?.retryable ?? false
    );
  }

  return (parsed as ApiEnvelope<T>).data;
}

export type CourseSize = "ONE_SESSION" | "ONE_WEEK" | "ONE_MONTH" | "SEMESTER";

export type CreateClassroomResponse = {
  classroom_id: string;
  classroom_code: string;
  currency_name: string;
  status: string;
  state_version: number;
};

export type SetupCandidate = {
  action_id: string;
  target_type: string;
  label: string;
  proposed_value: number;
  recommended_min_value: number;
  recommended_max_value: number;
  allowed_min_value: number;
  allowed_max_value: number;
  unit: "AMOUNT" | "COUNT" | "RATE_BPS";
  evidence_reason_codes: string[];
  rationale: string;
  cautions: string[];
};

export type SetupCheck = {
  check_id: string;
  severity: "PASS" | "WARN" | "BLOCK";
  reason_code: string;
  subject_action_ids: string[];
  explanation: string;
  correction_guidance: string | null;
};

export type SetupPrecheckResponse = {
  operation_id: string;
  ai_run_id: string;
  operation_status: string;
  run_status: string;
  fallback_used: boolean;
  fallback_code: string | null;
  overall_severity: "PASS" | "WARN" | "BLOCK";
  proposal_id: string | null;
  summary: string;
  candidates: SetupCandidate[];
  checks: SetupCheck[];
};

export type ModuleActivationResponse = {
  module_code: string;
  status: "ACTIVE";
  state_version: number;
  policy_area: string | null;
  policy_version: number | null;
  policy_version_id: string | null;
  rule_version: string;
  replayed: boolean;
};

export type AiProposalCommandResponse = {
  proposal_id: string;
  ai_run_id: string;
  decision_status: string;
  policy_version: number | null;
  policy_version_id: string | null;
  applied_values: Array<{
    action_id: string;
    proposed_value: number;
    unit: SetupCandidate["unit"];
  }>;
  state_version: number;
};

export const MODULE_DEFINITIONS = [
  { code: "ROLE_SALARY", name: "역할·급여", description: "직업 역할과 역할별 급여를 설정합니다." },
  { code: "INCOME_TAX", name: "소득세", description: "급여에서 공동기금으로 회수할 소득세율을 설정합니다." },
  { code: "STORE", name: "상점", description: "상품 판매와 학생 소비 활동을 운영합니다." },
  { code: "CHARACTER", name: "캐릭터", description: "학생 캐릭터와 꾸미기 아이템을 사용합니다." },
  { code: "TRADE", name: "개인 거래", description: "학생 간 제안·동의·교사 승인 거래를 사용합니다." },
  { code: "DEPOSIT", name: "예금·적금", description: "예금·적금 상품과 이자율을 설정합니다." },
  { code: "LOAN", name: "대출", description: "교육용 대출 금리와 상환을 설정합니다." },
  { code: "INVESTMENT", name: "투자", description: "가상 투자 상품과 손익 활동을 운영합니다." },
  { code: "PUBLIC_FUND", name: "공공 예산/투표", description: "공동기금 사용처와 익명 투표를 운영합니다." },
  { code: "QUIZ_GAME", name: "퀴즈", description: "게임형 경제 퀴즈를 운영합니다." }
] as const;

export type ModuleCode = typeof MODULE_DEFINITIONS[number]["code"];

export const MODULE_PREREQUISITES: Partial<Record<ModuleCode, ModuleCode[]>> = {
  INCOME_TAX: ["ROLE_SALARY"],
  LOAN: ["DEPOSIT"]
};

export function periodToCourseSize(period: string): CourseSize {
  if (period === "1회 체험") return "ONE_SESSION";
  if (period === "1달 운영") return "ONE_MONTH";
  if (period === "1학기 운영") return "SEMESTER";
  return "ONE_WEEK";
}

export function valueForDisplay(value: number, unit: SetupCandidate["unit"]) {
  return unit === "RATE_BPS" ? `${(value / 100).toFixed(2)}%` : value.toLocaleString("ko-KR");
}
