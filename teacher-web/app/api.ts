"use client";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const REQUEST_TIMEOUT_MS = Number(process.env.NEXT_PUBLIC_API_TIMEOUT_MS ?? 8_000);

let accessToken: string | null = null;
let refreshPromise: Promise<void> | null = null;

type ApiEnvelope<T> = {
  data: T;
  meta?: {
    trace_id?: string;
  };
};

type ApiErrorPayload = {
  code?: string;
  message?: string;
  trace_id?: string;
  field_errors?: Record<string, string[]>;
};

export class ApiError extends Error {
  status: number;
  code: string;
  traceId?: string;
  fieldErrors?: Record<string, string[]>;

  constructor(status: number, payload: ApiErrorPayload = {}) {
    super(payload.message ?? `요청을 처리하지 못했어요. (${status})`);
    this.name = "ApiError";
    this.status = status;
    this.code = payload.code ?? "UNKNOWN_ERROR";
    this.traceId = payload.trace_id;
    this.fieldErrors = payload.field_errors;
  }
}

export type TeacherSession = {
  access_token: string;
  expires_at: string;
  session_id: string;
  teacher_id: string;
  display_name: string;
  role: "TCH";
};

export type BootstrapClassroom = {
  resource_id: string;
  label: string;
  status: string;
  version?: number;
  state_version?: number;
  classroom_code?: string;
  currency_name?: string;
};

export type TeacherBootstrap = {
  user_id: string;
  roles: string[];
  schools: Array<{
    school_id: string;
    role: string;
  }>;
  classrooms: BootstrapClassroom[];
  start_screen: string;
};

export type ClassroomServerState = {
  configuration?: Record<string, unknown>;
  modulesAndPolicies?: Record<string, unknown>;
  rosterAndSalaries?: Record<string, unknown>;
  failures: string[];
};

function makeHeaders(init: RequestInit, idempotencyKey?: string) {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  if (idempotencyKey) headers.set("Idempotency-Key", idempotencyKey);
  return headers;
}

async function parseResponse<T>(response: Response): Promise<T> {
  const text = await response.text();
  let body: Record<string, any> = {};
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = { error: { message: text } };
    }
  }
  if (!response.ok) {
    const error = body?.error ?? body;
    throw new ApiError(response.status, {
      code: error?.code,
      message: error?.message,
      trace_id: error?.trace_id ?? body?.meta?.trace_id,
      field_errors: error?.field_errors
    });
  }
  return ((body as ApiEnvelope<T>).data ?? body) as T;
}

async function refreshTeacherAccessToken() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await fetch(`${API_BASE_URL}/auth/token-refreshes`, {
        method: "POST",
        credentials: "include",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "Idempotency-Key": crypto.randomUUID()
        },
        body: JSON.stringify({})
      });
      const refreshed = await parseResponse<{ access_token: string }>(response);
      accessToken = refreshed.access_token;
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const idempotencyKey = method === "GET" || method === "HEAD"
    ? undefined
    : new Headers(init.headers).get("Idempotency-Key") ?? crypto.randomUUID();

  const sendOnce = async () => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      return await fetch(`${API_BASE_URL}${path}`, {
        ...init,
        credentials: "include",
        headers: makeHeaders(init, idempotencyKey),
        signal: controller.signal
      });
    } finally {
      clearTimeout(timeout);
    }
  };
  const send = async () => {
    try {
      return await sendOnce();
    } catch (error) {
      if (error instanceof Error && ["AbortError", "TypeError"].includes(error.name)) return sendOnce();
      throw error;
    }
  };

  let response = await send();
  if (response.status === 401 && allowRefresh && path !== "/auth/token-refreshes") {
    await refreshTeacherAccessToken();
    response = await send();
  }
  return parseResponse<T>(response);
}

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function clearAccessToken() {
  accessToken = null;
}

export function hasTeacherAccessToken() {
  return Boolean(accessToken);
}

export function getRealtimeWebSocketUrl() {
  const configured = process.env.NEXT_PUBLIC_WEBSOCKET_URL;
  const url = new URL(configured ?? API_BASE_URL, window.location.href);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  if (!configured || url.pathname === "/" || url.pathname === "") {
    url.pathname = "/ws/v1";
  }
  return url.toString();
}

export type RealtimeTicketResponse = {
  ticket: string;
  expires_at: string;
};

export function createRealtimeTicket(channelIds: string[], resourceIds: string[]) {
  return apiRequest<RealtimeTicketResponse>("/auth/realtime-tickets", {
    method: "POST",
    body: JSON.stringify({
      expected_state_version: 0,
      screen_id: "SCR-TCH-001",
      ui_action_id: "UIA-TCH-001",
      client_occurred_at: new Date().toISOString(),
      channel_ids: channelIds,
      resource_ids: resourceIds
    })
  });
}

export async function createTeacherSession(idToken: string) {
  const session = await apiRequest<TeacherSession>("/auth/teacher-sessions", {
    method: "POST",
    body: JSON.stringify({ id_token: idToken })
  }, false);
  setAccessToken(session.access_token);
  return session;
}

export async function createTeacherPasswordSession(email: string, password: string) {
  const session = await apiRequest<TeacherSession>("/auth/teacher-password-sessions", {
    method: "POST",
    body: JSON.stringify({ email, password })
  }, false);
  setAccessToken(session.access_token);
  return session;
}

export async function registerTeacher(
  schoolCode: string,
  displayName: string,
  email: string,
  password: string
) {
  const session = await apiRequest<TeacherSession>("/auth/teacher-registrations", {
    method: "POST",
    body: JSON.stringify({
      school_code: schoolCode,
      display_name: displayName,
      email,
      password
    })
  }, false);
  setAccessToken(session.access_token);
  return session;
}

export async function restoreTeacherSession() {
  await refreshTeacherAccessToken();
  return getTeacherBootstrap(false);
}

export async function logoutTeacher() {
  try {
    await apiRequest("/auth/sessions/current", { method: "DELETE" }, false);
  } finally {
    clearAccessToken();
  }
}

export async function getTeacherBootstrap(allowRefresh = true): Promise<TeacherBootstrap> {
  try {
    const [bootstrap, current] = await Promise.all([
      apiRequest<TeacherBootstrap>("/auth/bootstrap", {}, allowRefresh),
      getTeacherCurrent(allowRefresh)
    ]);
    return {
      ...bootstrap,
      schools: current.schools
    };
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 404) throw error;
    const current = await getTeacherCurrent(allowRefresh);
    return {
      user_id: current.user_id,
      roles: [current.role, ...new Set(current.classrooms.map((classroom) => classroom.role))],
      schools: current.schools,
      classrooms: current.classrooms.map((classroom) => ({
        resource_id: classroom.classroom_id,
        label: classroom.classroom_code,
        status: "ACTIVE",
        classroom_code: classroom.classroom_code
      })),
      start_screen: "classrooms"
    };
  }
}

type TeacherCurrent = {
  user_id: string;
  role: string;
  schools: TeacherBootstrap["schools"];
  classrooms: Array<{ classroom_id: string; classroom_code: string; role: string }>;
};

function getTeacherCurrent(allowRefresh: boolean) {
  return apiRequest<TeacherCurrent>("/auth/me", {}, allowRefresh);
}

export async function getClassroomServerState(classroomId: string): Promise<ClassroomServerState> {
  const results = await Promise.allSettled([
    apiRequest<Record<string, unknown>>(`/classrooms/${classroomId}/configuration`),
    apiRequest<Record<string, unknown>>(`/classrooms/${classroomId}/modules-and-policies`),
    apiRequest<Record<string, unknown>>(`/classrooms/${classroomId}/roster-and-salaries`)
  ]);
  const labels = ["학급 설정", "모듈·정책", "학생·급여"];
  const failures = results.flatMap((result, index) => result.status === "rejected" ? [labels[index]] : []);
  if (failures.length === results.length) {
    const rejected = results[0] as PromiseRejectedResult;
    throw rejected.reason;
  }
  return {
    configuration: results[0].status === "fulfilled" ? results[0].value : undefined,
    modulesAndPolicies: results[1].status === "fulfilled" ? results[1].value : undefined,
    rosterAndSalaries: results[2].status === "fulfilled" ? results[2].value : undefined,
    failures
  };
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
  { code: "STORE", name: "상점·소비", description: "상품 판매와 학생 소비 활동을 운영합니다." },
  { code: "BUDGET", name: "예산 관리", description: "수입과 지출 예산을 계획하고 비교합니다." },
  { code: "PAYSLIP", name: "급여명세서", description: "급여와 공제 내역을 명세서로 제공합니다." },
  { code: "PUBLIC_FUND", name: "공동기금", description: "학급 공동기금의 조성과 사용을 운영합니다." },
  { code: "PUBLIC_BUDGET", name: "공공예산·투표", description: "공공예산 항목과 익명 투표를 운영합니다." },
  { code: "DEPOSIT", name: "정기예금", description: "정기예금 상품과 이자율을 설정합니다." },
  { code: "SAVINGS", name: "정기적금", description: "정기적금 상품과 납입 일정을 운영합니다." },
  { code: "CREDIT", name: "신용 기초", description: "신용과 성실도 개념을 학습합니다." },
  { code: "LOAN", name: "대출·상환", description: "교육용 대출 금리와 상환을 설정합니다." },
  { code: "INVESTMENT", name: "가상 투자", description: "가상 투자 상품과 손익 활동을 운영합니다." },
  { code: "RISK_MANAGEMENT", name: "투자 위험관리", description: "투자의 위험과 분산 원칙을 학습합니다." },
  { code: "MARKET_PRICE", name: "가격 변화", description: "시장 가격 변화를 경제 활동에 반영합니다." },
  { code: "TRADE", name: "개인거래", description: "학생 간 제안·동의·교사 승인 거래를 사용합니다." },
  { code: "PHYSICAL_STORE", name: "실제 상품", description: "교실의 실제 상품 판매와 재고를 운영합니다." },
  { code: "CHARACTER", name: "캐릭터", description: "학생 캐릭터와 꾸미기 아이템을 사용합니다." },
  { code: "QUIZ_GAME", name: "게임형 퀴즈", description: "점수와 선택적 순위가 있는 퀴즈를 운영합니다." },
  { code: "QUIZ_TEST", name: "평가형 퀴즈", description: "비공개 결과를 사용하는 평가형 퀴즈를 운영합니다." },
  { code: "AI_EVENT", name: "AI 경제 이벤트", description: "검토 가능한 AI 경제 이벤트를 운영합니다." },
  { code: "CUMULATIVE_REPORT", name: "누적 리포트", description: "장기 활동의 누적 결과를 제공합니다." }
] as const;

export type ModuleCode = typeof MODULE_DEFINITIONS[number]["code"];

export const STUDENT_ACCESS_MODULE_NAMES: string[] = [
  "상점·소비",
  "캐릭터",
  "개인거래",
  "정기예금",
  "정기적금",
  "대출·상환",
  "가상 투자",
  "공동기금",
  "공공예산·투표",
  "실제 상품",
  "게임형 퀴즈",
  "평가형 퀴즈"
];

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
