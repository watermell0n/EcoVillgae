import { apiRequest, refreshStudentTokens } from "@/api/client";
import {
  getRefreshToken,
  getStoredIdentity,
  setAccessToken
} from "./token-vault";
import type { StudentIdentity, StudentLoginInput, StudentSession } from "./types";

type StudentSessionResponse = {
  access_token: string;
  refresh_token: string;
  expires_at: string;
  classroom_id: string;
  student_id: string;
  role: "STU";
};

type BootstrapResponse = {
  user_id: string;
  roles: string[];
  classrooms: Array<{
    resource_id?: string;
    label?: string;
    status?: string;
    classroom_code?: string;
    currency_name?: string;
  }>;
  start_screen: string;
};

type MeResponse = {
  user_id: string;
  display_name: string;
  role: "STU" | "TCH";
  classrooms: Array<{ classroom_id?: string; resource_id?: string }>;
};

const useMockLogin = process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_LOGIN === "true";

export async function loginStudent(input: StudentLoginInput): Promise<StudentSession> {
  if (useMockLogin) {
    return {
      accessToken: "mock-access-token",
      refreshToken: "mock-refresh-token",
      expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
      classroomId: `mock-classroom-${input.classroomCode}`,
      studentId: `mock-student-${input.nickname}`,
      nickname: input.nickname,
      role: "STU"
    };
  }

  const response = await apiRequest<StudentSessionResponse>("/auth/student-sessions", {
    method: "POST",
    auth: false,
    retryOnUnauthorized: false,
    retryOnNetworkFailure: true,
    body: JSON.stringify({
      classroom_code: input.classroomCode,
      nickname: input.nickname,
      password: input.password,
      device_id: input.deviceId
    })
  });

  return {
    accessToken: response.access_token,
    refreshToken: response.refresh_token,
    expiresAt: response.expires_at,
    classroomId: response.classroom_id,
    studentId: response.student_id,
    nickname: input.nickname,
    role: response.role
  };
}

export async function fetchStudentBootstrap() {
  return apiRequest<BootstrapResponse>("/auth/bootstrap");
}

async function fetchCurrentStudent() {
  return apiRequest<MeResponse>("/auth/me");
}

export async function restoreStudentIdentity(): Promise<StudentIdentity | null> {
  const [storedIdentity, refreshToken] = await Promise.all([
    getStoredIdentity(),
    getRefreshToken()
  ]);
  if (!storedIdentity || !refreshToken) return null;

  if (refreshToken.startsWith("mock-")) {
    setAccessToken("mock-access-token");
    return storedIdentity;
  }

  await refreshStudentTokens();
  const [bootstrap, me] = await Promise.all([fetchStudentBootstrap(), fetchCurrentStudent()]);
  if (!bootstrap.roles.includes("STU") || me.role !== "STU") return null;

  const selected = bootstrap.classrooms.find((classroom) => classroom.resource_id === storedIdentity.classroomId)
    ?? bootstrap.classrooms[0];
  if (!selected?.resource_id) return null;

  return {
    ...storedIdentity,
    classroomId: selected.resource_id,
    classroomLabel: selected.label,
    currencyName: selected.currency_name,
    startScreen: bootstrap.start_screen,
    nickname: me.display_name || storedIdentity.nickname,
    studentId: me.user_id || storedIdentity.studentId
  };
}

export async function logoutStudent() {
  if (getAccessTokenIsMock()) return;
  await apiRequest<void>("/auth/sessions/current", {
    method: "DELETE",
    retryOnNetworkFailure: true
  });
}

function getAccessTokenIsMock() {
  return useMockLogin;
}
