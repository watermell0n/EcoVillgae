export type ApiMeta = {
  requestId: string;
  correlationId: string;
  timestamp: string;
};

export type ApiResponse<T> = {
  data: T;
  meta: ApiMeta;
};

export type ApiErrorBody = {
  error: {
    code: string;
    message: string;
    retryable: boolean;
    details: Array<{ field: string; reason: string }>;
  };
  meta?: ApiMeta;
};

export class ApiError extends Error {
  code: string;
  status: number;

  constructor(message: string, code: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";
const SESSION_KEY = "ecovillage.student.session";

function joinUrl(path: string) {
  return `${API_BASE_URL.replace(/\/$/, "")}/${path.replace(/^\//, "")}`;
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

  const savedSession = localStorage.getItem(SESSION_KEY);
  if (savedSession && !headers.has("Authorization")) {
    try {
      const session = JSON.parse(savedSession) as { accessToken?: string };
      if (session.accessToken && !session.accessToken.startsWith("mock-")) {
        headers.set("Authorization", `Bearer ${session.accessToken}`);
      }
    } catch {
      localStorage.removeItem(SESSION_KEY);
    }
  }

  const response = await fetch(joinUrl(path), {
    ...init,
    headers
  });

  const text = await response.text();
  const parsed = text ? JSON.parse(text) as ApiResponse<T> | ApiErrorBody : null;

  if (!response.ok) {
    const errorBody = parsed as ApiErrorBody | null;
    throw new ApiError(
      errorBody?.error.message ?? "요청을 처리하지 못했어요.",
      errorBody?.error.code ?? "HTTP_ERROR",
      response.status
    );
  }

  return (parsed as ApiResponse<T>).data;
}
