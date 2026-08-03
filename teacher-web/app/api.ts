const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

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

  const send = () => fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: makeHeaders(init, idempotencyKey)
  });

  let response = await send();
  if (response.status === 401 && allowRefresh && path !== "/auth/token-refreshes") {
    await refreshTeacherAccessToken();
    response = await send();
  }
  return parseResponse<T>(response);
}

export function setAccessToken(token: string) {
  accessToken = token;
}

export function clearAccessToken() {
  accessToken = null;
}

export async function createTeacherSession(idToken: string) {
  const session = await apiRequest<TeacherSession>("/auth/teacher-sessions", {
    method: "POST",
    body: JSON.stringify({ id_token: idToken })
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
    return await apiRequest<TeacherBootstrap>("/auth/bootstrap", {}, allowRefresh);
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 404) throw error;
    const current = await apiRequest<{
      user_id: string;
      role: string;
      classrooms: Array<{ classroom_id: string; classroom_code: string; role: string }>;
    }>("/auth/me", {}, allowRefresh);
    return {
      user_id: current.user_id,
      roles: [current.role, ...new Set(current.classrooms.map((classroom) => classroom.role))],
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
