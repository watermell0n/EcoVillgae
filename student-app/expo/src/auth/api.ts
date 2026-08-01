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

const useMockLogin = process.env.EXPO_PUBLIC_USE_MOCK_STUDENT_LOGIN === "true";

export async function loginStudent(input: StudentLoginInput): Promise<StudentSession> {
  if (useMockLogin) {
    return {
      accessToken: "mock-access-token",
      refreshToken: "mock-refresh-token",
      expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
      classroomId: "00000000-0000-4000-8000-000000000001",
      studentId: `mock-student-${input.attendanceNumber}`,
      nickname: input.nickname,
      role: "STU"
    };
  }

  const response = await apiRequest<StudentSessionResponse>("/auth/student-sessions", {
    method: "POST",
    auth: false,
    retryOnUnauthorized: false,
    body: JSON.stringify({
      classroom_code: input.classroomCode,
      attendance_number: input.attendanceNumber,
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

export async function restoreStudentIdentity(): Promise<StudentIdentity | null> {
  const [identity, refreshToken] = await Promise.all([
    getStoredIdentity(),
    getRefreshToken()
  ]);
  if (!identity || !refreshToken) return null;

  if (refreshToken.startsWith("mock-")) {
    setAccessToken("mock-access-token");
    return identity;
  }

  await refreshStudentTokens();
  return identity;
}
