import { randomUUID } from "expo-crypto";

import {
  getAccessToken,
  getRefreshToken,
  saveRefreshToken,
  setAccessToken
} from "@/auth/token-vault";

export type ApiMeta = {
  requestId?: string;
  correlationId?: string;
  traceId?: string;
  request_id?: string;
  correlation_id?: string;
  trace_id?: string;
  timestamp?: string;
};

type ApiEnvelope<T> = { data: T; meta?: ApiMeta };
type ApiErrorEnvelope = {
  error?: {
    code?: string;
    message?: string;
    retryable?: boolean;
    trace_id?: string;
    field_errors?: Record<string, string[]> | Array<{ field: string; message: string }>;
  };
  meta?: ApiMeta;
};

export type ApiErrorKind = "offline" | "timeout" | "unauthorized" | "forbidden" | "conflict" | "validation" | "server" | "unknown";

type RequestOptions = RequestInit & {
  auth?: boolean;
  retryOnUnauthorized?: boolean;
  retryOnNetworkFailure?: boolean;
  timeoutMs?: number;
};

type StudentRefreshResponse = {
  access_token: string;
  refresh_token: string;
  expires_at: string;
  session_id: string;
  role: "STU";
};

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const DEFAULT_TIMEOUT_MS = 12_000;
let refreshInFlight: Promise<StudentRefreshResponse> | null = null;

export function getRealtimeWebSocketUrl() {
  const configured = process.env.EXPO_PUBLIC_WEBSOCKET_URL;
  const url = new URL(configured ?? API_BASE_URL);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  if (!configured || url.pathname === "/" || url.pathname === "") {
    url.pathname = "/ws/v1";
  }
  return url.toString();
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status: number,
    readonly kind: ApiErrorKind,
    readonly traceId?: string,
    readonly fieldErrors?: ApiErrorEnvelope["error"] extends infer T ? T extends { field_errors?: infer F } ? F : never : never,
    readonly retryable = false
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function joinUrl(path: string) {
  return `${API_BASE_URL.replace(/\/$/, "")}/${path.replace(/^\//, "")}`;
}

function errorKind(status: number): ApiErrorKind {
  if (status === 401) return "unauthorized";
  if (status === 403) return "forbidden";
  if (status === 409) return "conflict";
  if (status === 400 || status === 422) return "validation";
  if (status >= 500) return "server";
  return "unknown";
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T;

  const body = await response.text();
  let parsed: ApiEnvelope<T> | ApiErrorEnvelope | null = null;
  try {
    parsed = body ? JSON.parse(body) as ApiEnvelope<T> | ApiErrorEnvelope : null;
  } catch {
    throw new ApiError("서버 응답을 읽지 못했어요.", "INVALID_RESPONSE", response.status, "server");
  }

  if (!response.ok) {
    const envelope = parsed as ApiErrorEnvelope | null;
    const error = envelope?.error;
    throw new ApiError(
      error?.message ?? "요청을 처리하지 못했어요.",
      error?.code ?? "HTTP_ERROR",
      response.status,
      errorKind(response.status),
      error?.trace_id ?? envelope?.meta?.trace_id ?? envelope?.meta?.traceId ?? envelope?.meta?.correlation_id ?? envelope?.meta?.correlationId,
      error?.field_errors,
      error?.retryable ?? response.status >= 500
    );
  }

  return (parsed as ApiEnvelope<T>).data;
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") {
      throw new ApiError("응답이 늦어지고 있어요. 다시 시도해 주세요.", "REQUEST_TIMEOUT", 0, "timeout", undefined, undefined, true);
    }
    throw new ApiError("인터넷 연결을 확인해 주세요.", "NETWORK_UNAVAILABLE", 0, "offline", undefined, undefined, true);
  } finally {
    clearTimeout(timeout);
  }
}

export async function refreshStudentTokens() {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = await getRefreshToken();
      if (!refreshToken) {
        throw new ApiError("다시 로그인해 주세요.", "REFRESH_TOKEN_MISSING", 401, "unauthorized");
      }

      const response = await fetchWithTimeout(joinUrl("/auth/token-refreshes"), {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Idempotency-Key": randomUUID(),
          "X-Correlation-ID": randomUUID(),
          "X-Refresh-Token": refreshToken
        }
      }, DEFAULT_TIMEOUT_MS);
      const session = await parseResponse<StudentRefreshResponse>(response);
      setAccessToken(session.access_token);
      await saveRefreshToken(session.refresh_token);
      return session;
    })().finally(() => {
      refreshInFlight = null;
    });
  }

  return refreshInFlight;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const {
    auth = true,
    retryOnUnauthorized = true,
    retryOnNetworkFailure = false,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    ...init
  } = options;
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  headers.set("X-Correlation-ID", headers.get("X-Correlation-ID") ?? randomUUID());

  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const isCommand = Boolean(init.method && !["GET", "HEAD", "OPTIONS"].includes(init.method.toUpperCase()));
  if (isCommand && !headers.has("Idempotency-Key")) headers.set("Idempotency-Key", randomUUID());

  const execute = async (allowRefresh: boolean, allowNetworkRetry: boolean): Promise<T> => {
    const token = getAccessToken();
    if (auth && token) headers.set("Authorization", `Bearer ${token}`);

    try {
      const response = await fetchWithTimeout(joinUrl(path), { ...init, headers }, timeoutMs);
      if (response.status === 401 && auth && allowRefresh) {
        await refreshStudentTokens();
        return execute(false, allowNetworkRetry);
      }
      return parseResponse<T>(response);
    } catch (error) {
      if (allowNetworkRetry && error instanceof ApiError && error.retryable) {
        return execute(allowRefresh, false);
      }
      throw error;
    }
  };

  return execute(retryOnUnauthorized, retryOnNetworkFailure);
}
