import { randomUUID } from "expo-crypto";

import {
  getAccessToken,
  getRefreshToken,
  saveRefreshToken,
  setAccessToken
} from "@/auth/token-vault";

type ApiMeta = {
  requestId: string;
  correlationId: string;
  timestamp: string;
};

type ApiEnvelope<T> = { data: T; meta: ApiMeta };
type ApiErrorEnvelope = {
  error?: { code?: string; message?: string; retryable?: boolean };
};

type RequestOptions = RequestInit & {
  auth?: boolean;
  retryOnUnauthorized?: boolean;
};

type StudentRefreshResponse = {
  access_token: string;
  refresh_token: string;
  expires_at: string;
  session_id: string;
  role: "STU";
};

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
let refreshInFlight: Promise<StudentRefreshResponse> | null = null;

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status: number
  ) {
    super(message);
    this.name = "ApiError";
  }
}
function joinUrl(path: string) {
  return `${API_BASE_URL.replace(/\/$/, "")}/${path.replace(/^\//, "")}`;
}

async function parseResponse<T>(response: Response): Promise<T> {
  const body = await response.text();
  const parsed = body ? JSON.parse(body) as ApiEnvelope<T> | ApiErrorEnvelope : null;

  if (!response.ok) {
    const error = parsed as ApiErrorEnvelope | null;
    throw new ApiError(
      error?.error?.message ?? "요청을 처리하지 못했어요.",
      error?.error?.code ?? "HTTP_ERROR",
      response.status
    );
  }

  return (parsed as ApiEnvelope<T>).data;
}

export async function refreshStudentTokens() {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = await getRefreshToken();
      if (!refreshToken) {
        throw new ApiError("다시 로그인해 주세요.", "REFRESH_TOKEN_MISSING", 401);
      }

      const response = await fetch(joinUrl("/auth/token-refreshes"), {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Idempotency-Key": randomUUID(),
          "X-Refresh-Token": refreshToken
        }
      });
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
    ...init
  } = options;
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (init.method && init.method !== "GET" && !headers.has("Idempotency-Key")) {
    headers.set("Idempotency-Key", randomUUID());
  }

  const token = getAccessToken();
  if (auth && token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(joinUrl(path), { ...init, headers });
  if (response.status === 401 && auth && retryOnUnauthorized) {
    await refreshStudentTokens();
    return apiRequest<T>(path, { ...options, retryOnUnauthorized: false });
  }

  return parseResponse<T>(response);
}
